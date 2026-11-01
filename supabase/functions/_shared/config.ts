import { createClient } from "@supabase/supabase-js";
import {
  AnthropicEditAgent,
  type EditAgent,
  type Effort,
  ScriptedEditAgent,
} from "./edit_agent.ts";
import { ElevenLabsTranscriber } from "./elevenlabs.ts";
import { utf8 } from "./encoding.ts";
import type { Deps } from "./handler.ts";
import {
  type IntegrityVerifier,
  NoIntegrityCheck,
  normaliseDigest,
  parseServiceAccount,
  PlayIntegrityVerifier,
} from "./integrity.ts";
import {
  FakePlayBilling,
  GooglePlayBilling,
  type PlayBilling,
} from "./play_billing.ts";
import { PostgresQuotaStore } from "./quota.ts";
import { FakeTranscriber, type Transcriber } from "./transcriber.ts";

/**
 * The function's dependencies from its environment: Supabase's own variables plus the secrets set
 * from supabase/.env.secrets (docs/CLOUD_API.md lists them). Switching Play Integrity off or using
 * the fake transcriber is refused unless SUPABASE_URL is a local stack, so a deployed function
 * can't be left open by a stray setting.
 */

export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
export const MIN_API_VERSION = 1;
const PACKAGE_NAME = "com.savie.precut";
const MAX_VERDICT_AGE_MS = 5 * 60_000;

export class ConfigError extends Error {}

export function depsFromEnv(
  env: Record<string, string | undefined>,
  fetchFn: typeof fetch = fetch,
): Deps {
  const url = required(env, "SUPABASE_URL");
  const local = isLocal(url);
  const secret = required(env, "INSTALL_TOKEN_SECRET");
  if (secret.length < 32) {
    throw new ConfigError(
      "INSTALL_TOKEN_SECRET must be at least 32 characters",
    );
  }

  return {
    quota: new PostgresQuotaStore(
      createClient(url, secretKey(env), {
        auth: { persistSession: false, autoRefreshToken: false },
      }),
    ),
    integrity: integrityFrom(env, local, fetchFn),
    transcriber: transcriberFrom(env, local, fetchFn),
    editAgent: editAgentFrom(env, local, fetchFn),
    billing: billingFrom(env, local, fetchFn),
    proProducts: (env.PRO_PRODUCT_IDS ?? "precut_pro").split(",").map((id) =>
      id.trim()
    ).filter((id) => id !== ""),
    rtdnSecret: rtdnSecretFrom(env),
    installTokenSecret: utf8(secret),
    maxUploadBytes: MAX_UPLOAD_BYTES,
    minApiVersion: MIN_API_VERSION,
    clock: () => new Date(),
    log: (entry) => console.log(JSON.stringify(entry)),
  };
}

function integrityFrom(
  env: Record<string, string | undefined>,
  local: boolean,
  fetchFn: typeof fetch,
): IntegrityVerifier {
  if (env.REQUIRE_INTEGRITY === "false") {
    if (!local) {
      throw new ConfigError(
        "REQUIRE_INTEGRITY=false is only allowed on a local stack",
      );
    }
    return new NoIntegrityCheck();
  }
  const digests = (env.PLAY_CERT_SHA256 ?? "").split(",").map((d) => d.trim())
    .filter((d) => d !== "").map(normaliseDigest);
  if (digests.length === 0) {
    throw new ConfigError("PLAY_CERT_SHA256 lists no signing certificates");
  }
  return new PlayIntegrityVerifier(
    parseServiceAccount(required(env, "GOOGLE_SERVICE_ACCOUNT_JSON")),
    {
      packageName: PACKAGE_NAME,
      certificateDigests: new Set(digests),
      maxAgeMs: MAX_VERDICT_AGE_MS,
    },
    fetchFn,
  );
}

function transcriberFrom(
  env: Record<string, string | undefined>,
  local: boolean,
  fetchFn: typeof fetch,
): Transcriber {
  if (env.TRANSCRIBER === "fake") {
    if (!local) {
      throw new ConfigError(
        "TRANSCRIBER=fake is only allowed on a local stack",
      );
    }
    return new FakeTranscriber({
      language: "en",
      durationMs: 1_200,
      words: [{ text: "Hello", startMs: 0, endMs: 500 }, {
        text: "PreCut",
        startMs: 600,
        endMs: 1_200,
      }],
    });
  }
  return new ElevenLabsTranscriber(
    required(env, "ELEVENLABS_API_KEY"),
    env.ELEVENLABS_ZERO_RETENTION === "true",
    fetchFn,
  );
}

