import { assert, assertEquals } from "@std/assert";
import { hex, sha256 } from "../_shared/encoding.ts";
import { createHandler } from "../_shared/handler.ts";
import { signInstallToken } from "../_shared/install_token.ts";
import {
  BillingUnavailable,
  GooglePlayBilling,
  type Subscription,
  subscriptionFrom,
} from "../_shared/play_billing.ts";
import { purchaseHash } from "../_shared/request_hash.ts";
import {
  cloudRequest,
  INSTALL,
  NOW,
  RTDN_SECRET,
  SECRET,
  testDeps,
} from "./helpers.ts";

const TOKEN = "purchase-token-from-play-0123456789";
const ACTIVE: Subscription = {
  productId: "precut_pro",
  basePlanId: "yearly",
  state: "active",
  expiresAt: "2027-10-22T10:00:00Z",
  autoRenewing: true,
  trial: true,
  acknowledged: false,
};

async function setup() {
  const deps = testDeps();
  await deps.quota.register(INSTALL);
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  return { deps, handler: createHandler(deps), token };
}

function buy(installToken: string, purchaseToken: string = TOKEN) {
  return cloudRequest("POST", "/purchase", {
    headers: {
      "x-install-token": installToken,
      "x-integrity-token": "play-token",
      "content-type": "application/json",
    },
    body: JSON.stringify({ purchaseToken }),
  });
}

function notification(data: unknown, secret = RTDN_SECRET) {
  return cloudRequest("POST", `/rtdn?secret=${secret}`, {
    api: null,
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      message: { data: btoa(JSON.stringify(data)), messageId: "1" },
      subscription: "projects/p/subscriptions/rtdn",
    }),
  });
}

Deno.test("purchase: Google's record makes the install Pro, acknowledged, the verdict bound to the token", async () => {
  const { deps, handler, token } = await setup();
  deps.billing.subscriptions.set(TOKEN, ACTIVE);
  const response = await handler(buy(token));
  assertEquals(response.status, 200);
  const { status } = await response.json();
  assertEquals(status.pro.pro, true);
  assertEquals(deps.billing.acknowledged, [TOKEN]);
  assertEquals(
    deps.integrity.asked[0].expectedHash,
    await purchaseHash(INSTALL, TOKEN),
  );
  const saved = deps.quota.purchases.get(hex(await sha256(TOKEN)));
  assert(saved?.installs.has(INSTALL));
  // The token itself is never logged.
  assert(!JSON.stringify(deps.logs).includes(TOKEN));
});

Deno.test("purchase: a token Google doesn't know, or another product, is refused", async () => {
  const { deps, handler, token } = await setup();
  assertEquals(
    (await (await handler(buy(token))).json()).error.code,
    "bad_purchase",
  );
  deps.billing.subscriptions.set(TOKEN, { ...ACTIVE, productId: "other" });
  assertEquals((await handler(buy(token))).status, 400);
  assertEquals(deps.quota.purchases.size, 0);
});

Deno.test("purchase: Google unreachable is retryable; an expired purchase isn't Pro and isn't acknowledged", async () => {
  const { deps, handler, token } = await setup();
  deps.billing.error = new BillingUnavailable("503");
  assertEquals((await handler(buy(token))).status, 503);
  deps.billing.error = null;
  deps.billing.subscriptions.set(TOKEN, {
    ...ACTIVE,
    state: "expired",
    expiresAt: "2026-10-01T00:00:00Z",
  });
  const { status } = await (await handler(buy(token))).json();
  assertEquals(status.pro, {
    pro: false,
    state: "expired",
    expiresAt: "2026-10-01T00:00:00Z",
  });
  assertEquals(deps.billing.acknowledged, []);
});

