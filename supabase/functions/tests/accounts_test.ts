import { assert, assertEquals } from "@std/assert";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import { JwksUserVerifier, resumableUploadUrl } from "../_shared/accounts.ts";
import { createHandler } from "../_shared/handler.ts";
import { signInstallToken } from "../_shared/install_token.ts";
import {
  ANA,
  ANA_TOKEN,
  cloudRequest,
  INSTALL,
  NOW,
  RELEASE_SECRET,
  SECRET,
  testDeps,
} from "./helpers.ts";

/** The account routes (Phase 30); their SQL is tested in accounts_sql_test.ts. */

const PROJECT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const VIDEO = "a".repeat(64);

function signedIn(
  method: string,
  path: string,
  body?: unknown,
  token = ANA_TOKEN,
): Request {
  return cloudRequest(method, path, {
    headers: {
      authorization: `Bearer ${token}`,
      "content-type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

const SAVE = {
  baseRevision: null,
  name: "Trip",
  project: { clips: [`cloud:${VIDEO}`] },
  media: [VIDEO, VIDEO],
  coverSha256: null,
  durationMs: 12000,
  editedAt: "2026-11-13T10:00:00Z",
};

Deno.test("accounts: every route needs a signed-in user", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  for (
    const [method, path] of [
      ["GET", "/projects"],
      ["PUT", `/projects/${PROJECT}`],
      ["DELETE", `/projects/${PROJECT}`],
      ["POST", "/media/grant"],
      ["POST", "/media/confirm"],
      ["GET", `/media/${VIDEO}`],
      ["GET", "/account"],
      ["DELETE", "/account"],
      ["POST", "/account/link"],
    ]
  ) {
    const none = await handler(cloudRequest(method, path));
    assertEquals(none.status, 401, `${method} ${path}`);
    assertEquals((await none.json()).error.code, "signed_out");
    assertEquals(
      (await handler(signedIn(method, path, undefined, "someone-else"))).status,
      401,
    );
  }
  assertEquals(deps.accounts.calls, []);
  // Without accounts set up, the routes say so.
  const off = await createHandler({ ...deps, users: null })(
    signedIn("GET", "/account"),
  );
  assertEquals((await off.json()).error.code, "not_configured");
  // Like every app route, they need the API version.
  const old = await handler(
    cloudRequest("GET", "/account", {
      api: null,
      headers: { authorization: `Bearer ${ANA_TOKEN}` },
    }),
  );
  assertEquals((await old.json()).error.code, "upgrade_required");
});

Deno.test("projects: changes page through; a save goes to the user's own projects", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  assertEquals(
    (await handler(
      signedIn("GET", "/projects?since=2026-11-13T10:00:00Z&after=" + PROJECT),
    )).status,
    200,
  );
  assertEquals(deps.accounts.calls[0], {
    name: "changes",
    args: [ANA, "2026-11-13T10:00:00Z", PROJECT, 50],
  });
  assertEquals(
    (await handler(signedIn("GET", "/projects?since=yesterday"))).status,
    400,
  );
  const saved = await handler(
    signedIn("PUT", `/projects/${PROJECT.toUpperCase()}`, SAVE),
  );
  assertEquals(saved.status, 200);
  assertEquals(await saved.json(), {
    revision: 1,
    updatedAt: "2026-11-13T10:00:00Z",
  });
  // The id is lower-cased and repeated files counted once.
  assertEquals(deps.accounts.calls[1], {
    name: "save",
    args: [ANA, PROJECT, { ...SAVE, media: [VIDEO] }],
  });
});

Deno.test("projects: bad saves are refused before the database", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  for (
    const body of [
      null,
      { ...SAVE, baseRevision: 0 },
      { ...SAVE, name: 7 },
      { ...SAVE, name: "x".repeat(201) },
      { ...SAVE, project: [] },
      { ...SAVE, media: ["not-a-hash"] },
      { ...SAVE, coverSha256: "ABC" },
      { ...SAVE, durationMs: -1 },
      { ...SAVE, editedAt: "soon" },
    ]
  ) {
    assertEquals(
      (await handler(signedIn("PUT", `/projects/${PROJECT}`, body))).status,
      400,
      JSON.stringify(body),
    );
  }
  assertEquals(
    (await handler(signedIn("PUT", "/projects/not-a-uuid", SAVE))).status,
    400,
  );
  assertEquals(deps.accounts.calls, []);
});

Deno.test("projects: conflicts, missing files and size come back as errors", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const server = { id: PROJECT, revision: 4, name: "Trip (phone 2)" };
  deps.accounts.replies.save = { ok: false, code: "conflict", project: server };
  const conflict = await handler(signedIn("PUT", `/projects/${PROJECT}`, SAVE));
  assertEquals(conflict.status, 409);
  assertEquals(await conflict.json(), {
    error: { code: "conflict", project: server },
  });
  deps.accounts.replies.save = {
    ok: false,
    code: "missing_media",
    missing: [VIDEO],
  };
  const missing = await handler(signedIn("PUT", `/projects/${PROJECT}`, SAVE));
  assertEquals(await missing.json(), {
    error: { code: "missing_media", missing: [VIDEO] },
  });
  deps.accounts.replies.save = { ok: false, code: "too_large" };
  assertEquals(
    (await handler(signedIn("PUT", `/projects/${PROJECT}`, SAVE))).status,
    413,
  );
  // Deleting names the revision it saw.
  assertEquals(
    (await handler(signedIn("DELETE", `/projects/${PROJECT}?baseRevision=3`)))
      .status,
    200,
  );
  assertEquals(deps.accounts.calls.at(-1), {
    name: "delete",
    args: [ANA, PROJECT, 3],
  });
  assertEquals(
    (await handler(signedIn("DELETE", `/projects/${PROJECT}?baseRevision=x`)))
      .status,
    400,
  );
  deps.accounts.replies.delete = {
    ok: false,
    code: "conflict",
    project: server,
  };
  assertEquals(
    (await handler(signedIn("DELETE", `/projects/${PROJECT}?baseRevision=3`)))
      .status,
    409,
  );
});

