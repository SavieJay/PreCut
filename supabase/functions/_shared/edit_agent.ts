import Anthropic from "@anthropic-ai/sdk";
import EDIT_TOOLS from "./edit_tools.json" with { type: "json" };

/**
 * Prompt-to-edit (Phase 22C) and AI Edit (Phase 27): one step of Claude working on a project. The
 * app holds the conversation and runs the tools itself, through its own editing rules (the server
 * can't run the app's Kotlin reducer): each step it sends the whole conversation, the server adds
 * the system prompt and the tool list, asks Claude, and hands back Claude's reply to append as it
 * is. The API key stays here; nothing about the conversation is stored, only token counts.
 */

export type Effort = "low" | "medium" | "high" | "xhigh" | "max";

/** Tokens and what they cost (millionths of a US dollar) for one step. */
export interface EditUsage {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  costMicrousd: number;
}

export interface EditStep {
  /** Claude's reply, ready to append to the conversation unchanged. */
  content: unknown[];
  /** "end_turn", "tool_use", "max_tokens", "refusal" or "pause_turn". */
  stopReason: string;
  usage: EditUsage;
  /** The model that answered (a fallback model when the requested one declined). */
  model: string;
}

/** Why a step failed: the app's conversation was malformed, or the provider failed (retryable). */
export class EditAgentError extends Error {
  constructor(
    readonly code: "bad_request" | "provider_failed",
    message: string,
  ) {
    super(message);
  }
}

export interface EditAgent {
  step(messages: unknown[]): Promise<EditStep>;
}

export const EDIT_TOOL_NAMES: readonly string[] = EDIT_TOOLS.map((tool) =>
  tool.name
);

export const SYSTEM_PROMPT =
  `You edit a video project in PreCut, an Android video editor, for the person using it.

Their first message holds a JSON brief of the project: the main-track clips in timeline order (ids, how long each plays, where it starts, its source length and the part of the source it keeps), overlays, texts, audio clips, the aspect ratio, the playhead and selection, and catalogs of the filters, effects, transitions, animations and text templates this version of the app has. Then comes what they want.

Make the changes with the tools. Each tool runs on their phone and its result says whether the change worked, any new ids, and the clips' updated timeline; later tool calls must use those. Use only ids from the brief or from tool results, and catalog ids exactly as listed. Times are in seconds.

Keep each tool call one clear change: the person reviews every change before anything is applied and may keep some and not others. If the request is unclear, or no tool can do it, say so in a sentence instead of guessing. When you are done, end with one or two short sentences saying what you changed, in the language the person wrote in.`;

/** US dollars per million tokens: input, output, cache read, cache write (5-minute). */
const PRICES: Record<string, [number, number, number, number]> = {
  "claude-opus-5-5": [4, 20, 0.2, 5],
  "claude-opus-5": [5, 25, 0.5, 6.25],
  "claude-opus-4-8": [5, 25, 0.5, 6.25],
  "claude-sonnet-5-5": [2, 10, 0.2, 2.5],
  "claude-haiku-5-5": [0.1, 0.5, 0.01, 0.125],
};

interface Tokens {
  input_tokens: number;
  output_tokens: number;
  cache_read_input_tokens?: number | null;
  cache_creation_input_tokens?: number | null;
}

/** Cost of [tokens] on [model] (the [fallback] model's prices when it isn't in the table). */
export function costMicrousd(
  model: string,
  tokens: Tokens,
  fallback: string,
): number {
  const [input, output, read, write] = PRICES[model] ?? PRICES[fallback] ??
    [0, 0, 0, 0];
  return Math.round(
    tokens.input_tokens * input + tokens.output_tokens * output +
      (tokens.cache_read_input_tokens ?? 0) * read +
      (tokens.cache_creation_input_tokens ?? 0) * write,
  );
}

/**
 * [content] as the app should append it: after a fallback (another model taking over when the
 * requested one declined), only text from before the last switch may be sent back, and the switch
 * markers themselves are dropped.
 */
export function echoable<T extends { type: string }>(content: T[]): T[] {
  let last = -1;
  content.forEach((block, i) => {
    if (block.type === "fallback") last = i;
  });
  return content.filter((block, i) =>
    block.type !== "fallback" && (i > last || block.type === "text")
  );
}

/** The usage of every model that ran (fallback attempts included), priced per model. */
export function usageOf(
  usage: Tokens & {
    iterations?: ({ type: string; model?: string | null } & Tokens)[] | null;
  },
  model: string,
  requested: string,
): EditUsage {
  const attempts = (usage.iterations ?? []).filter((it) =>
    it.type === "message" || it.type === "fallback_message"
  );
  const parts = attempts.length > 0
    ? attempts.map((it) => ({ tokens: it, model: it.model ?? model }))
    : [{ tokens: usage, model }];
  return parts.reduce<EditUsage>((sum, { tokens, model: m }) => ({
    inputTokens: sum.inputTokens + tokens.input_tokens,
    outputTokens: sum.outputTokens + tokens.output_tokens,
    cacheReadTokens: sum.cacheReadTokens +
      (tokens.cache_read_input_tokens ?? 0),
    cacheWriteTokens: sum.cacheWriteTokens +
      (tokens.cache_creation_input_tokens ?? 0),
    costMicrousd: sum.costMicrousd + costMicrousd(m, tokens, requested),
  }), {
    inputTokens: 0,
    outputTokens: 0,
    cacheReadTokens: 0,
    cacheWriteTokens: 0,
    costMicrousd: 0,
  });
}

/** Claude through Anthropic's API. */
export class AnthropicEditAgent implements EditAgent {
  private readonly client: Anthropic;

  constructor(
    apiKey: string,
    private readonly model: string,
    private readonly effort: Effort,
    fetchFn: typeof fetch = fetch,
    maxRetries = 2,
  ) {
    this.client = new Anthropic({
      apiKey,
      fetch: fetchFn,
      maxRetries,
      timeout: 120_000,
    });
  }

  async step(messages: unknown[]): Promise<EditStep> {
    let response: Anthropic.Beta.BetaMessage;
    try {
      response = await this.client.beta.messages.create({
        model: this.model,
        max_tokens: 16_000,
        system: SYSTEM_PROMPT,
        tools: EDIT_TOOLS as Anthropic.Beta.BetaTool[],
        messages: messages as Anthropic.Beta.BetaMessageParam[],
        output_config: { effort: this.effort },
        // The conversation grows by a step at a time: each step reads the last one's cache.
        cache_control: { type: "ephemeral" },
        // A policy decline is retried server-side on Anthropic's recommended fallback model.
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
      });
    } catch (e) {
      if (e instanceof Anthropic.BadRequestError) {
        throw new EditAgentError("bad_request", e.message);
      }
      if (e instanceof Anthropic.APIError) {
        throw new EditAgentError(
          "provider_failed",
          `${e.status ?? "network"}: ${e.message}`,
        );
      }
      throw e;
    }
    return {
      content: echoable(response.content),
      stopReason: response.stop_reason ?? "end_turn",
      usage: usageOf(response.usage, response.model, this.model),
      model: response.model,
    };
  }
}

/** For a local stack and tests: answers each step with the next reply it was given. */
export class ScriptedEditAgent implements EditAgent {
  readonly asked: unknown[][] = [];

  constructor(private readonly replies: EditStep[]) {}

  step(messages: unknown[]): Promise<EditStep> {
    this.asked.push(messages);
    const reply =
      this.replies[Math.min(this.asked.length - 1, this.replies.length - 1)];
    return Promise.resolve(reply);
  }
}
