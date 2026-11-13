import type { ProjectSave, User } from "./accounts.ts";
import { isUuid } from "./encoding.ts";
import {
  authenticate,
  type Deps,
  errorResponse,
  json,
  sameSecret,
  shortHash,
} from "./handler.ts";

/**
 * Accounts and cloud projects (Phase 30, docs/CLOUD_API.md). Every route here needs the signed-in
 * user's Supabase access token (`Authorization: Bearer …`); `POST /account/link` also the
 * install token. Logs hold a short hash of the user, never names, emails or projects.
 */

const CHANGES_PAGE = 50;
const DOWNLOAD_SECONDS = 3600;
const SHA256 = /^[0-9a-f]{64}$/;

/** Answers an account route, or null when [path] isn't one. */
export function accountRoute(
  method: string,
  path: string,
  request: Request,
  deps: Deps,
): Promise<Response> | null {
  const project = path.match(/^\/projects\/([^/]+)$/);
  const file = path.match(/^\/media\/([0-9a-f]{64})$/);
  const key = `${method} ${path}`;
  if (key === "GET /projects") {
    return signedIn(request, deps, (u) => changes(request, u, deps));
  }
  const id = project?.[1].toLowerCase();
  if (id && method === "PUT") {
    return signedIn(request, deps, (u) => save(request, id, u, deps));
  }
  if (id && method === "DELETE") {
    return signedIn(request, deps, (u) => remove(request, id, u, deps));
  }
  if (key === "POST /media/grant") {
    return signedIn(request, deps, (u) => grant(request, u, deps));
  }
  if (key === "POST /media/confirm") {
    return signedIn(request, deps, (u) => confirm(request, u, deps));
  }
  if (file && method === "GET") {
    return signedIn(request, deps, (u) => download(file[1], u, deps));
  }
  if (key === "GET /account") {
    return signedIn(request, deps, (u) => account(u, deps));
  }
  if (key === "DELETE /account") {
    return signedIn(request, deps, (u) => deleteAccount(u, deps));
  }
  if (key === "POST /account/link") {
    return signedIn(request, deps, (u) => link(request, u, deps));
  }
  return null;
}

async function signedIn(
  request: Request,
  deps: Deps,
  run: (user: User) => Promise<Response>,
): Promise<Response> {
  if (!deps.users) return errorResponse("not_configured");
  const token = (request.headers.get("authorization") ?? "").match(
    /^Bearer (.+)$/,
  )?.[1];
  const user = token ? await deps.users.verify(token) : null;
  if (!user) return errorResponse("signed_out");
  return await run(user);
}

async function changes(
  request: Request,
  user: User,
  deps: Deps,
): Promise<Response> {
  const query = new URL(request.url).searchParams;
  const since = query.get("since");
  const after = query.get("after");
  if (since !== null && Number.isNaN(Date.parse(since))) {
    return errorResponse("bad_request");
  }
  if (after !== null && !isUuid(after)) return errorResponse("bad_request");
  return json(
    200,
    await deps.accounts.changes(user.id, since, after, CHANGES_PAGE),
  );
}

async function save(
  request: Request,
  id: string,
  user: User,
  deps: Deps,
): Promise<Response> {
  if (!isUuid(id)) return errorResponse("bad_request");
  const body = saveFrom(await request.json().catch(() => null));
  if (!body) return errorResponse("bad_request");
  const result = await deps.accounts.save(user.id, id, body);
  deps.log({
    event: "project_save",
    user: await shortHash(user.id),
    media: body.media.length,
    outcome: result.ok ? "ok" : result.code,
  });
  if (result.ok) {
    return json(200, {
      revision: result.revision,
      updatedAt: result.updatedAt,
    });
  }
  if (result.code === "conflict") {
    return errorResponse("conflict", { project: result.project });
  }
  if (result.code === "missing_media") {
    return errorResponse("missing_media", { missing: result.missing });
  }
  return errorResponse("too_large");
}

/** A save body: names and numbers in range, files as SHA-256 hex, the project an object. */
function saveFrom(body: unknown): ProjectSave | null {
  if (typeof body !== "object" || body === null) return null;
  const b = body as Record<string, unknown>;
  const base = b.baseRevision ?? null;
  if (base !== null && !(Number.isInteger(base) && (base as number) > 0)) {
    return null;
  }
  if (typeof b.name !== "string" || b.name.length > 200) return null;
  if (
    typeof b.project !== "object" || b.project === null ||
    Array.isArray(b.project)
  ) return null;
  if (
    !Array.isArray(b.media) || b.media.length > 2000 ||
    !b.media.every((m) => typeof m === "string" && SHA256.test(m))
  ) {
    return null;
  }
  const cover = b.coverSha256 ?? null;
  if (cover !== null && !(typeof cover === "string" && SHA256.test(cover))) {
    return null;
  }
  if (!Number.isInteger(b.durationMs) || (b.durationMs as number) < 0) {
    return null;
  }
  if (typeof b.editedAt !== "string" || Number.isNaN(Date.parse(b.editedAt))) {
    return null;
  }
  return {
    baseRevision: base as number | null,
    name: b.name,
    project: b.project as Record<string, unknown>,
    media: [...new Set(b.media as string[])],
    coverSha256: cover as string | null,
    durationMs: b.durationMs as number,
    editedAt: b.editedAt,
  };
}