Deno.test("media: a grant says where to upload, into the user's folder", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const granted = await handler(
    signedIn("POST", "/media/grant", {
      sha256: VIDEO,
      size: 1000,
      mime: "video/mp4",
    }),
  );
  assertEquals(await granted.json(), {
    exists: false,
    upload: {
      url: deps.media.uploadUrl,
      bucket: "media",
      path: `${ANA}/${VIDEO}`,
    },
  });
  deps.accounts.replies.grant = { ok: true, exists: true };
  assertEquals(
    await (await handler(
      signedIn("POST", "/media/grant", {
        sha256: VIDEO,
        size: 1000,
        mime: "video/mp4",
      }),
    )).json(),
    {
      exists: true,
    },
  );
  deps.accounts.replies.grant = {
    ok: false,
    code: "storage_full",
    storageUsed: 900,
    storageLimit: 1000,
  };
  const full = await handler(
    signedIn("POST", "/media/grant", {
      sha256: VIDEO,
      size: 1000,
      mime: "video/mp4",
    }),
  );
  assertEquals(full.status, 507);
  assertEquals((await full.json()).error, {
    code: "storage_full",
    storageUsed: 900,
    storageLimit: 1000,
  });
  for (
    const body of [{ sha256: "x", size: 1, mime: "a/b" }, {
      sha256: VIDEO,
      size: -1,
      mime: "a/b",
    }, { sha256: VIDEO, size: 1, mime: "" }]
  ) {
    assertEquals(
      (await handler(signedIn("POST", "/media/grant", body))).status,
      400,
    );
  }
});

Deno.test("media: a confirm with the wrong size removes the upload; downloads are signed links", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  assertEquals(
    (await handler(signedIn("POST", "/media/confirm", { sha256: VIDEO })))
      .status,
    200,
  );
  deps.accounts.replies.confirm = { ok: false, code: "size_mismatch" };
  assertEquals(
    (await handler(signedIn("POST", "/media/confirm", { sha256: VIDEO })))
      .status,
    409,
  );
  assertEquals(deps.media.removed, [`${ANA}/${VIDEO}`]);
  deps.accounts.replies.confirm = { ok: false, code: "not_found" };
  assertEquals(
    (await handler(signedIn("POST", "/media/confirm", { sha256: VIDEO })))
      .status,
    404,
  );
  const link = await (await handler(signedIn("GET", `/media/${VIDEO}`))).json();
  assertEquals(link.expiresIn, 3600);
  assert(link.url.includes(`${ANA}/${VIDEO}`));
  deps.accounts.replies.ready = false;
  assertEquals((await handler(signedIn("GET", `/media/${VIDEO}`))).status, 404);
});

