import { assert, assertEquals } from "@std/assert";
import { createHandler } from "../_shared/handler.ts";
import { signInstallToken } from "../_shared/install_token.ts";
import { templateHash } from "../_shared/request_hash.ts";
import { cloudRequest, INSTALL, NOW, SECRET, testDeps } from "./helpers.ts";

const TEMPLATE = {
  id: "t1",
  title: "Trip",
  slots: [{ clipId: "a" }],
  project: { id: "t1", name: "Trip", createdAt: 1, updatedAt: 1, clips: [] },
};

async function setup() {
  const deps = testDeps();
  await deps.quota.register(INSTALL);
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  return { deps, handler: createHandler(deps), token };
}

function share(token: string, body: unknown) {
  return cloudRequest("POST", "/templates", {
    headers: {
      "x-install-token": token,
      "x-integrity-token": "play-token",
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

Deno.test("templates: shared under a short id, bound to the body, then open publicly with CORS", async () => {
  const { deps, handler, token } = await setup();
  const body = { template: TEMPLATE };
  const response = await handler(share(token, body));
  assertEquals(response.status, 200);
  const { id } = await response.json();
  assert(/^[A-Za-z0-9]{8}$/.test(id));
  assertEquals(
    deps.integrity.asked[0].expectedHash,
    await templateHash(INSTALL, new TextEncoder().encode(JSON.stringify(body))),
  );
  // The website asks without an API version.
  const opened = await handler(
    new Request(`http://localhost/functions/v1/cloud/templates/${id}`),
  );
  assertEquals(opened.status, 200);
  assertEquals(opened.headers.get("access-control-allow-origin"), "*");
  assertEquals((await opened.json()).template, TEMPLATE);
  assertEquals(deps.quota.templates.get(id)?.opens, 1);
});

Deno.test("templates: unknown ids, bad bodies and the daily limit", async () => {
  const { deps, handler, token } = await setup();
  assertEquals(
    (await handler(
      new Request("http://localhost/functions/v1/cloud/templates/ABCDEFGH"),
    )).status,
    404,
  );
  assertEquals((await handler(share(token, { template: "x" }))).status, 400);
  assertEquals(
    (await handler(share(token, { template: { title: "no project" } }))).status,
    400,
  );
  deps.quota.templatesPerDay = 0;
  assertEquals(
    (await (await handler(share(token, { template: TEMPLATE }))).json()).error
      .code,
    "templates_used",
  );
});
