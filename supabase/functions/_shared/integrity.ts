import * as jose from "jose";
import { base64Url } from "./encoding.ts";

/**
 * Checks a Play Integrity standard token from the app: Google decodes it (only its servers can),
 * then [checkVerdict] applies PreCut's rules. The service account belongs to the Google Cloud
 * project whose number the app passes to Play Integrity.
 */

export type IntegrityFailure = "app_not_verified" | "device_not_verified";

export type IntegrityResult = { ok: true } | {
  ok: false;
  code: IntegrityFailure;
  reason: string;
};

export interface IntegrityVerifier {
  /** Whether [token] is a fresh verdict for [expectedHash] from the genuine app on a genuine phone. */
  verify(token: string, expectedHash: string): Promise<IntegrityResult>;
}

/** Google couldn't be asked (network, outage, our credentials): worth retrying, not the app's fault. */
export class IntegrityUnavailable extends Error {}

export interface VerdictRules {
  packageName: string;
  /** Certificates the app may be signed with (debug, upload, Play app signing), as [normaliseDigest] gives them. */
  certificateDigests: Set<string>;
  /** How old a verdict may be. */
  maxAgeMs: number;
}

const DEVICE_LABEL = "MEETS_DEVICE_INTEGRITY";
const ACCEPTED_APP_VERDICTS = new Set([
  "PLAY_RECOGNIZED",
  "UNRECOGNIZED_VERSION",
]);
/** Allowance for the phone's clock running ahead of ours. */
const FUTURE_SLACK_MS = 60_000;

/**
 * Google's recommended order: the request (package, hash, age), then the app, then the device.
 * The app is recognised by its signing certificate rather than by Play distribution, so debug and
 * sideloaded builds signed with your keys pass on a certified phone; a repackaged app can't carry
 * your certificate.
 */
export function checkVerdict(
  payload: unknown,
  expectedHash: string,
  rules: VerdictRules,
  now: number,
): IntegrityResult {
  const verdict = payload as Verdict | null;
  const request = verdict?.requestDetails;
  if (request?.requestPackageName !== rules.packageName) {
    return fail("app_not_verified", "request package");
  }
  if (request.requestHash !== expectedHash) {
    return fail("app_not_verified", "request hash");
  }
  const at = Number(request.timestampMillis);
  if (
    !Number.isFinite(at) || now - at > rules.maxAgeMs ||
    at - now > FUTURE_SLACK_MS
  ) {
    return fail("app_not_verified", "verdict age");
  }

  const app = verdict?.appIntegrity;
  if (!app || !ACCEPTED_APP_VERDICTS.has(app.appRecognitionVerdict ?? "")) {
    return fail("app_not_verified", "app verdict");
  }
  if (app.packageName !== rules.packageName) {
    return fail("app_not_verified", "app package");
  }
  const signedByUs = (app.certificateSha256Digest ?? []).some((d) =>
    rules.certificateDigests.has(normaliseDigest(d))
  );
  if (!signedByUs) return fail("app_not_verified", "certificate");

  if (
    !(verdict?.deviceIntegrity?.deviceRecognitionVerdict ?? []).includes(
      DEVICE_LABEL,
    )
  ) {
    return fail("device_not_verified", "device verdict");
  }
  return { ok: true };
}

/**
 * A certificate digest as URL-safe base64 without padding (how verdicts carry them), from that
 * form, standard base64 or hex with or without colons (how `apksigner` and keytool print them).
 */
