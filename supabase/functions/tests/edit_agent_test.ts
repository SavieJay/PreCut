import { assert, assertEquals, assertRejects } from "@std/assert";
import {
  AnthropicEditAgent,
  costMicrousd,
  echoable,
  EDIT_TOOL_NAMES,
  EditAgentError,
  usageOf,
} from "../_shared/edit_agent.ts";

const MESSAGES = [{ role: "user", content: "Make it shorter" }];

/** A fetch that records the request and answers with [status] and [body]. */
function fakeFetch(status: number, body: unknown) {
  const calls: {
    url: string;
    headers: Headers;
    body: Record<string, unknown>;
  }[] = [];
  const fn = (input: string | URL | Request, init?: RequestInit) => {
    const request = new Request(input, init);
    return request.json().then((json) => {
      calls.push({ url: request.url, headers: request.headers, body: json });
      return new Response(JSON.stringify(body), {
        status,
        headers: { "content-type": "application/json", "request-id": "req_1" },
      });
    });
  };
  return { fn: fn as typeof fetch, calls };
}

const MESSAGE = {
  id: "msg_1",
  type: "message",
  role: "assistant",
  model: "claude-opus-5-5",
  stop_reason: "tool_use",
  stop_sequence: null,
  content: [
    { type: "thinking", thinking: "", signature: "sig" },
    {
      type: "tool_use",
      id: "toolu_1",
      name: "trim_clip",
      input: { clip_id: "a", keep_from_seconds: 0, keep_to_seconds: 5 },
    },
  ],
  usage: {
    input_tokens: 1_000,
    output_tokens: 100,
    cache_read_input_tokens: 2_000,
    cache_creation_input_tokens: 0,
  },
};

Deno.test("the request: the model, every tool, the effort, caching and the default fallback", async () => {
  const { fn, calls } = fakeFetch(200, MESSAGE);
  const agent = new AnthropicEditAgent(
    "sk-test",
    "claude-opus-5-5",
    "medium",
    fn,
    0,
  );
  const step = await agent.step(MESSAGES);
  const { headers, body } = calls[0];
  assertEquals(body.model, "claude-opus-5-5");
  assertEquals(
    (body.tools as { name: string; strict: boolean }[]).map((t) => t.name),
    [...EDIT_TOOL_NAMES],
  );
  // Anthropic refuses strict tools with over 16 nullable parameters or a grammar too large to
  // compile (both seen live), so the four tools with the most optional values aren't strict.
  assertEquals(
    (body.tools as { name: string; strict: boolean }[])
      .filter((t) => !t.strict).map((t) => t.name),
    [
      "set_clip_sound",
      "set_clip_look",
      "set_clip_animation",
      "set_audio_track",
    ],
  );
  assertEquals(body.output_config, { effort: "medium" });
  assertEquals(body.cache_control, { type: "ephemeral" });
  assertEquals(body.fallbacks, "default");
  assertEquals(body.messages, MESSAGES);
  assert(
    headers.get("anthropic-beta")?.includes("server-side-fallback-2026-07-01"),
  );
  assertEquals(headers.get("x-api-key"), "sk-test");
  // Claude's reply comes back whole (thinking blocks included) for the app to append unchanged.
  assertEquals(step.content, MESSAGE.content);
  assertEquals(step.stopReason, "tool_use");
  // 1,000 × $4 + 100 × $20 + 2,000 × $0.20 per million tokens.
  assertEquals(step.usage.costMicrousd, 6_400);
});

Deno.test("a bad conversation is the app's fault; an outage or overload is retryable", async () => {
  const badRequest = new AnthropicEditAgent(
    "sk-test",
    "claude-opus-5-5",
    "medium",
    fakeFetch(400, {
      type: "error",
      error: { type: "invalid_request_error", message: "bad" },
    }).fn,
    0,
  );
  const refused = await assertRejects(
    () => badRequest.step(MESSAGES),
    EditAgentError,
  );
  assertEquals(refused.code, "bad_request");
  const overloaded = new AnthropicEditAgent(
    "sk-test",
    "claude-opus-5-5",
    "medium",
    fakeFetch(529, {
      type: "error",
      error: { type: "overloaded_error", message: "busy" },
    }).fn,
    0,
  );
  const failed = await assertRejects(
    () => overloaded.step(MESSAGES),
    EditAgentError,
  );
  assertEquals(failed.code, "provider_failed");
});

Deno.test("after a fallback, only text from before the switch goes back; markers are dropped", () => {
  const content = [
    { type: "thinking", thinking: "" },
    { type: "text", text: "Starting" },
    { type: "fallback", from: { model: "a" }, to: { model: "b" } },
    { type: "thinking", thinking: "" },
    { type: "tool_use", id: "t" },
  ];
  assertEquals(echoable(content), [{ type: "text", text: "Starting" }, {
    type: "thinking",
    thinking: "",
  }, { type: "tool_use", id: "t" }]);
  const plain = [{ type: "text", text: "Hi" }];
  assertEquals(echoable(plain), plain);
});

Deno.test("usage: every model that ran is priced at its own rate; unknown models at the requested one's", () => {
  const tokens = {
    input_tokens: 1_000_000,
    output_tokens: 0,
    cache_read_input_tokens: 0,
    cache_creation_input_tokens: 0,
  };
  assertEquals(
    costMicrousd("claude-opus-5-5", tokens, "claude-opus-5-5"),
    4_000_000,
  );
  assertEquals(
    costMicrousd("claude-future", tokens, "claude-haiku-5-5"),
    100_000,
  );
  const usage = usageOf(
    {
      input_tokens: 10,
      output_tokens: 10,
      iterations: [
        {
          type: "message",
          model: "claude-opus-5-5",
          input_tokens: 1_000,
          output_tokens: 0,
        },
        {
          type: "fallback_message",
          model: "claude-opus-4-8",
          input_tokens: 1_000,
          output_tokens: 100,
        },
        { type: "compaction", input_tokens: 99_999, output_tokens: 0 },
      ],
    },
    "claude-opus-4-8",
    "claude-opus-5-5",
  );
  assertEquals(usage.inputTokens, 2_000);
  assertEquals(usage.costMicrousd, 4_000 + 5_000 + 2_500);
});
