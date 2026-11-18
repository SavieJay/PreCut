import { assert, assertEquals } from "@std/assert";
import { createHandler } from "../_shared/handler.ts";
import { signInstallToken } from "../_shared/install_token.ts";
import {
  ANA,
  ANA_TOKEN,
  cloudRequest,
  INSTALL,
  NOW,
  PLAN_MONTHLY,
  PLAN_YEARLY,
  SECRET,
  testDeps,
} from "./helpers.ts";

/** Phase 31: Paystack checkout, confirmation, cancellation and refreshes (no webhook). */

function signedIn(method: string, path: string, body?: unknown) {
  return cloudRequest(method, path, {
    headers: {
      authorization: `Bearer ${ANA_TOKEN}`,
      "content-type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function checkout(
  handler: (r: Request) => Promise<Response>,
  plan = "monthly",
) {
  return await (await handler(signedIn("POST", "/paystack/checkout", { plan })))
    .json();
}

Deno.test("checkout: a payment page for the plan, for the account's email; the reference is kept", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const page = await checkout(handler, "yearly");
  assert(page.url.startsWith("https://checkout.paystack.com/"));
  assert(/^precut_[0-9a-f]{32}$/.test(page.reference));
  assertEquals(deps.paystack.api.initialized[0], {
    email: "ana@example.com",
    planCode: PLAN_YEARLY,
    reference: page.reference,
    metadata: { precut_user: ANA, precut_plan: "yearly" },
  });
  assertEquals(
    deps.accounts.paystackRows.get(ANA)?.pending_reference,
    page.reference,
  );
  assertEquals(
    (await handler(signedIn("POST", "/paystack/checkout", { plan: "weekly" })))
      .status,
    400,
  );
  // Without Paystack keys the routes say so.
  const off = await createHandler({ ...deps, paystack: null })(
    signedIn("POST", "/paystack/checkout", { plan: "monthly" }),
  );
  assertEquals((await off.json()).error.code, "not_configured");
});

Deno.test("confirm: only a successful payment for PreCut's plan, by this account, makes it Pro", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const { reference } = await checkout(handler);
  const paystack = deps.paystack.api;
  const transaction = {
    status: "success",
    customerId: "77",
    planCode: PLAN_MONTHLY,
    userId: ANA,
    paidAt: "2026-11-18T10:00:00.000Z",
  };
  const confirm = async () =>
    await (await handler(signedIn("POST", "/paystack/confirm", { reference })))
      .json();

  paystack.transactions.set(reference, { ...transaction, status: "abandoned" });
  assertEquals((await confirm()).paid, false);
  paystack.transactions.set(reference, {
    ...transaction,
    userId: "someone-else",
  });
  assertEquals((await confirm()).paid, false);
  paystack.transactions.set(reference, {
    ...transaction,
    planCode: "PLN_newstheme",
  });
  assertEquals((await confirm()).paid, false);
  assertEquals(deps.accounts.paystackRows.get(ANA)?.status, null);

  // Paid, before Paystack has made the subscription: one period from the payment.
  paystack.transactions.set(reference, transaction);
  assertEquals((await confirm()).paid, true);
  const row = deps.accounts.paystackRows.get(ANA);
  assertEquals([
    row?.status,
    row?.plan,
    row?.customer_code,
    row?.paid_until,
    row?.pending_reference,
  ], [
    "active",
    "monthly",
    "77",
    "2026-12-18T10:00:00.000Z",
    null,
  ]);
  assertEquals(
    (await handler(
      signedIn("POST", "/paystack/confirm", { reference: "nope" }),
    )).status,
    400,
  );
});

Deno.test("confirm: the subscription Paystack made is saved, with what cancelling needs", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const { reference } = await checkout(handler);
  deps.paystack.api.transactions.set(reference, {
    status: "success",
    customerId: "77",
    planCode: PLAN_MONTHLY,
    userId: ANA,
    paidAt: null,
  });
  deps.paystack.api.subscriptions.set("SUB_other", {
    customerId: "77",
    code: "SUB_other",
    emailToken: "x",
    status: "active",
    nextPaymentDate: "2027-01-01T00:00:00.000Z",
    planCode: "PLN_newstheme",
  });
  deps.paystack.api.subscriptions.set("SUB_1", {
    customerId: "77",
    code: "SUB_1",
    emailToken: "tok",
    status: "active",
    nextPaymentDate: "2026-12-18T10:00:00.000Z",
    planCode: PLAN_MONTHLY,
  });
  await handler(signedIn("POST", "/paystack/confirm", { reference }));
  const row = deps.accounts.paystackRows.get(ANA);
  assertEquals([row?.subscription_code, row?.email_token, row?.paid_until], [
    "SUB_1",
    "tok",
    "2026-12-18T10:00:00.000Z",
  ]);

  // Cancelling stops renewing (Pro lasts the period) and reads Paystack again.
  const cancelled = await handler(signedIn("POST", "/paystack/cancel"));
  assertEquals(cancelled.status, 200);
  assertEquals(deps.paystack.api.disabled, ["SUB_1"]);
  assertEquals(deps.accounts.paystackRows.get(ANA)?.status, "non-renewing");
  // Nothing left to cancel.
  deps.accounts.paystackRows.delete(ANA);
  assertEquals(
    (await handler(signedIn("POST", "/paystack/cancel"))).status,
    404,
  );
});