export function normaliseDigest(digest: string): string {
  const compact = digest.trim().replace(/:/g, "");
  if (/^[0-9a-fA-F]{64}$/.test(compact)) {
    return base64Url(
      Uint8Array.from(compact.match(/../g) ?? [], (pair) => parseInt(pair, 16)),
    );
  }
  return compact.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export interface ServiceAccount {
  clientEmail: string;
  privateKeyPem: string;
  privateKeyId?: string;
}

/** The fields we use from a Google service-account key file (JSON). */
export function parseServiceAccount(json: string): ServiceAccount {
  const key = JSON.parse(json);
  if (
    typeof key?.client_email !== "string" ||
    typeof key?.private_key !== "string"
  ) {
    throw new Error("GOOGLE_SERVICE_ACCOUNT_JSON is not a service-account key");
  }
  return {
    clientEmail: key.client_email,
    privateKeyPem: key.private_key,
    privateKeyId: key.private_key_id,
  };
}

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/playintegrity";

/** Decodes tokens with Google's `decodeIntegrityToken`, reusing an access token for its hour. */
export class PlayIntegrityVerifier implements IntegrityVerifier {
  private accessToken: { value: string; expiresAt: number } | null = null;

  constructor(
    private readonly account: ServiceAccount,
    private readonly rules: VerdictRules,
    private readonly fetchFn: typeof fetch = fetch,
    private readonly clock: () => number = Date.now,
  ) {}

  async verify(token: string, expectedHash: string): Promise<IntegrityResult> {
    const url =
      `https://playintegrity.googleapis.com/v1/${this.rules.packageName}:decodeIntegrityToken`;
    const response = await this.post(url, {
      headers: {
        "Authorization": `Bearer ${await this.googleToken()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ integrity_token: token }),
    });
    // Google refuses a malformed, forged or foreign token with a 400.
    if (response.status === 400) {
      return fail("app_not_verified", "token refused by Google");
    }
    if (!response.ok) {
      throw new IntegrityUnavailable(
        `decodeIntegrityToken: HTTP ${response.status}`,
      );
    }
    const body = await response.json();
    return checkVerdict(
      body?.tokenPayloadExternal,
      expectedHash,
      this.rules,
      this.clock(),
    );
  }

  private async googleToken(): Promise<string> {
    const now = this.clock();
    if (this.accessToken && this.accessToken.expiresAt - 60_000 > now) {
      return this.accessToken.value;
    }
    const key = await jose.importPKCS8(this.account.privateKeyPem, "RS256");
    const issuedAt = Math.floor(now / 1000);
    const assertion = await new jose.SignJWT({ scope: SCOPE })
      .setProtectedHeader({
        alg: "RS256",
        typ: "JWT",
        ...(this.account.privateKeyId
          ? { kid: this.account.privateKeyId }
          : {}),
      })
      .setIssuer(this.account.clientEmail)
      .setAudience(TOKEN_URL)
      .setIssuedAt(issuedAt)
      .setExpirationTime(issuedAt + 3600)
      .sign(key);
    const response = await this.post(TOKEN_URL, {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    });
    if (!response.ok) {
      throw new IntegrityUnavailable(`Google token: HTTP ${response.status}`);
    }
    const body = await response.json();
    if (typeof body?.access_token !== "string") {
      throw new IntegrityUnavailable("Google token: no access_token");
    }
    this.accessToken = {
      value: body.access_token,
      expiresAt: now + Number(body.expires_in ?? 3600) * 1000,
    };
    return body.access_token;
  }

  private async post(
    url: string,
    init: { headers: Record<string, string>; body: BodyInit },
  ): Promise<Response> {
    try {
      return await this.fetchFn(url, { method: "POST", ...init });
    } catch (e) {
      throw new IntegrityUnavailable(`${url}: ${e}`);
    }
  }
}

/** Accepts everything: only for a local stack, where there's no Play Integrity (see config.ts). */
export class NoIntegrityCheck implements IntegrityVerifier {
  verify(): Promise<IntegrityResult> {
    return Promise.resolve({ ok: true });
  }
}

interface Verdict {
  requestDetails?: {
    requestPackageName?: string;
    requestHash?: string;
    timestampMillis?: string | number;
  };
  appIntegrity?: {
    appRecognitionVerdict?: string;
    packageName?: string;
    certificateSha256Digest?: string[];
  };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
}

function fail(code: IntegrityFailure, reason: string): IntegrityResult {
  return { ok: false, code, reason };
}
