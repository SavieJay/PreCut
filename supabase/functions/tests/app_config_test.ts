import { assertEquals } from "@std/assert";
import { createHandler } from "../_shared/handler.ts";
import { cloudRequest, testDeps } from "./helpers.ts";

Deno.test("config: public, for any app version, cacheable for 5 minutes", async () => {
  const deps = testDeps();
  deps.quota.config = {
    minSupportedVersionCode: 40,
    latestVersionCode: 42,
    forceMessage: "Please update",
    whatsNew: ["AI edit"],
    downloadUrl: null,
    updatedAt: "2026-11-01T10:00:00+00:00",
  };
  const handler = createHandler(deps);
  // No API version header, no install token, no Play Integrity: an old or unregistered app still learns it must update.
  const response = await handler(
    new Request("http://localhost/functions/v1/cloud/config?platform=android"),
  );
  assertEquals(response.status, 200);
  assertEquals(response.headers.get("cache-control"), "public, max-age=300");
  assertEquals(await response.json(), deps.quota.config);
  assertEquals(deps.integrity.asked.length, 0);
});

Deno.test("config: Android only; other routes still need the API version", async () => {
  const handler = createHandler(testDeps());
  const ios = await handler(
    new Request("http://localhost/functions/v1/cloud/config?platform=ios"),
  );
  assertEquals(ios.status, 400);
  const status = await handler(
    new Request("http://localhost/functions/v1/cloud/status"),
  );
  assertEquals((await status.json()).error.code, "upgrade_required");
  assertEquals(
    (await handler(cloudRequest("GET", "/config"))).status,
    200,
  );
});
