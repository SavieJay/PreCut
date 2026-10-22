import { createClient } from "@supabase/supabase-js";
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
