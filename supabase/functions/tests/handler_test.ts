import { assert, assertEquals, assertFalse } from "@std/assert";
import { createHandler } from "../_shared/handler.ts";
import {
  signInstallToken,
  verifyInstallToken,
} from "../_shared/install_token.ts";
import { IntegrityUnavailable } from "../_shared/integrity.ts";
import { registerHash, transcribeHash } from "../_shared/request_hash.ts";
import { TranscribeError } from "../_shared/transcriber.ts";
import {
  cloudRequest,
  INSTALL,
  NOW,
  SECRET,
  testDeps,
  TRANSCRIPT,
} from "./helpers.ts";

const AUDIO = new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8]);

async function registered() {
  const deps = testDeps();
  await deps.quota.register(INSTALL);
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  return { deps, handler: createHandler(deps), token };
}

function upload(
  token: string,
  init: { headers?: Record<string, string>; body?: BodyInit; query?: string } =
    {},
) {
  return cloudRequest("POST", `/transcribe${init.query ?? ""}`, {
    headers: {
      "x-install-token": token,
      "x-integrity-token": "play-token",
      "x-audio-duration-ms": "2000",
      "content-type": "audio/mp4",
      ...init.headers,
    },
    body: init.body ?? AUDIO,
  });
}

async function errorCode(response: Response): Promise<string> {
  return (await response.json()).error.code;
}

Deno.test("every request must name an API version it supports", async () => {
  const deps = testDeps();
  const response = await createHandler(deps)(
    cloudRequest("GET", "/status", { api: null }),
  );
  assertEquals(response.status, 426);
  assertEquals(await errorCode(response), "upgrade_required");
});

Deno.test("unknown routes are not found", async () => {
  const response = await createHandler(testDeps())(
    cloudRequest("GET", "/nothing"),
  );
  assertEquals(response.status, 404);
});

Deno.test("register: a verified install gets a token for its own ID and its status", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const response = await handler(cloudRequest("POST", "/register", {
    headers: {
      "x-integrity-token": "play-token",
      "content-type": "application/json",
    },
    body: JSON.stringify({ installId: INSTALL }),
  }));
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(
    (await verifyInstallToken(body.installToken, SECRET, NOW))?.installId,
    INSTALL,
  );
  assertEquals(body.status.secondsLeft, 1800);
  // The verdict had to be for registering exactly this install.
  assertEquals(deps.integrity.asked, [{
    token: "play-token",
    expectedHash: await registerHash(INSTALL),
  }]);
});

Deno.test("register: refused without a good verdict or a proper install ID", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const body = JSON.stringify({ installId: INSTALL });
  assertEquals(
    await errorCode(await handler(cloudRequest("POST", "/register", { body }))),
    "app_not_verified",
  );
  deps.integrity.result = {
    ok: false,
    code: "device_not_verified",
    reason: "test",
  };
  const device = await handler(
    cloudRequest("POST", "/register", {
      headers: { "x-integrity-token": "t" },
      body,
    }),
  );
  assertEquals(device.status, 403);
  assertEquals(await errorCode(device), "device_not_verified");
  const notUuid = await handler(
    cloudRequest("POST", "/register", {
      headers: { "x-integrity-token": "t" },
      body: '{"installId":"x"}',
    }),
  );
  assertEquals(await errorCode(notUuid), "bad_request");
  assertEquals(deps.quota.installs.size, 0);
});

Deno.test("register: Google being unreachable is retryable, not a verdict", async () => {
  const deps = testDeps();
  deps.integrity.error = new IntegrityUnavailable("down");
  const response = await createHandler(deps)(cloudRequest("POST", "/register", {
    headers: { "x-integrity-token": "t" },
    body: JSON.stringify({ installId: INSTALL }),
  }));
  assertEquals(response.status, 503);
  assertEquals(await errorCode(response), "integrity_unavailable");
});