Deno.test("refresh: a renewal is read on /status and /account once the period passes; Paystack down keeps the answer", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  await deps.quota.register(INSTALL);
  deps.accounts.installUsers.set(INSTALL, ANA);
  deps.paystack.api.subscriptions.set("SUB_1", {
    customerId: "77",
    code: "SUB_1",
    emailToken: "tok",
    status: "active",
    nextPaymentDate: "2027-01-18T10:00:00.000Z",
    planCode: PLAN_MONTHLY,
  });
  await deps.accounts.savePaystack(ANA, {
    mode: "test",
    plan: "monthly",
    customer_code: "77",
    subscription_code: "SUB_1",
    email_token: "tok",
    status: "active",
    paid_until: "2026-12-18T10:00:00.000Z",
    pending_reference: null,
  });
  // Fresh and within its period: not asked again.
  deps.now.value = new Date("2026-12-01T00:00:00Z");
  deps.accounts.now = () => deps.now.value;
  await deps.accounts.savePaystack(ANA, {
    ...deps.accounts.paystackRows.get(ANA)!,
    pending_reference: null,
  });
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  const status = () =>
    handler(
      cloudRequest("GET", "/status", { headers: { "x-install-token": token } }),
    );
  await status();
  assertEquals(
    deps.accounts.paystackRows.get(ANA)?.paid_until,
    "2026-12-18T10:00:00.000Z",
  );
  // Past the period: Paystack's renewal comes in.
  deps.now.value = new Date("2026-12-19T00:00:00Z");
  await status();
  assertEquals(
    deps.accounts.paystackRows.get(ANA)?.paid_until,
    "2027-01-18T10:00:00.000Z",
  );
  // Paystack down: the saved answer stays, the app still gets its status.
  deps.paystack.api.down = true;
  deps.now.value = new Date("2027-01-20T00:00:00Z");
  assertEquals((await status()).status, 200);
  assertEquals(
    deps.accounts.paystackRows.get(ANA)?.paid_until,
    "2027-01-18T10:00:00.000Z",
  );
  // /account carries the plan.
  deps.paystack.api.down = false;
  deps.accounts.planReply = { pro: true, state: "active" };
  assertEquals(
    (await (await handler(signedIn("GET", "/account"))).json()).plan,
    { pro: true, state: "active" },
  );
});

Deno.test("refresh: a test-mode row is never read with live keys", async () => {
  const deps = testDeps();
  const handler = createHandler({
    ...deps,
    paystack: { ...deps.paystack, mode: "live" },
  });
  await deps.accounts.savePaystack(ANA, {
    mode: "test",
    plan: "monthly",
    customer_code: "77",
    subscription_code: "SUB_1",
    email_token: "tok",
    status: "active",
    paid_until: "2020-01-01T00:00:00.000Z",
    pending_reference: null,
  });
  await handler(signedIn("GET", "/account"));
  assertEquals(
    deps.accounts.paystackRows.get(ANA)?.paid_until,
    "2020-01-01T00:00:00.000Z",
  );
  assertEquals(deps.accounts.calls.at(-2), {
    name: "plan",
    args: [ANA, "live"],
  });
});