/**
 * Google Play's subscription records (Phase 25) with the same service account as Play Integrity;
 * null without one (a local stack), so /purchase answers not_configured. BILLING=fake (local stack
 * only) knows no purchases.
 */
function billingFrom(
  env: Record<string, string | undefined>,
  local: boolean,
  fetchFn: typeof fetch,
): PlayBilling | null {
  if (env.BILLING === "fake") {
    if (!local) {
      throw new ConfigError("BILLING=fake is only allowed on a local stack");
    }
    return new FakePlayBilling();
  }
  const account = env.GOOGLE_SERVICE_ACCOUNT_JSON;
  if (!account) return null;
  return new GooglePlayBilling(
    parseServiceAccount(account),
    PACKAGE_NAME,
    fetchFn,
  );
}

/** The push endpoint's secret for Real-time developer notifications; without one, /rtdn refuses all. */
function rtdnSecretFrom(
  env: Record<string, string | undefined>,
): string | null {
  const secret = env.RTDN_SECRET ?? "";
  if (secret === "") return null;
  if (secret.length < 24) {
    throw new ConfigError("RTDN_SECRET must be at least 24 characters");
  }
  return secret;
}

/** Claude's default for AI edits; ANTHROPIC_MODEL picks another (e.g. a cheaper one). */
export const DEFAULT_EDIT_MODEL = "claude-opus-5-5";
const EFFORTS: readonly Effort[] = ["low", "medium", "high", "xhigh", "max"];

/**
 * Claude for AI edits (Phase 22C) when ANTHROPIC_API_KEY is set; null without it, so the other
 * routes keep working and /edit answers not_configured. EDIT_AGENT=fake (local stack only) answers
 * every step with a short reply.
 */
function editAgentFrom(
  env: Record<string, string | undefined>,
  local: boolean,
  fetchFn: typeof fetch,
): EditAgent | null {
  if (env.EDIT_AGENT === "fake") {
    if (!local) {
      throw new ConfigError("EDIT_AGENT=fake is only allowed on a local stack");
    }
    return new ScriptedEditAgent([{
      content: [{
        type: "text",
        text: "This is a test reply: nothing was changed.",
      }],
      stopReason: "end_turn",
      usage: {
        inputTokens: 0,
        outputTokens: 0,
        cacheReadTokens: 0,
        cacheWriteTokens: 0,
        costMicrousd: 0,
      },
      model: "fake",
    }]);
  }
  const key = env.ANTHROPIC_API_KEY?.trim();
  if (!key) return null;
  const effort = (env.ANTHROPIC_EFFORT ?? "medium") as Effort;
  if (!EFFORTS.includes(effort)) {
    throw new ConfigError(
      `ANTHROPIC_EFFORT must be one of ${EFFORTS.join(", ")}`,
    );
  }
  return new AnthropicEditAgent(
    key,
    env.ANTHROPIC_MODEL?.trim() || DEFAULT_EDIT_MODEL,
    effort,
    fetchFn,
  );
}

/** The project's secret key: the new `sb_secret_` key when Supabase provides one, else the legacy service_role key. */
function secretKey(env: Record<string, string | undefined>): string {
  if (env.SUPABASE_SECRET_KEYS) {
    try {
      const keys = JSON.parse(env.SUPABASE_SECRET_KEYS);
      if (typeof keys?.default === "string") return keys.default;
    } catch {
      // Fall back to the legacy key below.
    }
  }
  return required(env, "SUPABASE_SERVICE_ROLE_KEY");
}

/** A stack on this machine: the CLI's (`supabase start`), which reaches itself as "kong" inside Docker. */
export function isLocal(url: string): boolean {
  const host = new URL(url).hostname;
  return host === "localhost" || host === "127.0.0.1" || host === "kong" ||
    host === "host.docker.internal";
}

function required(
  env: Record<string, string | undefined>,
  name: string,
): string {
  const value = env[name];
  if (!value) throw new ConfigError(`${name} is not set`);
  return value;
}
