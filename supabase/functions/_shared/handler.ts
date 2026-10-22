import { type Bytes, hex, isUuid, sha256 } from "./encoding.ts";
import { signInstallToken, verifyInstallToken } from "./install_token.ts";
import { IntegrityUnavailable, type IntegrityVerifier } from "./integrity.ts";
import type { QuotaStore, ReservationRefusal } from "./quota.ts";
import { registerHash, transcribeHash } from "./request_hash.ts";
import {
  TranscribeError,
  type Transcriber,
  type Transcript,
} from "./transcriber.ts";

/**
 * The `cloud` function's routes (docs/CLOUD_API.md). Every request names the API version it
 * speaks (`X-PreCut-Api`); an install proves itself with its token (`X-Install-Token`), and
 * registration and uploads also carry a Play Integrity token (`X-Integrity-Token`) bound to that
 * request. Logs never hold audio, words or tokens: only a short hash of the install, lengths and
 * the outcome.
 */

export interface Deps {
  quota: QuotaStore;
  integrity: IntegrityVerifier;
  transcriber: Transcriber;
  installTokenSecret: Bytes;
  maxUploadBytes: number;
  minApiVersion: number;
  clock: () => Date;
  log: (entry: Record<string, unknown>) => void;
}

export type ErrorCode =
  | ReservationRefusal
  | "bad_request"
  | "unsupported_audio"
  | "app_not_verified"
  | "device_not_verified"
  | "integrity_unavailable"
  | "too_large"
  | "provider_failed"
  | "upgrade_required"
  | "not_found"
  | "not_configured"
  | "server_error";

const STATUS: Record<ErrorCode, number> = {
  bad_request: 400,
  bad_audio: 422,
  unsupported_audio: 415,
  not_registered: 401,
  app_not_verified: 403,
  device_not_verified: 403,
  too_large: 413,
  too_long: 413,
  quota_used: 429,
  budget_reached: 503,
  paused: 503,
  integrity_unavailable: 503,
  provider_failed: 502,
  upgrade_required: 426,
  not_found: 404,
  not_configured: 500,
  server_error: 500,
};

const AUDIO_TYPES = new Set([
  "audio/mp4",
  "audio/m4a",
  "audio/x-m4a",
  "audio/aac",
  "audio/wav",
  "audio/x-wav",
  "audio/wave",
  "audio/mpeg",
  "audio/ogg",
  "audio/webm",
]);

export function errorResponse(
  code: ErrorCode,
  extra: Record<string, unknown> = {},
): Response {
  return json(STATUS[code], { error: { code, ...extra } });
}

export function createHandler(
  deps: Deps,
): (request: Request) => Promise<Response> {
  return async (request) => {
    const started = Date.now();
    let response: Response;
    try {
      response = await route(request, deps);
    } catch (e) {
      deps.log({
        event: "error",
        message: e instanceof Error ? e.message : String(e),
      });
      response = errorResponse("server_error");
    }
    await drainBody(request, deps.maxUploadBytes);
    deps.log({
      event: "request",
      method: request.method,
      path: routePath(request),
      status: response.status,
      ms: Date.now() - started,
    });
    return response;
  };
}

function route(request: Request, deps: Deps): Promise<Response> {
  const api = Number(request.headers.get("x-precut-api") ?? "0");
  if (!(api >= deps.minApiVersion)) {
    return Promise.resolve(errorResponse("upgrade_required"));
  }
  switch (`${request.method} ${routePath(request)}`) {
    case "POST /register":
      return register(request, deps);
    case "GET /status":
      return status(request, deps);
    case "POST /transcribe":
      return transcribe(request, deps);
    case "DELETE /me":
      return forget(request, deps);
    default:
      return Promise.resolve(errorResponse("not_found"));
  }
}

/** The path after the function's name: "/functions/v1/cloud/status" (local) and "/cloud/status" (deployed) are "/status". */
function routePath(request: Request): string {
  return new URL(request.url).pathname.replace(/^.*?\/cloud(?=\/|$)/, "") ||
    "/";
}

/** A genuine app on a genuine phone registers its install ID and gets its install token. */
async function register(request: Request, deps: Deps): Promise<Response> {
  const body = await request.json().catch(() => null);
  const installId = body?.installId;
  if (!isUuid(installId)) return errorResponse("bad_request");
  const refused = await checkIntegrity(
    request,
    await registerHash(installId),
    deps,
  );
  if (refused) return refused;
  const status = await deps.quota.register(installId);
  const installToken = await signInstallToken(
    installId,
    deps.installTokenSecret,
    deps.clock(),
  );
  deps.log({ event: "register", install: await shortHash(installId) });
  return json(200, { installToken, status });
}

async function status(request: Request, deps: Deps): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;
  const status = await deps.quota.status(install.installId);
  if (!status) return errorResponse("not_registered");
  return json(200, { status, ...install.renewal });
}

/**
 * One upload: checks the audio's size and type, the Play Integrity verdict for exactly these
 * bytes, then reserves the stated length, transcribes and charges the longer of the stated and
 * the transcribed length. A failed transcription releases the reservation.
 */
