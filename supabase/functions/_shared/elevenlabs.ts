import type { Bytes } from "./encoding.ts";
import {
  TranscribeError,
  type Transcriber,
  type Transcript,
  type TranscriptWord,
} from "./transcriber.ts";

/**
 * ElevenLabs Scribe v2 (POST /v1/speech-to-text, multipart): word timings, no speaker labels and
 * no sound tags. With [zeroRetention] it asks for zero retention (`enable_logging=false`), which
 * ElevenLabs offers to enterprise accounts only; otherwise switch off "Improve the models for
 * everyone" in the account so the audio isn't used for training.
 */
export class ElevenLabsTranscriber implements Transcriber {
  constructor(
    private readonly apiKey: string,
    private readonly zeroRetention: boolean = false,
    private readonly fetchFn: typeof fetch = fetch,
  ) {}

  async transcribe(
    audio: Bytes,
    mimeType: string,
    language: string | null,
  ): Promise<Transcript> {
    const form = new FormData();
    form.append("model_id", "scribe_v2");
    form.append(
      "file",
      new Blob([audio], { type: mimeType }),
      fileName(mimeType),
    );
    form.append("timestamps_granularity", "word");
    form.append("tag_audio_events", "false");
    form.append("diarize", "false");
    if (language) form.append("language_code", language);
    const url = this.zeroRetention
      ? `${ENDPOINT}?enable_logging=false`
      : ENDPOINT;
    let response: Response;
    try {
      response = await this.fetchFn(url, {
        method: "POST",
        headers: { "xi-api-key": this.apiKey },
        body: form,
      });
    } catch (e) {
      throw new TranscribeError(
        "provider_failed",
        `ElevenLabs unreachable: ${e}`,
      );
    }
    if (response.status === 400 || response.status === 422) {
      await response.body?.cancel();
      throw new TranscribeError(
        "bad_audio",
        `ElevenLabs refused the audio: HTTP ${response.status}`,
      );
    }
    if (!response.ok) {
      await response.body?.cancel();
      throw new TranscribeError(
        "provider_failed",
        `ElevenLabs: HTTP ${response.status}`,
      );
    }
    return scribeTranscript(await response.json());
  }
}

const ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text";

/**
 * Our transcript from Scribe's single-channel response: its words only (it also returns the
 * spacing between them and sound tags), in milliseconds. The duration is the audio's when Scribe
 * reports it, else the end of the last word.
 */
export function scribeTranscript(body: unknown): Transcript {
  const response = body as ScribeResponse | null;
  if (!Array.isArray(response?.words)) {
    throw new TranscribeError(
      "provider_failed",
      "ElevenLabs: no words in the response",
    );
  }
  const words: TranscriptWord[] = [];
  for (const word of response.words) {
    if (word?.type !== "word" || typeof word.text !== "string") continue;
    if (typeof word.start !== "number" || typeof word.end !== "number") {
      continue;
    }
    const text = word.text.trim();
    if (text === "") continue;
    const startMs = Math.round(word.start * 1000);
    words.push({
      text,
      startMs,
      endMs: Math.max(startMs, Math.round(word.end * 1000)),
    });
  }
  const reported = typeof response.audio_duration_secs === "number"
    ? Math.round(response.audio_duration_secs * 1000)
    : 0;
  return {
    language: typeof response.language_code === "string"
      ? response.language_code
      : null,
    durationMs: Math.max(reported, words.at(-1)?.endMs ?? 0),
    words,
  };
}

interface ScribeResponse {
  language_code?: string;
  audio_duration_secs?: number | null;
  words?: {
    text?: string;
    start?: number | null;
    end?: number | null;
    type?: string;
  }[];
}

/** A file name with the right extension: Scribe accepts "all major audio formats" and may go by it. */
function fileName(mimeType: string): string {
  if (mimeType.includes("wav")) return "audio.wav";
  if (mimeType.includes("mpeg")) return "audio.mp3";
  if (mimeType.includes("ogg")) return "audio.ogg";
  if (mimeType.includes("webm")) return "audio.webm";
  return "audio.m4a";
}