async function remove(
  request: Request,
  id: string,
  user: User,
  deps: Deps,
): Promise<Response> {
  const raw = new URL(request.url).searchParams.get("baseRevision");
  const base = raw === null ? null : Number(raw);
  if (!isUuid(id) || (base !== null && !(Number.isInteger(base) && base > 0))) {
    return errorResponse("bad_request");
  }
  const result = await deps.accounts.delete(user.id, id, base);
  if (!result.ok) return errorResponse("conflict", { project: result.project });
  return json(200, { revision: result.revision });
}

async function grant(
  request: Request,
  user: User,
  deps: Deps,
): Promise<Response> {
  const body = await request.json().catch(() => null) as
    | Record<string, unknown>
    | null;
  const sha = body?.sha256;
  const size = body?.size;
  const mime = body?.mime;
  if (
    typeof sha !== "string" || !SHA256.test(sha) ||
    !Number.isSafeInteger(size) || (size as number) < 0 ||
    typeof mime !== "string" || mime.length < 1 || mime.length > 100
  ) return errorResponse("bad_request");
  const result = await deps.accounts.grant(user.id, sha, size as number, mime);
  if (!result.ok && result.code === "storage_full") {
    return errorResponse("storage_full", {
      storageUsed: result.storageUsed,
      storageLimit: result.storageLimit,
    });
  }
  if (!result.ok) {
    return errorResponse("too_large", { maxUploadBytes: result.maxFileBytes });
  }
  if (result.exists) return json(200, { exists: true });
  return json(200, {
    exists: false,
    upload: {
      url: deps.media.uploadUrl,
      bucket: "media",
      path: `${user.id}/${sha}`,
    },
  });
}

async function confirm(
  request: Request,
  user: User,
  deps: Deps,
): Promise<Response> {
  const body = await request.json().catch(() => null) as
    | Record<string, unknown>
    | null;
  const sha = body?.sha256;
  if (typeof sha !== "string" || !SHA256.test(sha)) {
    return errorResponse("bad_request");
  }
  const result = await deps.accounts.confirm(user.id, sha);
  if (result.ok) return json(200, {});
  if (result.code === "size_mismatch") {
    // The phone uploads it again; a stored object with the wrong size would block that.
    await deps.media.remove([`${user.id}/${sha}`]);
    return errorResponse("size_mismatch");
  }
  return errorResponse("not_found");
}

async function download(
  sha: string,
  user: User,
  deps: Deps,
): Promise<Response> {
  if (!(await deps.accounts.ready(user.id, sha))) {
    return errorResponse("not_found");
  }
  const url = await deps.media.signedUrl(`${user.id}/${sha}`, DOWNLOAD_SECONDS);
  if (!url) return errorResponse("not_found");
  return json(200, { url, expiresIn: DOWNLOAD_SECONDS });
}

async function account(user: User, deps: Deps): Promise<Response> {
  return json(200, {
    email: user.email,
    ...(await deps.accounts.account(user.id)),
  });
}

/** Removes the stored files, then the user, whose rows go with it (foreign keys). */
async function deleteAccount(user: User, deps: Deps): Promise<Response> {
  const files = await deps.accounts.files(user.id);
  await deps.media.remove(files.map((sha) => `${user.id}/${sha}`));
  await deps.userAdmin.deleteUser(user.id);
  deps.log({
    event: "account_delete",
    user: await shortHash(user.id),
    files: files.length,
    outcome: "ok",
  });
  return json(200, { deleted: true });
}

async function link(
  request: Request,
  user: User,
  deps: Deps,
): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;
  if (!(await deps.accounts.linkInstall(user.id, install.installId))) {
    return errorResponse("not_registered");
  }
  const status = await deps.quota.status(install.installId);
  return json(200, { linked: true, status, ...install.renewal });
}

/**
 * The daily clean-up (POST /cleanup, `Authorization: Bearer <RELEASE_SECRET>`, from the scheduled
 * workflow): drops what nobody uses and removes those files from Storage.
 */
export async function cleanup(request: Request, deps: Deps): Promise<Response> {
  const given = (request.headers.get("authorization") ?? "").replace(
    /^Bearer /,
    "",
  );
  if (!deps.releaseSecret || !(await sameSecret(given, deps.releaseSecret))) {
    deps.log({ event: "cleanup", outcome: "forbidden" });
    return errorResponse("forbidden");
  }
  const dropped = await deps.accounts.cleanup();
  await deps.media.remove(dropped.map((d) => `${d.userId}/${d.sha256}`));
  deps.log({ event: "cleanup", files: dropped.length, outcome: "ok" });
  return json(200, { removed: dropped.length });
}