async function transcribe(request: Request, deps: Deps): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;

  const statedMs = Number(request.headers.get("x-audio-duration-ms"));
  if (!Number.isInteger(statedMs) || statedMs <= 0) {
    return errorResponse("bad_request");
  }
  const mimeType = (request.headers.get("content-type") ?? "").split(";")[0]
    .trim().toLowerCase();
  if (!AUDIO_TYPES.has(mimeType)) return errorResponse("unsupported_audio");
  const language = new URL(request.url).searchParams.get("lang");
  if (
    language !== null && language !== "auto" && !/^[a-z]{2,3}$/.test(language)
  ) return errorResponse("bad_request");
  if (
    Number(request.headers.get("content-length") ?? "0") > deps.maxUploadBytes
  ) {
    return errorResponse("too_large", { maxUploadBytes: deps.maxUploadBytes });
  }
  const audio = new Uint8Array(await request.arrayBuffer());
  if (audio.length > deps.maxUploadBytes) {
    return errorResponse("too_large", { maxUploadBytes: deps.maxUploadBytes });
  }
  if (audio.length === 0) return errorResponse("bad_audio");

  const refused = await checkIntegrity(
    request,
    await transcribeHash(install.installId, audio),
    deps,
  );
  if (refused) return refused;

  const seconds = Math.ceil(statedMs / 1000);
  const reservation = await deps.quota.reserve(install.installId, seconds);
  if (!reservation.ok) {
    const { ok: _refused, code, ...extra } = reservation;
    return errorResponse(code, extra);
  }
  let transcript: Transcript;
  try {
    transcript = await deps.transcriber.transcribe(
      audio,
      mimeType,
      language === "auto" ? null : language,
    );
  } catch (e) {
    await deps.quota.settle(install.installId, reservation.month, seconds, 0);
    if (e instanceof TranscribeError) {
      deps.log({
        event: "transcribe",
        install: await shortHash(install.installId),
        seconds,
        outcome: e.code,
        reason: e.message,
      });
      return errorResponse(e.code);
    }
    throw e;
  }
  const charged = Math.max(seconds, Math.ceil(transcript.durationMs / 1000));
  const status = await deps.quota.settle(
    install.installId,
    reservation.month,
    seconds,
    charged,
  );
  deps.log({
    event: "transcribe",
    install: await shortHash(install.installId),
    bytes: audio.length,
    seconds,
    charged,
    words: transcript.words.length,
    outcome: "ok",
  });
  return json(200, {
    transcript,
    secondsLeft: status?.secondsLeft ?? 0,
    ...install.renewal,
  });
}

async function forget(request: Request, deps: Deps): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;
  const deleted = await deps.quota.forget(install.installId);
  deps.log({
    event: "forget",
    install: await shortHash(install.installId),
    deleted,
  });
  return json(200, { deleted });
}

/** The install a request's token names, and a fresh token to hand back when it's close to expiring. */
async function authenticate(
  request: Request,
  deps: Deps,
): Promise<
  { installId: string; renewal: { installToken?: string } } | Response
> {
  const token = request.headers.get("x-install-token");
  const now = deps.clock();
  const verified = token
    ? await verifyInstallToken(token, deps.installTokenSecret, now)
    : null;
  if (!verified) return errorResponse("not_registered");
  const renewal = verified.renew
    ? {
      installToken: await signInstallToken(
        verified.installId,
        deps.installTokenSecret,
        now,
      ),
    }
    : {};
  return { installId: verified.installId, renewal };
}

/** A response refusing the request, or null when its Play Integrity verdict is good for [expectedHash]. */
async function checkIntegrity(
  request: Request,
  expectedHash: string,
  deps: Deps,
): Promise<Response | null> {
  const token = request.headers.get("x-integrity-token");
  if (!token) return errorResponse("app_not_verified");
  try {
    const result = await deps.integrity.verify(token, expectedHash);
    if (result.ok) return null;
    deps.log({
      event: "integrity",
      outcome: result.code,
      reason: result.reason,
    });
    return errorResponse(result.code);
  } catch (e) {
    if (!(e instanceof IntegrityUnavailable)) throw e;
    deps.log({ event: "integrity", outcome: "unavailable", reason: e.message });
    return errorResponse("integrity_unavailable");
  }
}

/**
 * Reads what's left of [request]'s body, up to [limit] bytes, then stops. Supabase's hosted
 * runtime doesn't pass a reply back while the request body is still arriving, so a refusal sent
 * before an upload was read would hang the caller (seen on a deployed project: 1 KB answered,
 * 100 KB never did). Every response therefore waits for the upload to be read first.
 */
export async function drainBody(
  request: Request,
  limit: number,
): Promise<void> {
  if (!request.body || request.bodyUsed) return;
  const reader = request.body.getReader();
  let read = 0;
  try {
    while (read <= limit) {
      const { done, value } = await reader.read();
      if (done) return;
      read += value.length;
    }
    await reader.cancel();
  } catch {
    // The caller went away: nothing left to read.
  }
}

/** Enough of the install ID's hash to follow one install through the logs, not to recover the ID. */
async function shortHash(installId: string): Promise<string> {
  return hex(await sha256(installId)).slice(0, 10);
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