Deno.test("account: shown with the email; deleting removes the files, then the user", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const shown = await (await handler(signedIn("GET", "/account"))).json();
  assertEquals(shown.email, "ana@example.com");
  assertEquals(shown.storageLimit, 2147483648);
  deps.accounts.replies.files = [VIDEO];
  assertEquals((await handler(signedIn("DELETE", "/account"))).status, 200);
  assertEquals(deps.media.removed, [`${ANA}/${VIDEO}`]);
  assertEquals(deps.userAdmin.deleted, [ANA]);
  assert(!JSON.stringify(deps.logs).includes(ANA));
});

Deno.test("account: linking needs the install token too", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  assertEquals(
    (await (await handler(signedIn("POST", "/account/link"))).json()).error
      .code,
    "not_registered",
  );
  await deps.quota.register(INSTALL);
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  const linked = await handler(
    cloudRequest("POST", "/account/link", {
      headers: {
        authorization: `Bearer ${ANA_TOKEN}`,
        "x-install-token": token,
      },
    }),
  );
  assertEquals(linked.status, 200);
  assertEquals((await linked.json()).linked, true);
  assertEquals(deps.accounts.calls.at(-1), {
    name: "linkInstall",
    args: [ANA, INSTALL],
  });
});

Deno.test("cleanup: only with the secret; removes the dropped files", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  const run = (secret: string) =>
    handler(
      cloudRequest("POST", "/cleanup", {
        api: null,
        headers: { authorization: `Bearer ${secret}` },
      }),
    );
  assertEquals((await run("wrong")).status, 403);
  deps.accounts.replies.cleanup = [{ userId: ANA, sha256: VIDEO }];
  assertEquals(await (await run(RELEASE_SECRET)).json(), { removed: 1 });
  assertEquals(deps.media.removed, [`${ANA}/${VIDEO}`]);
});

Deno.test("users: tokens from the project's keys, for signed-in (not anonymous) users", async () => {
  const { publicKey, privateKey } = await generateKeyPair("ES256");
  const jwk = { ...(await exportJWK(publicKey)), kid: "k1", alg: "ES256" };
  const issuer = "https://ref.supabase.co/auth/v1";
  const verifier = new JwksUserVerifier(
    createLocalJWKSet({ keys: [jwk] }),
    issuer,
  );
  const token = (
    claims: Record<string, unknown>,
    key = privateKey,
    iss = issuer,
  ) =>
    new SignJWT({ role: "authenticated", email: "ana@example.com", ...claims })
      .setProtectedHeader({ alg: "ES256", kid: "k1" })
      .setSubject(ANA).setIssuer(iss).setAudience("authenticated")
      .setIssuedAt().setExpirationTime("1h").sign(key);
  assertEquals(await verifier.verify(await token({})), {
    id: ANA,
    email: "ana@example.com",
  });
  assertEquals(
    await verifier.verify(await token({ is_anonymous: true })),
    null,
  );
  assertEquals(await verifier.verify(await token({ role: "anon" })), null);
  assertEquals(
    await verifier.verify(
      await token({}, privateKey, "https://other.supabase.co/auth/v1"),
    ),
    null,
  );
  const other = await generateKeyPair("ES256");
  assertEquals(await verifier.verify(await token({}, other.privateKey)), null);
  assertEquals(await verifier.verify("not.a.token"), null);
  const expired = await new SignJWT({ role: "authenticated" })
    .setProtectedHeader({ alg: "ES256", kid: "k1" })
    .setSubject(ANA).setIssuer(issuer).setAudience("authenticated")
    .setExpirationTime(Math.floor(Date.now() / 1000) - 60)
    .sign(privateKey);
  assertEquals(await verifier.verify(expired), null);
});

Deno.test("uploads go to Storage's own host on Supabase", () => {
  assertEquals(
    resumableUploadUrl("https://auekkmbwfpqofjqugjsr.supabase.co/"),
    "https://auekkmbwfpqofjqugjsr.storage.supabase.co/storage/v1/upload/resumable",
  );
  assertEquals(
    resumableUploadUrl("http://127.0.0.1:54321"),
    "http://127.0.0.1:54321/storage/v1/upload/resumable",
  );
});
