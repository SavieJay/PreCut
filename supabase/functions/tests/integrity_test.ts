import { assertEquals, assertRejects } from "@std/assert";
import * as jose from "jose";
import { base64Url } from "../_shared/encoding.ts";
import {
  checkVerdict,
  IntegrityUnavailable,
  normaliseDigest,
  parseServiceAccount,
  PlayIntegrityVerifier,
  type VerdictRules,
} from "../_shared/integrity.ts";

const NOW = Date.parse("2026-10-22T10:00:00Z");
// apksigner/keytool print a certificate's SHA-256 in hex; verdicts carry it as URL-safe base64.
const CERT_HEX =
  "6A:6A:14:74:B5:CB:BB:2B:1A:A5:7E:0B:C3:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22";
const CERT = normaliseDigest(CERT_HEX);
const RULES: VerdictRules = {
  packageName: "com.savie.precut",
  certificateDigests: new Set([CERT]),
  maxAgeMs: 300_000,
};

function verdict(
  changes: { request?: object; app?: object; device?: object } = {},
) {
  return {
    requestDetails: {
      requestPackageName: "com.savie.precut",
      requestHash: "HASH",
      timestampMillis: String(NOW - 2_000),
      ...changes.request,
    },
    appIntegrity: {
      appRecognitionVerdict: "PLAY_RECOGNIZED",
      packageName: "com.savie.precut",
      certificateSha256Digest: [CERT],
      versionCode: "60",
      ...changes.app,
    },
    deviceIntegrity: {
      deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"],
      ...changes.device,
    },
    accountDetails: { appLicensingVerdict: "LICENSED" },
  };
}

function reason(payload: unknown, hash = "HASH") {
  const result = checkVerdict(payload, hash, RULES, NOW);
  return result.ok ? "ok" : `${result.code}: ${result.reason}`;
}

Deno.test("a fresh verdict from the app signed with our key on a certified phone passes", () => {
  assertEquals(reason(verdict()), "ok");
  // Sideloaded (debug build, or a release not installed from Play): still ours by its certificate.
  assertEquals(
    reason(verdict({ app: { appRecognitionVerdict: "UNRECOGNIZED_VERSION" } })),
    "ok",
  );
  // Stronger device labels alongside the default one are fine.
  assertEquals(
    reason(
      verdict({
        device: {
          deviceRecognitionVerdict: [
            "MEETS_BASIC_INTEGRITY",
            "MEETS_DEVICE_INTEGRITY",
          ],
        },
      }),
    ),
    "ok",
  );
});

Deno.test("the request must be this one: package, hash and age", () => {
  assertEquals(
    reason(verdict({ request: { requestPackageName: "com.other" } })),
    "app_not_verified: request package",
  );
  assertEquals(reason(verdict(), "OTHER"), "app_not_verified: request hash");
  assertEquals(
    reason(verdict({ request: { timestampMillis: String(NOW - 301_000) } })),
    "app_not_verified: verdict age",
  );
  assertEquals(
    reason(verdict({ request: { timestampMillis: String(NOW + 120_000) } })),
    "app_not_verified: verdict age",
  );
  assertEquals(
    reason(verdict({ request: { timestampMillis: "soon" } })),
    "app_not_verified: verdict age",
  );
});

Deno.test("the app must be ours: evaluated, our package, signed with one of our certificates", () => {
  assertEquals(
    reason(verdict({ app: { appRecognitionVerdict: "UNEVALUATED" } })),
    "app_not_verified: app verdict",
  );
  assertEquals(
    reason(verdict({ app: { packageName: "com.copy" } })),
    "app_not_verified: app package",
  );
  assertEquals(
    reason(verdict({ app: { certificateSha256Digest: ["c29tZW9uZS1lbHNl"] } })),
    "app_not_verified: certificate",
  );
  assertEquals(
    reason(verdict({ app: { certificateSha256Digest: undefined } })),
    "app_not_verified: certificate",
  );
});

