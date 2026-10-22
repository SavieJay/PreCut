import { assertEquals, assertInstanceOf, assertThrows } from "@std/assert";
import * as jose from "jose";
import { ConfigError, depsFromEnv, isLocal } from "../_shared/config.ts";
import { ElevenLabsTranscriber } from "../_shared/elevenlabs.ts";
import {
  NoIntegrityCheck,
  PlayIntegrityVerifier,
} from "../_shared/integrity.ts";
import { FakeTranscriber } from "../_shared/transcriber.ts";

const { privateKey } = await jose.generateKeyPair("RS256", {
  extractable: true,
});
const DEPLOYED = {
  SUPABASE_URL: "https://abcdefghijklmnop.supabase.co",
  SUPABASE_SECRET_KEYS: JSON.stringify({ default: "sb_secret_test" }),
  INSTALL_TOKEN_SECRET: "x".repeat(48),
  ELEVENLABS_API_KEY: "el-key",
  GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify({
    client_email: "a@b.iam.gserviceaccount.com",
    private_key: await jose.exportPKCS8(privateKey),
  }),
  PLAY_CERT_SHA256:
    "6A:6A:14:74:B5:CB:BB:2B:1A:A5:7E:0B:C3:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22, c29tZQ",
};

function message(fn: () => unknown): string {
  return assertThrows(fn, ConfigError).message;
}

Deno.test("a deployed function checks Play Integrity and uses ElevenLabs", () => {
  const deps = depsFromEnv(DEPLOYED);
  assertInstanceOf(deps.integrity, PlayIntegrityVerifier);
  assertInstanceOf(deps.transcriber, ElevenLabsTranscriber);
  assertEquals(deps.maxUploadBytes, 10 * 1024 * 1024);
});

Deno.test("the legacy service_role key is used when Supabase gives no new secret key", () => {
  const { SUPABASE_SECRET_KEYS: _, ...legacy } = DEPLOYED;
  depsFromEnv({ ...legacy, SUPABASE_SERVICE_ROLE_KEY: "legacy" });
  assertEquals(
    message(() => depsFromEnv(legacy)),
    "SUPABASE_SERVICE_ROLE_KEY is not set",
  );
});

Deno.test("Play Integrity off and the fake transcriber are refused on a deployed project", () => {
  assertEquals(
    message(() => depsFromEnv({ ...DEPLOYED, REQUIRE_INTEGRITY: "false" })),
    "REQUIRE_INTEGRITY=false is only allowed on a local stack",
  );
  assertEquals(
    message(() => depsFromEnv({ ...DEPLOYED, TRANSCRIBER: "fake" })),
    "TRANSCRIBER=fake is only allowed on a local stack",
  );
});

Deno.test("a local stack may run without Play Integrity or a provider key", () => {
  const deps = depsFromEnv({
    SUPABASE_URL: "http://kong:8000",
    SUPABASE_SERVICE_ROLE_KEY: "local",
    INSTALL_TOKEN_SECRET: "y".repeat(40),
    REQUIRE_INTEGRITY: "false",
    TRANSCRIBER: "fake",
  });
  assertInstanceOf(deps.integrity, NoIntegrityCheck);
  assertInstanceOf(deps.transcriber, FakeTranscriber);
});

Deno.test("missing or weak secrets stop the function from starting", () => {
  assertEquals(
    message(() => depsFromEnv({ ...DEPLOYED, INSTALL_TOKEN_SECRET: "short" })),
    "INSTALL_TOKEN_SECRET must be at least 32 characters",
  );
  assertEquals(
    message(() => depsFromEnv({ ...DEPLOYED, PLAY_CERT_SHA256: " , " })),
    "PLAY_CERT_SHA256 lists no signing certificates",
  );
  assertEquals(
    message(() => depsFromEnv({ ...DEPLOYED, ELEVENLABS_API_KEY: "" })),
    "ELEVENLABS_API_KEY is not set",
  );
  assertEquals(
    message(() =>
      depsFromEnv({ ...DEPLOYED, GOOGLE_SERVICE_ACCOUNT_JSON: undefined })
    ),
    "GOOGLE_SERVICE_ACCOUNT_JSON is not set",
  );
});

Deno.test("only this machine's stack counts as local", () => {
  assertEquals(
    [
      "http://127.0.0.1:54321",
      "http://localhost:54321",
      "http://kong:8000",
      "https://abc.supabase.co",
      "https://kong.example.com",
    ].map(isLocal),
    [true, true, true, false, false],
  );
});
