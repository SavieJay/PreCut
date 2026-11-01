import { type EditAgent, EditAgentError } from "./edit_agent.ts";
import { type Bytes, hex, isUuid, sha256 } from "./encoding.ts";
import { signInstallToken, verifyInstallToken } from "./install_token.ts";
import { IntegrityUnavailable, type IntegrityVerifier } from "./integrity.ts";
import type { EditRefusal, QuotaStore, ReservationRefusal } from "./quota.ts";
import {
  BillingUnavailable,
  type PlayBilling,
  type Subscription,
} from "./play_billing.ts";
import {
  editHash,
  purchaseHash,
  registerHash,
  transcribeHash,
  translateHash,
} from "./request_hash.ts";
import {
  type CaptionLine,
  TranslateError,
  type Translator,
} from "./translator.ts";
import {
  TranscribeError,
  type Transcriber,
  type Transcript,
} from "./transcriber.ts";

/**
 * The `cloud` function's routes (docs/CLOUD_API.md). Every request names the API version it
 * speaks (`X-PreCut-Api`); an install proves itself with its token (`X-Install-Token`), and
 * registration and uploads also carry a Play Integrity token (`X-Integrity-Token`) bound to that
 * request. Logs never hold audio, words, conversations or tokens: only a short hash of the install,
 * lengths, token counts and the outcome.
 */

export interface Deps {
  quota: QuotaStore;
  integrity: IntegrityVerifier;
  transcriber: Transcriber;
  /** Claude for AI edits; null when no Anthropic key is set (the other routes still work). */
  editAgent: EditAgent | null;
  /** Caption translation (Phase 26B); null without an Anthropic key. */
  translator: Translator | null;
  /** Google Play's subscription records (Phase 25); null when not configured. */
  billing: PlayBilling | null;
  /** The subscription product ids that make an install Pro. */
  proProducts: readonly string[];
  /** The secret in the Real-time developer notifications push URL; null switches /rtdn off. */
  rtdnSecret: string | null;
  installTokenSecret: Bytes;
  maxUploadBytes: number;
  minApiVersion: number;
  clock: () => Date;
  log: (entry: Record<string, unknown>) => void;
}

export type ErrorCode =
  | ReservationRefusal
  | EditRefusal
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
  | "bad_purchase"
  | "billing_unavailable"
  | "forbidden"
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
  runs_used: 429,
  steps_used: 429,
  budget_reached: 503,
  paused: 503,
  integrity_unavailable: 503,
  provider_failed: 502,
  upgrade_required: 426,
  not_found: 404,
  not_configured: 500,
  bad_purchase: 400,
  billing_unavailable: 503,
  forbidden: 403,
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
  // Update rules answer every app version, however old: they're how an old one learns to update.
  if (`${request.method} ${routePath(request)}` === "GET /config") {
    return appConfig(request, deps);
  }
  // Google's Pub/Sub sends no API version.
  if (`${request.method} ${routePath(request)}` === "POST /rtdn") {
    return rtdn(request, deps);
  }
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
    case "POST /edit":
      return edit(request, deps);
    case "POST /purchase":
      return purchase(request, deps);
    case "POST /translate":
      return translate(request, deps);
    default:
      return Promise.resolve(errorResponse("not_found"));
  }
}

const MAX_TRANSLATE_BYTES = 64 * 1024;
const MAX_TRANSLATE_LINES = 400;

/**
 * Caption translation (Phase 26B, Pro with a free taste): the caption lines and a language code;
 * counted as one AI request against the plan's monthly AI edit requests and the AI budget. The
 * verdict is bound to the exact body; the lines are never stored or logged.
 */
async function translate(request: Request, deps: Deps): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;
  if (!deps.translator) return errorResponse("not_configured");
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length > MAX_TRANSLATE_BYTES) {
    return errorResponse("too_large", { maxUploadBytes: MAX_TRANSLATE_BYTES });
  }
  let body: { lines?: unknown; target?: unknown };
  try {
    body = JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return errorResponse("bad_request");
  }
  const lines = body?.lines;
  const target = body?.target;
  const valid = Array.isArray(lines) && lines.length > 0 &&
    lines.length <= MAX_TRANSLATE_LINES &&
    lines.every((l) =>
      typeof l?.id === "string" && typeof l?.text === "string"
    ) &&
    typeof target === "string" &&
    /^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$/.test(target);
  if (!valid) return errorResponse("bad_request");
  const refused = await checkIntegrity(
    request,
    await translateHash(install.installId, bytes),
    deps,
  );
  if (refused) return refused;
  const runId = crypto.randomUUID();
  const grant = await deps.quota.beginEdit(install.installId, runId);
  if (!grant.ok) {
    const { ok: _refused, code, ...extra } = grant;
    return errorResponse(code, extra);
  }
  const who = { install: await shortHash(install.installId) };
  let translation;
  try {
    translation = await deps.translator.translate(
      (lines as CaptionLine[]).map((l) => ({ id: l.id, text: l.text })),
      target as string,
    );
  } catch (e) {
    if (!(e instanceof TranslateError)) throw e;
    await deps.quota.releaseEdit(runId);
    deps.log({
      event: "translate",
      ...who,
      outcome: e.code,
      reason: e.message,
    });
    return errorResponse(e.code);
  }
  await deps.quota.settleEdit(runId, translation.usage);
  deps.log({
    event: "translate",
    ...who,
    lines: (lines as unknown[]).length,
    target,
    costMicrousd: translation.usage.costMicrousd,
    outcome: "ok",
  });
  return json(200, {
    lines: translation.lines,
    editRunsLeft: grant.editRunsLeft,
    ...install.renewal,
  });
}

