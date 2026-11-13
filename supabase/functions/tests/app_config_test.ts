import { assertEquals } from "@std/assert";
import { createHandler } from "../_shared/handler.ts";
import { cloudRequest, RELEASE_SECRET, testDeps } from "./helpers.ts";

Deno.test("config: public, for any app version, cacheable for 5 minutes", async () => {
  const deps = testDeps();
  deps.quota.config = {
    minSupportedVersionCode: 40,
    latestVersionCode: 42,
    latestVersionName: "1.2.0",
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

function releaseRequest(body: unknown, secret = RELEASE_SECRET): Request {
  return new Request("http://localhost/functions/v1/cloud/release", {
    method: "POST",
    headers: {
      authorization: `Bearer ${secret}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

const RELEASE = {
  versionCode: 72,
  versionName: "1.1.1",
  whatsNew: [" Faster export ", "Fixes"],
};

Deno.test("release: the publish workflow raises the latest version and What's new, never the minimum", async () => {
  const deps = testDeps();
  deps.quota.config = {
    ...deps.quota.config,
    minSupportedVersionCode: 40,
    latestVersionCode: 71,
  };
  const handler = createHandler(deps);
  // No API version header: the workflow isn't an app.
  const response = await handler(releaseRequest(RELEASE));
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(body.applied, true);
  assertEquals(body.config.latestVersionCode, 72);
  assertEquals(body.config.latestVersionName, "1.1.1");
  assertEquals(body.config.whatsNew, ["Faster export", "Fixes"]);
  assertEquals(body.config.minSupportedVersionCode, 40);
  // The apps see it at once.
  const config = await (await handler(
    new Request("http://localhost/functions/v1/cloud/config"),
  )).json();
  assertEquals(config.latestVersionCode, 72);
});

Deno.test("release: an older run finishing late changes nothing", async () => {
  const deps = testDeps();
  deps.quota.config = {
    ...deps.quota.config,
    latestVersionCode: 80,
    latestVersionName: "1.3.0",
  };
  const response = await createHandler(deps)(releaseRequest(RELEASE));
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(body.applied, false);
  assertEquals(body.config.latestVersionCode, 80);
  assertEquals(body.config.latestVersionName, "1.3.0");
});

Deno.test("release: the wrong secret, or none set, is forbidden", async () => {
  const deps = testDeps();
  const wrong = await createHandler(deps)(
    releaseRequest(RELEASE, "not-the-secret"),
  );
  assertEquals(wrong.status, 403);
  const off = await createHandler({ ...deps, releaseSecret: null })(
    releaseRequest(RELEASE),
  );
  assertEquals(off.status, 403);
  const unsigned = await createHandler(deps)(
    new Request("http://localhost/functions/v1/cloud/release", {
      method: "POST",
      body: JSON.stringify(RELEASE),
    }),
  );
  assertEquals(unsigned.status, 403);
  assertEquals(deps.quota.config.latestVersionCode, 0);
});

Deno.test("release: bad versions and What's new lines are refused", async () => {
  const deps = testDeps();
  const handler = createHandler(deps);
  for (
    const body of [
      null,
      { ...RELEASE, versionCode: 0 },
      { ...RELEASE, versionCode: 7.5 },
      { ...RELEASE, versionCode: "72" },
      { ...RELEASE, versionName: "" },
      { ...RELEASE, versionName: "x".repeat(33) },
      { ...RELEASE, whatsNew: "Fixes" },
      { ...RELEASE, whatsNew: [""] },
      { ...RELEASE, whatsNew: ["x".repeat(121)] },
      { ...RELEASE, whatsNew: Array(9).fill("Fix") },
    ]
  ) {
    assertEquals(
      (await handler(releaseRequest(body))).status,
      400,
      JSON.stringify(body),
    );
  }
  assertEquals(deps.quota.config.latestVersionCode, 0);
});