Deno.test("status: needs a valid install token, and no Play Integrity check", async () => {
  const { deps, handler, token } = await registered();
  assertEquals(
    await errorCode(await handler(cloudRequest("GET", "/status"))),
    "not_registered",
  );
  const forged = await signInstallToken(
    INSTALL,
    new TextEncoder().encode("another-secret-another-secret-123"),
    NOW,
  );
  assertEquals(
    await errorCode(
      await handler(
        cloudRequest("GET", "/status", {
          headers: { "x-install-token": forged },
        }),
      ),
    ),
    "not_registered",
  );
  const response = await handler(
    cloudRequest("GET", "/status", { headers: { "x-install-token": token } }),
  );
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(body.status.secondsLeft, 1800);
  // A fresh token isn't replaced.
  assertEquals(body.installToken, undefined);
  assertEquals(deps.integrity.asked.length, 0);
});

Deno.test("status: a token close to expiring comes back renewed", async () => {
  const { deps, handler, token } = await registered();
  deps.now.value = new Date(NOW.getTime() + 160 * 24 * 3600 * 1000);
  const body = await (await handler(
    cloudRequest("GET", "/status", { headers: { "x-install-token": token } }),
  )).json();
  const renewed = await verifyInstallToken(
    body.installToken,
    SECRET,
    deps.now.value,
  );
  assertEquals(renewed, { installId: INSTALL, renew: false });
});

Deno.test("status: an expired token or a forgotten install is not registered", async () => {
  const { deps, handler, token } = await registered();
  await deps.quota.forget(INSTALL);
  assertEquals(
    await errorCode(
      await handler(
        cloudRequest("GET", "/status", {
          headers: { "x-install-token": token },
        }),
      ),
    ),
    "not_registered",
  );
  deps.now.value = new Date(NOW.getTime() + 181 * 24 * 3600 * 1000);
  await deps.quota.register(INSTALL);
  assertEquals(
    await errorCode(
      await handler(
        cloudRequest("GET", "/status", {
          headers: { "x-install-token": token },
        }),
      ),
    ),
    "not_registered",
  );
});

Deno.test("transcribe: words in our format, the verdict bound to these bytes, the longer length charged", async () => {
  const { deps, handler, token } = await registered();
  const response = await handler(upload(token));
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(body.transcript, TRANSCRIPT);
  // Stated 2 s, transcribed 2.4 s: 3 s charged.
  assertEquals(body.secondsLeft, 1797);
  assertEquals(deps.quota.installs.get(INSTALL), { used: 3, reserved: 0 });
  assertEquals(deps.integrity.asked, [{
    token: "play-token",
    expectedHash: await transcribeHash(INSTALL, AUDIO),
  }]);
  assertEquals(deps.transcriber.calls, [{
    bytes: 8,
    mimeType: "audio/mp4",
    language: null,
  }]);
});

Deno.test("transcribe: a language is passed on; auto lets the provider detect it", async () => {
  const { deps, handler, token } = await registered();
  await handler(upload(token, { query: "?lang=yo" }));
  await handler(upload(token, { query: "?lang=auto" }));
  assertEquals(deps.transcriber.calls.map((c) => c.language), ["yo", null]);
  assertEquals(
    await errorCode(await handler(upload(token, { query: "?lang=yoruba!" }))),
    "bad_request",
  );
});

Deno.test("transcribe: refuses wrong headers and sizes before reading the verdict or the minutes", async () => {
  const { deps, handler, token } = await registered();
  assertEquals(
    await errorCode(
      await handler(upload(token, { headers: { "x-audio-duration-ms": "" } })),
    ),
    "bad_request",
  );
  assertEquals(
    await errorCode(
      await handler(
        upload(token, { headers: { "x-audio-duration-ms": "-5" } }),
      ),
    ),
    "bad_request",
  );
  const unsupported = await handler(
    upload(token, { headers: { "content-type": "text/plain" } }),
  );
  assertEquals(unsupported.status, 415);
  const big = await handler(upload(token, { body: new Uint8Array(2048) }));
  assertEquals(big.status, 413);
  assertEquals((await big.json()).error, {
    code: "too_large",
    maxUploadBytes: 1024,
  });
  assertEquals(
    await errorCode(await handler(upload(token, { body: new Uint8Array(0) }))),
    "bad_audio",
  );
  assertEquals(deps.integrity.asked.length, 0);
  assertEquals(deps.quota.installs.get(INSTALL), { used: 0, reserved: 0 });
});