/**
 * PreCut Pro (Phase 25): the app sends a subscription's purchase token (after buying, or to
 * restore); Google's record of it decides, it's linked to this install, acknowledged if needed,
 * and the install's status (plan and quotas) comes back. The verdict is bound to this token.
 */
async function purchase(request: Request, deps: Deps): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;
  if (!deps.billing) return errorResponse("not_configured");
  const body = await request.json().catch(() => null);
  const token = body?.purchaseToken;
  if (typeof token !== "string" || token.length < 10 || token.length > 4096) {
    return errorResponse("bad_request");
  }
  const refused = await checkIntegrity(
    request,
    await purchaseHash(install.installId, token),
    deps,
  );
  if (refused) return refused;
  const who = { install: await shortHash(install.installId) };
  let sub: Subscription | null;
  try {
    sub = await deps.billing.subscription(token);
  } catch (e) {
    if (!(e instanceof BillingUnavailable)) throw e;
    deps.log({
      event: "purchase",
      ...who,
      outcome: "unavailable",
      reason: e.message,
    });
    return errorResponse("billing_unavailable");
  }
  if (!sub || !deps.proProducts.includes(sub.productId)) {
    deps.log({ event: "purchase", ...who, outcome: "bad_purchase" });
    return errorResponse("bad_purchase");
  }
  const saved = await deps.quota.savePurchase(
    install.installId,
    hex(await sha256(token)),
    token,
    sub,
  );
  if (!saved.ok) return errorResponse(saved.code);
  if (!sub.acknowledged && (sub.state === "active" || sub.state === "grace")) {
    try {
      await deps.billing.acknowledge(sub.productId, token);
    } catch (e) {
      // Not fatal: the next check acknowledges it (Google allows three days).
      if (!(e instanceof BillingUnavailable)) throw e;
      deps.log({
        event: "acknowledge",
        ...who,
        outcome: "unavailable",
        reason: e.message,
      });
    }
  }
  deps.log({ event: "purchase", ...who, state: sub.state, outcome: "ok" });
  const status = await deps.quota.status(install.installId);
  return json(200, { status, ...install.renewal });
}

/**
 * Real-time developer notifications (Phase 25), pushed by Google Cloud Pub/Sub to
 * `/rtdn?secret=...`. A notification only says "look again": the purchase is looked up with
 * Google, and only purchases an app already sent are updated. Pub/Sub retries anything but 2xx,
 * so only a failure worth retrying (Google unreachable) answers otherwise.
 */
async function rtdn(request: Request, deps: Deps): Promise<Response> {
  const secret = new URL(request.url).searchParams.get("secret") ?? "";
  if (!deps.rtdnSecret || !(await sameSecret(secret, deps.rtdnSecret))) {
    deps.log({ event: "rtdn", outcome: "forbidden" });
    return errorResponse("forbidden");
  }
  const envelope = await request.json().catch(() => null);
  let note: {
    packageName?: string;
    subscriptionNotification?: {
      purchaseToken?: string;
      notificationType?: number;
    };
  } | null = null;
  try {
    note = JSON.parse(atob(envelope?.message?.data ?? ""));
  } catch {
    note = null;
  }
  const token = note?.subscriptionNotification?.purchaseToken;
  if (typeof token !== "string" || !deps.billing) {
    // A test notification, one-time products, or not set up: nothing to do, and nothing to retry.
    deps.log({ event: "rtdn", outcome: "ignored" });
    return json(200, { ok: true });
  }
  const tokenHash = hex(await sha256(token));
  if (!(await deps.quota.purchaseKnown(tokenHash))) {
    deps.log({ event: "rtdn", outcome: "unknown_purchase" });
    return json(200, { ok: true });
  }
  let sub: Subscription | null;
  try {
    sub = await deps.billing.subscription(token);
  } catch (e) {
    if (!(e instanceof BillingUnavailable)) throw e;
    deps.log({ event: "rtdn", outcome: "unavailable", reason: e.message });
    return errorResponse("billing_unavailable");
  }
  if (sub && deps.proProducts.includes(sub.productId)) {
    await deps.quota.savePurchase(null, tokenHash, token, sub);
  }
  deps.log({
    event: "rtdn",
    type: note?.subscriptionNotification?.notificationType,
    state: sub?.state,
    outcome: "ok",
  });
  return json(200, { ok: true });
}