Deno.test("the device must meet device integrity (emulators get an empty verdict)", () => {
  assertEquals(
    reason(verdict({ device: { deviceRecognitionVerdict: [] } })),
    "device_not_verified: device verdict",
  );
  assertEquals(
    reason(
      verdict({
        device: { deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY"] },
      }),
    ),
    "device_not_verified: device verdict",
  );
  assertEquals(reason(null), "app_not_verified: request package");
});

Deno.test("certificate digests compare the same in hex, colons, base64 or URL-safe base64", () => {
  const bytes = Uint8Array.from(CERT_HEX.split(":"), (b) => parseInt(b, 16));
  const standard = btoa(String.fromCharCode(...bytes));
  assertEquals(normaliseDigest(CERT_HEX.toLowerCase().replace(/:/g, "")), CERT);
  assertEquals(normaliseDigest(standard), CERT);
  assertEquals(normaliseDigest(base64Url(bytes)), CERT);
});

async function serviceAccount() {
  const { publicKey, privateKey } = await jose.generateKeyPair("RS256", {
    extractable: true,
  });
  const json = JSON.stringify({
    type: "service_account",
    client_email: "precut-integrity@precut.iam.gserviceaccount.com",
    private_key: await jose.exportPKCS8(privateKey),
    private_key_id: "key-1",
  });
  return { account: parseServiceAccount(json), publicKey };
}

Deno.test("Google: signs in with the service account once, then decodes and checks each token", async () => {
  const { account, publicKey } = await serviceAccount();
  const calls: string[] = [];
  const fetchFn =
    (async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      calls.push(url);
      if (url === "https://oauth2.googleapis.com/token") {
        const form = new URLSearchParams(String(init?.body));
        assertEquals(
          form.get("grant_type"),
          "urn:ietf:params:oauth:grant-type:jwt-bearer",
        );
        const { payload, protectedHeader } = await jose.jwtVerify(
          form.get("assertion") ?? "",
          publicKey,
          {
            issuer: "precut-integrity@precut.iam.gserviceaccount.com",
            audience: "https://oauth2.googleapis.com/token",
            currentDate: new Date(NOW),
          },
        );
        assertEquals(
          payload.scope,
          "https://www.googleapis.com/auth/playintegrity",
        );
        assertEquals(protectedHeader.kid, "key-1");
        return Response.json({
          access_token: "google-access",
          expires_in: 3599,
        });
      }
      assertEquals(
        url,
        "https://playintegrity.googleapis.com/v1/com.savie.precut:decodeIntegrityToken",
      );
      assertEquals(
        new Headers(init?.headers).get("authorization"),
        "Bearer google-access",
      );
      assertEquals(JSON.parse(String(init?.body)), {
        integrity_token: "play-token",
      });
      return Response.json({ tokenPayloadExternal: verdict() });
    }) as typeof fetch;
  const verifier = new PlayIntegrityVerifier(
    account,
    RULES,
    fetchFn,
    () => NOW,
  );
  assertEquals(await verifier.verify("play-token", "HASH"), { ok: true });
  assertEquals((await verifier.verify("play-token", "OTHER")).ok, false);
  assertEquals(calls.filter((c) => c.includes("oauth2")).length, 1);
});

Deno.test("Google: a refused token is a verdict, an outage is retryable", async () => {
  const { account } = await serviceAccount();
  let decodeStatus = 400;
  const fetchFn = ((input: string | URL | Request) => {
    if (String(input).includes("oauth2")) {
      return Promise.resolve(
        Response.json({ access_token: "a", expires_in: 3599 }),
      );
    }
    return Promise.resolve(new Response("{}", { status: decodeStatus }));
  }) as typeof fetch;
  const verifier = new PlayIntegrityVerifier(
    account,
    RULES,
    fetchFn,
    () => NOW,
  );
  assertEquals(await verifier.verify("forged", "HASH"), {
    ok: false,
    code: "app_not_verified",
    reason: "token refused by Google",
  });
  decodeStatus = 503;
  await assertRejects(() => verifier.verify("t", "HASH"), IntegrityUnavailable);
  const offline = new PlayIntegrityVerifier(
    account,
    RULES,
    (() => Promise.reject(new TypeError("offline"))) as typeof fetch,
    () => NOW,
  );
  await assertRejects(() => offline.verify("t", "HASH"), IntegrityUnavailable);
});

Deno.test("a key file without an email or private key is refused", () => {
  try {
    parseServiceAccount('{"type":"service_account"}');
    throw new Error("accepted");
  } catch (e) {
    assertEquals(
      (e as Error).message,
      "GOOGLE_SERVICE_ACCOUNT_JSON is not a service-account key",
    );
  }
});