Deno.test("rtdn: a notification looks the purchase up again; unknown purchases and bad secrets change nothing", async () => {
  const { deps, handler, token } = await setup();
  deps.billing.subscriptions.set(TOKEN, ACTIVE);
  await handler(buy(token));
  deps.billing.subscriptions.set(TOKEN, { ...ACTIVE, state: "on_hold" });
  const note = {
    version: "1.0",
    packageName: "com.savie.precut",
    subscriptionNotification: { notificationType: 5, purchaseToken: TOKEN },
  };
  assertEquals((await handler(notification(note, "wrong-secret"))).status, 403);
  assertEquals((await handler(notification(note))).status, 200);
  const saved = deps.quota.purchases.get(hex(await sha256(TOKEN)));
  assertEquals(saved?.subscription.state, "on_hold");
  // Never sent by an app: ignored (and acknowledged to Pub/Sub so it isn't retried).
  const stranger = {
    ...note,
    subscriptionNotification: {
      notificationType: 4,
      purchaseToken: "someone-elses-token-123",
    },
  };
  assertEquals((await handler(notification(stranger))).status, 200);
  assertEquals(deps.quota.purchases.size, 1);
  assertEquals(
    (await handler(notification({ testNotification: {} }))).status,
    200,
  );
  deps.billing.error = new BillingUnavailable("down");
  assertEquals((await handler(notification(note))).status, 503);
});

Deno.test("rtdn: off without a secret", async () => {
  const deps = { ...testDeps(), rtdnSecret: null };
  const response = await createHandler(deps)(notification({}, ""));
  assertEquals(response.status, 403);
});

Deno.test("Google's SubscriptionPurchaseV2 becomes PreCut's record", () => {
  const sub = subscriptionFrom({
    subscriptionState: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [
      {
        productId: "precut_pro",
        expiryTime: "2026-11-01T00:00:00Z",
        offerDetails: { basePlanId: "monthly" },
      },
      {
        productId: "precut_pro",
        expiryTime: "2026-12-01T00:00:00Z",
        autoRenewingPlan: { autoRenewEnabled: true },
        offerDetails: { basePlanId: "yearly", offerId: "free-trial" },
        offerPhase: { freeTrial: {} },
      },
    ],
  });
  assertEquals(sub, {
    productId: "precut_pro",
    basePlanId: "yearly",
    state: "grace",
    expiresAt: "2026-12-01T00:00:00Z",
    autoRenewing: true,
    trial: true,
    acknowledged: true,
  });
  assertEquals(subscriptionFrom({ lineItems: [] }), null);
  assertEquals(
    subscriptionFrom({
      subscriptionState: "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED",
      lineItems: [{ productId: "x" }],
    })?.state,
    "expired",
  );
});

Deno.test("the Play Developer API: unknown tokens are null, outages throw, acknowledging posts", async () => {
  const { privateKey } = await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  );
  const pkcs8 = new Uint8Array(
    await crypto.subtle.exportKey("pkcs8", privateKey),
  );
  const pem = `-----BEGIN PRIVATE KEY-----\n${
    btoa(String.fromCharCode(...pkcs8))
  }\n-----END PRIVATE KEY-----`;
  const calls: string[] = [];
  let status = 404;
  const fetchFn = ((input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    calls.push(`${init?.method ?? "GET"} ${url}`);
    if (url.startsWith("https://oauth2.googleapis.com/token")) {
      return Promise.resolve(
        Response.json({ access_token: "at", expires_in: 3600 }),
      );
    }
    return Promise.resolve(
      new Response(status === 200 ? "{}" : "", { status }),
    );
  }) as typeof fetch;
  const billing = new GooglePlayBilling(
    { clientEmail: "sa@example.com", privateKeyPem: pem },
    "com.savie.precut",
    fetchFn,
  );
  assertEquals(await billing.subscription("tok"), null);
  status = 500;
  let threw = false;
  await billing.subscription("tok").catch((e) =>
    threw = e instanceof BillingUnavailable
  );
  assert(threw);
  status = 200;
  await billing.acknowledge("precut_pro", "tok");
  assert(
    calls.some((c) =>
      c.startsWith("POST ") &&
      c.endsWith("/purchases/subscriptions/precut_pro/tokens/tok:acknowledge")
    ),
  );
  assertEquals(calls.filter((c) => c.includes("oauth2")).length, 1);
});
