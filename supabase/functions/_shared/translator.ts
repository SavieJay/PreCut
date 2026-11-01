import Anthropic from "@anthropic-ai/sdk";
import { type EditUsage, type Effort, usageOf } from "./edit_agent.ts";

/**
 * Caption translation (Phase 26B, Pro): caption lines in, the same lines in another language out,
 * by Claude with a JSON-schema answer. Nothing is stored; only token counts are kept.
 */

export interface CaptionLine {
  id: string;
  text: string;
}

export interface Translation {
  lines: CaptionLine[];
  usage: EditUsage;
}

export class TranslateError extends Error {
  constructor(
    readonly code: "bad_request" | "provider_failed",
    message: string,
  ) {
    super(message);
  }
}

export interface Translator {
  translate(lines: CaptionLine[], target: string): Promise<Translation>;
}

export const TRANSLATE_PROMPT =
  `You translate a video's captions for PreCut, an Android video editor. Each line is shown on screen while it's spoken, so keep every translation about as long as the original, natural and spoken in tone, and keep names, numbers and emoji. Translate each line on its own, keeping its id; don't merge, split, drop or add lines. If a line is already in the target language, return it unchanged.`;

const SCHEMA = {
  type: "object",
  properties: {
    lines: {
      type: "array",
      items: {
        type: "object",
        properties: { id: { type: "string" }, text: { type: "string" } },
        required: ["id", "text"],
        additionalProperties: false,
      },
    },
  },
  required: ["lines"],
  additionalProperties: false,
};

/** Claude through Anthropic's API, with the same model and effort settings as AI edits. */
export class AnthropicTranslator implements Translator {
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

  async translate(lines: CaptionLine[], target: string): Promise<Translation> {
    let response: Anthropic.Messages.Message;
    try {
      response = await this.client.messages.create({
        model: this.model,
        max_tokens: 16_000,
        system: TRANSLATE_PROMPT,
        messages: [{
          role: "user",
          content: `Translate into the language with code "${target}":\n${
            JSON.stringify({ lines })
          }`,
        }],
        output_config: {
          effort: this.effort,
          format: { type: "json_schema", schema: SCHEMA },
        },
      } as Anthropic.Messages.MessageCreateParamsNonStreaming);
    } catch (e) {
      if (e instanceof Anthropic.BadRequestError) {
        throw new TranslateError("bad_request", e.message);
      }
      if (e instanceof Anthropic.APIError) {
        throw new TranslateError(
          "provider_failed",
          `${e.status ?? "network"}: ${e.message}`,
        );
      }
      throw e;
    }
    const text = response.content.filter((b) => b.type === "text").map((b) =>
      (b as { text: string }).text
    ).join("");
    let parsed: { lines?: unknown };
    try {
      parsed = JSON.parse(text);
    } catch {
      throw new TranslateError(
        "provider_failed",
        `unreadable answer (${response.stop_reason})`,
      );
    }
    return {
      lines: matchedLines(lines, parsed.lines),
      usage: usageOf(response.usage, response.model, this.model),
    };
  }
}

/** Claude's lines matched to the asked ones by id, in their order; a line it missed keeps its text. */
export function matchedLines(
  asked: CaptionLine[],
  answer: unknown,
): CaptionLine[] {
  const given = new Map<string, string>();
  if (Array.isArray(answer)) {
    for (const line of answer) {
      if (typeof line?.id === "string" && typeof line?.text === "string") {
        given.set(line.id, line.text);
      }
    }
  }
  return asked.map((line) => ({
    id: line.id,
    text: given.get(line.id)?.trim() || line.text,
  }));
}

/** For a local stack and tests: every line tagged with the target. */
export class FakeTranslator implements Translator {
  readonly asked: { lines: CaptionLine[]; target: string }[] = [];
  error: Error | null = null;

  translate(lines: CaptionLine[], target: string): Promise<Translation> {
    this.asked.push({ lines, target });
    if (this.error) return Promise.reject(this.error);
    return Promise.resolve({
      lines: lines.map((l) => ({ id: l.id, text: `[${target}] ${l.text}` })),
      usage: {
        inputTokens: 100,
        outputTokens: 50,
        cacheReadTokens: 0,
        cacheWriteTokens: 0,
        costMicrousd: 1_400,
      },
    });
  }
}
