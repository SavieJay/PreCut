import { assert, assertEquals } from "@std/assert";
import { EditAgentError } from "../_shared/edit_agent.ts";
import { createHandler } from "../_shared/handler.ts";
import { signInstallToken } from "../_shared/install_token.ts";
import { editHash } from "../_shared/request_hash.ts";
import {
  cloudRequest,
  INSTALL,
  NOW,
  SECRET,
  stepReply,
  testDeps,
} from "./helpers.ts";

const RUN = "7b0c2f8e-1d3a-4e5b-9c6d-0a1b2c3d4e5f";

const CONVERSATION = [{
  role: "user",
  content: [
    { type: "text", text: '{"clips":[{"id":"a","length_seconds":12}]}' },
    { type: "text", text: "Make it shorter" },
  ],
}];

async function setup() {
  const deps = testDeps();
  await deps.quota.register(INSTALL);
  const token = await signInstallToken(INSTALL, SECRET, NOW);
  return { deps, handler: createHandler(deps), token };
}

function step(
  token: string,
  body: unknown,
  headers: Record<string, string> = {},
) {
  return cloudRequest("POST", "/edit", {
    headers: {
      "x-install-token": token,
      "x-integrity-token": "play-token",
      "content-type": "application/json",
      ...headers,
    },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

async function errorCode(response: Response): Promise<string> {
  return (await response.json()).error.code;
}

Deno.test("edit: Claude's reply comes back to append, the verdict bound to this exact body, the usage kept", async () => {
  const { deps, handler, token } = await setup();
  const body = JSON.stringify({ runId: RUN, messages: CONVERSATION });
  const response = await handler(step(token, body));
  assertEquals(response.status, 200);
  const reply = await response.json();
  assertEquals(reply.content, stepReply("Trimmed the first clip.").content);
  assertEquals(reply.stopReason, "end_turn");
  assertEquals(reply.stepsLeft, 7);
  assertEquals(reply.editRunsLeft, 9);
  assertEquals(deps.editAgent.asked, [CONVERSATION]);
  assertEquals(
    deps.integrity.asked[0].expectedHash,
    await editHash(INSTALL, new TextEncoder().encode(body)),
  );
  assertEquals(deps.quota.runs.get(RUN)?.usage[0].costMicrousd, 6_580);
});

Deno.test("edit: the next steps of a run use steps, not runs, until the run's steps are used", async () => {
  const { deps, handler, token } = await setup();
  deps.quota.maxEditSteps = 2;
  const conversation = [
    ...CONVERSATION,
    {
      role: "assistant",
      content: [{ type: "tool_use", id: "t1", name: "trim_clip", input: {} }],
    },
    {
      role: "user",
      content: [{ type: "tool_result", tool_use_id: "t1", content: "Done" }],
    },
  ];
  assertEquals(
    (await (await handler(step(token, { runId: RUN, messages: CONVERSATION })))
      .json()).stepsLeft,
    1,
  );
  const second =
    await (await handler(step(token, { runId: RUN, messages: conversation })))
      .json();
  assertEquals(second.stepsLeft, 0);
  assertEquals(second.editRunsLeft, 9);
  const third = await handler(
    step(token, { runId: RUN, messages: conversation }),
  );
  assertEquals(third.status, 429);
  assertEquals(await errorCode(third), "steps_used");
});

Deno.test("edit: used-up runs are refused with when they come back, before asking Claude", async () => {
  const { deps, handler, token } = await setup();
  deps.quota.freeEditRuns = 0;
  const response = await handler(
    step(token, { runId: RUN, messages: CONVERSATION }),
  );
  assertEquals(response.status, 429);
  const error = (await response.json()).error;
  assertEquals(error.code, "runs_used");
  assertEquals(error.resetsAt, "2026-11-01T00:00:00+00:00");
  assertEquals(deps.editAgent.asked.length, 0);
});

Deno.test("edit: malformed conversations and runs are refused before the verdict", async () => {
  const { deps, handler, token } = await setup();
  const bad = [
    "not json",
    { runId: "not-a-uuid", messages: CONVERSATION },
    { runId: RUN, messages: [] },
    {
      runId: RUN,
      messages: [{
        role: "assistant",
        content: [{ type: "text", text: "Hi" }],
      }],
    },
    { runId: RUN, messages: [...CONVERSATION, ...CONVERSATION] },
    // The user's turns carry text and tool results only.
    {
      runId: RUN,
      messages: [{ role: "user", content: [{ type: "image", source: {} }] }],
    },
  ];
  for (const body of bad) {
    const response = await handler(step(token, body));
    assertEquals(response.status, 400, JSON.stringify(body));
  }
  assertEquals(deps.integrity.asked.length, 0);
  const huge = await handler(
    step(token, {
      runId: RUN,
      messages: [{ role: "user", content: "x".repeat(600 * 1024) }],
    }),
  );
  assertEquals(await errorCode(huge), "too_large");
});

Deno.test("edit: an unverified step is refused and costs nothing", async () => {
  const { deps, handler, token } = await setup();
  deps.integrity.result = {
    ok: false,
    code: "device_not_verified",
    reason: "test",
  };
  const response = await handler(
    step(token, { runId: RUN, messages: CONVERSATION }),
  );
  assertEquals(await errorCode(response), "device_not_verified");
  assertEquals(deps.quota.runs.size, 0);
});

Deno.test("edit: a provider failure gives the step (and a new run) back", async () => {
  const { deps, handler, token } = await setup();
  deps.editAgent.error = new EditAgentError(
    "provider_failed",
    "529: overloaded",
  );
  const response = await handler(
    step(token, { runId: RUN, messages: CONVERSATION }),
  );
  assertEquals(response.status, 502);
  assertEquals(await errorCode(response), "provider_failed");
  assertEquals(deps.quota.runs.size, 0);
});

Deno.test("edit: without an Anthropic key it isn't set up, and nothing else changes", async () => {
  const { deps, token } = await setup();
  const handler = createHandler({ ...deps, editAgent: null });
  assertEquals(
    await errorCode(
      await handler(step(token, { runId: RUN, messages: CONVERSATION })),
    ),
    "not_configured",
  );
  const status = await handler(
    cloudRequest("GET", "/status", { headers: { "x-install-token": token } }),
  );
  assertEquals(status.status, 200);
});

Deno.test("edit: logs hold counts and costs, never the conversation", async () => {
  const { deps, handler, token } = await setup();
  await handler(step(token, { runId: RUN, messages: CONVERSATION }));
  const logged = JSON.stringify(deps.logs);
  assert(!logged.includes("Make it shorter"));
  assert(!logged.includes("Trimmed"));
  assert(!logged.includes(RUN));
  const entry = deps.logs.find((log) => log.event === "edit");
  assertEquals(entry?.costMicrousd, 6_580);
  assertEquals(entry?.outcome, "ok");
});
