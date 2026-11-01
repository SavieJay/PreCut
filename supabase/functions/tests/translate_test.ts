import { assert, assertEquals } from "@std/assert";
import { createHandler } from "../_shared/handler.ts";
import { signInstallToken } from "../_shared/install_token.ts";
import { translateHash } from "../_shared/request_hash.ts";
import { matchedLines, TranslateError } from "../_shared/translator.ts";
import { cloudRequest, INSTALL, NOW, SECRET, testDeps } from "./helpers.ts";

const LINES = [{ id: "c1", text: "Welcome to Lagos" }, {
  id: "c2",
  text: "Let's go",
}];

async function setup() {
  const deps = testDeps();
  await deps.quota.register(INSTALL);
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  return { deps, handler: createHandler(deps), token };
}

function ask(token: string, body: unknown) {
  return cloudRequest("POST", "/translate", {
    headers: {
      "x-install-token": token,
      "x-integrity-token": "play-token",
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

Deno.test("translate: lines come back by id, counted as one AI request, the verdict bound to the body", async () => {
  const { deps, handler, token } = await setup();
  const body = { lines: LINES, target: "fr" };
  const response = await handler(ask(token, body));
  assertEquals(response.status, 200);
  const reply = await response.json();
  assertEquals(reply.lines, [{ id: "c1", text: "[fr] Welcome to Lagos" }, {
    id: "c2",
    text: "[fr] Let's go",
  }]);
  assertEquals(reply.editRunsLeft, 9);
  assertEquals(
    deps.integrity.asked[0].expectedHash,
    await translateHash(
      INSTALL,
      new TextEncoder().encode(JSON.stringify(body)),
    ),
  );
  assert(!JSON.stringify(deps.logs).includes("Lagos"));
});

Deno.test("translate: bad input, used-up requests and provider failures", async () => {
  const { deps, handler, token } = await setup();
  for (
    const bad of [{ lines: [], target: "fr" }, {
      lines: LINES,
      target: "French!",
    }, { lines: [{ id: 1 }], target: "fr" }]
  ) {
    assertEquals((await handler(ask(token, bad))).status, 400);
  }
  deps.quota.freeEditRuns = 0;
  assertEquals(
    (await (await handler(ask(token, { lines: LINES, target: "yo" }))).json())
      .error.code,
    "runs_used",
  );
  deps.quota.freeEditRuns = 10;
  deps.translator.error = new TranslateError("provider_failed", "529");
  const failed = await handler(ask(token, { lines: LINES, target: "yo" }));
  assertEquals(failed.status, 502);
  assertEquals(deps.quota.runs.size, 0);
});

Deno.test("translate: Claude's answer is matched to the lines asked; missing ones keep their text", () => {
  assertEquals(
    matchedLines(LINES, [{ id: "c2", text: "Allons-y" }, {
      id: "zz",
      text: "?",
    }]),
    [{ id: "c1", text: "Welcome to Lagos" }, { id: "c2", text: "Allons-y" }],
  );
  assertEquals(matchedLines(LINES, "nonsense"), LINES);
});