/** Compares secrets in constant time (through their hashes, so lengths don't leak either). */
async function sameSecret(given: string, expected: string): Promise<boolean> {
  const [a, b] = [await sha256(given), await sha256(expected)];
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

/**
 * Forced updates (Phase 23): public, with no token or Play Integrity check, so first launches and
 * copies not installed from Play get it too. Caches may keep it for 5 minutes.
 */
async function appConfig(request: Request, deps: Deps): Promise<Response> {
  const platform = new URL(request.url).searchParams.get("platform") ??
    "android";
  if (platform !== "android") return errorResponse("bad_request");
  const config = await deps.quota.appConfig(platform);
  const response = json(200, config);
  response.headers.set("cache-control", "public, max-age=300");
  return response;
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

/** A conversation's size limit: a large project's brief plus a few steps of tool results. */
export const MAX_EDIT_BYTES = 512 * 1024;
const MAX_EDIT_MESSAGES = 200;

/**
 * One AI edit step (Phase 22C): the conversation so far, the Play Integrity verdict for exactly
 * this body, and a step of the install's run; then Claude's reply, which the app appends and acts
 * on (its tools run on the phone). Only token counts and the cost are kept. A provider failure
 * gives the step back.
 */
async function edit(request: Request, deps: Deps): Promise<Response> {
  const install = await authenticate(request, deps);
  if (install instanceof Response) return install;
  if (!deps.editAgent) return errorResponse("not_configured");
  if (Number(request.headers.get("content-length") ?? "0") > MAX_EDIT_BYTES) {
    return errorResponse("too_large", { maxUploadBytes: MAX_EDIT_BYTES });
  }
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length > MAX_EDIT_BYTES) {
    return errorResponse("too_large", { maxUploadBytes: MAX_EDIT_BYTES });
  }
  let body: { runId?: unknown; messages?: unknown };
  try {
    body = JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return errorResponse("bad_request");
  }
  const runId = body?.runId;
  if (!isUuid(runId) || !isConversation(body.messages)) {
    return errorResponse("bad_request");
  }
  const refused = await checkIntegrity(
    request,
    await editHash(install.installId, bytes),
    deps,
  );
  if (refused) return refused;
  const grant = await deps.quota.beginEdit(install.installId, runId);
  if (!grant.ok) {
    const { ok: _refused, code, ...extra } = grant;
    return errorResponse(code, extra);
  }
  const who = {
    install: await shortHash(install.installId),
    run: await shortHash(runId),
  };
  let step;
  try {
    step = await deps.editAgent.step(body.messages);
  } catch (e) {
    if (!(e instanceof EditAgentError)) throw e;
    await deps.quota.releaseEdit(runId);
    deps.log({ event: "edit", ...who, outcome: e.code, reason: e.message });
    return errorResponse(e.code);
  }
  await deps.quota.settleEdit(runId, step.usage);
  deps.log({
    event: "edit",
    ...who,
    messages: body.messages.length,
    bytes: bytes.length,
    model: step.model,
    stopReason: step.stopReason,
    inputTokens: step.usage.inputTokens,
    outputTokens: step.usage.outputTokens,
    cacheReadTokens: step.usage.cacheReadTokens,
    costMicrousd: step.usage.costMicrousd,
    outcome: "ok",
  });
  return json(200, {
    content: step.content,
    stopReason: step.stopReason,
    stepsLeft: grant.stepsLeft,
    editRunsLeft: grant.editRunsLeft,
    ...install.renewal,
  });
}

/**
 * A conversation as the app sends it: user and assistant turns taking turns, starting with the
 * user; the user's turns hold text and tool results only (Claude's own turns come back as they were).
 */
function isConversation(messages: unknown): messages is unknown[] {
  if (
    !Array.isArray(messages) || messages.length === 0 ||
    messages.length > MAX_EDIT_MESSAGES
  ) return false;
  return messages.every((message, i) => {
    if (typeof message !== "object" || message === null) return false;
    const { role, content } = message as { role?: unknown; content?: unknown };
    if (role !== (i % 2 === 0 ? "user" : "assistant")) return false;
    if (typeof content === "string") {
      return role === "user" && content.length > 0;
    }
    if (!Array.isArray(content) || content.length === 0) return false;
    return content.every((block) => {
      const type = (block as { type?: unknown } | null)?.type;
      if (typeof type !== "string") return false;
      return role === "assistant" || type === "text" || type === "tool_result";
    });
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
