import { depsFromEnv, MAX_UPLOAD_BYTES } from "../_shared/config.ts";
import { createHandler, drainBody, errorResponse } from "../_shared/handler.ts";

/**
 * PreCut's cloud AI endpoint (Phase 20): register, status, transcribe, delete. Set up on first
 * request; until its secrets are set it answers "not_configured" and logs which one is missing.
 */

let handler: ((request: Request) => Promise<Response>) | null = null;

Deno.serve(async (request) => {
  if (!handler) {
    try {
      handler = createHandler(depsFromEnv(Deno.env.toObject()));
    } catch (e) {
      console.error(
        JSON.stringify({
          event: "config",
          message: e instanceof Error ? e.message : String(e),
        }),
      );
      await drainBody(request, MAX_UPLOAD_BYTES);
      return errorResponse("not_configured");
    }
  }
  return await handler(request);
});