Deno.test("transcribe: an unverified upload is refused and costs nothing", async () => {
  const { deps, handler, token } = await registered();
  deps.integrity.result = {
    ok: false,
    code: "app_not_verified",
    reason: "certificate",
  };
  const response = await handler(upload(token));
  assertEquals(response.status, 403);
  assertEquals(deps.transcriber.calls.length, 0);
  assertEquals(deps.quota.installs.get(INSTALL), { used: 0, reserved: 0 });
});

Deno.test("transcribe: used-up minutes and over-long audio are refused with the details", async () => {
  const { deps, handler, token } = await registered();
  deps.quota.installs.set(INSTALL, { used: 1799, reserved: 0 });
  const used = await handler(upload(token));
  assertEquals(used.status, 429);
  assertEquals((await used.json()).error, {
    code: "quota_used",
    secondsLeft: 1,
    resetsAt: "2026-11-01T00:00:00+00:00",
  });
  const long = await handler(
    upload(token, { headers: { "x-audio-duration-ms": "601000" } }),
  );
  assertEquals((await long.json()).error, {
    code: "too_long",
    maxUploadSeconds: 600,
  });
  assertEquals(deps.transcriber.calls.length, 0);
});

Deno.test("transcribe: a provider failure releases the minutes", async () => {
  const { deps, handler, token } = await registered();
  deps.transcriber.error = new TranscribeError("provider_failed", "HTTP 500");
  const failed = await handler(upload(token));
  assertEquals(failed.status, 502);
  assertEquals(await errorCode(failed), "provider_failed");
  deps.transcriber.error = new TranscribeError("bad_audio", "HTTP 422");
  assertEquals((await handler(upload(token))).status, 422);
  assertEquals(deps.quota.installs.get(INSTALL), { used: 0, reserved: 0 });
});

Deno.test("me: deletes the install, after which its token is refused", async () => {
  const { deps, handler, token } = await registered();
  const response = await handler(
    cloudRequest("DELETE", "/me", { headers: { "x-install-token": token } }),
  );
  assertEquals(await response.json(), { deleted: true });
  assertFalse(deps.quota.installs.has(INSTALL));
  assertEquals(await errorCode(await handler(upload(token))), "not_registered");
});

Deno.test("a storage failure is a server error, logged without details of the request", async () => {
  const { deps, handler, token } = await registered();
  deps.quota.failWith = new Error("database down");
  const response = await handler(upload(token));
  assertEquals(response.status, 500);
  assert(
    deps.logs.some((entry) =>
      entry.event === "error" && entry.message === "database down"
    ),
  );
});

Deno.test("logs hold no audio, words or tokens", async () => {
  const { deps, handler, token } = await registered();
  await handler(upload(token));
  await handler(
    cloudRequest("GET", "/status", { headers: { "x-install-token": token } }),
  );
  const logged = JSON.stringify(deps.logs);
  for (const secret of [token, "play-token", "Hello", "Lagos", INSTALL]) {
    assertFalse(logged.includes(secret), secret);
  }
  assert(
    deps.logs.some((entry) =>
      entry.event === "transcribe" && entry.outcome === "ok" &&
      entry.charged === 3
    ),
  );
});

Deno.test("the deployed path (without /functions/v1) routes the same", async () => {
  const { handler, token } = await registered();
  const response = await handler(
    new Request("https://abc.supabase.co/cloud/status", {
      headers: { "x-precut-api": "1", "x-install-token": token },
    }),
  );
  assertEquals(response.status, 200);
});

Deno.test("refused uploads are still read to the end before the reply (hosted Supabase waits for them)", async () => {
  const { handler } = await registered();
  for (
    const request of [
      upload("not-a-token"),
      upload("not-a-token", { body: new Uint8Array(2048) }),
    ]
  ) {
    const response = await handler(request);
    assertEquals(response.status, 401);
    assert(request.bodyUsed, "the upload was left unread");
  }
});
