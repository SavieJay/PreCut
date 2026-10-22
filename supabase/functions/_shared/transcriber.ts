import type { Bytes } from "./encoding.ts";

/**
 * Speech to text behind PreCut's own format, so the app never depends on a provider's response
 * and the provider can change without an app update.
 */

export interface TranscriptWord {
  text: string;
  startMs: number;
  endMs: number;
}

export interface Transcript {
  /** The language the provider heard (its own code, e.g. "en" or "eng"); null when it didn't say. */
  language: string | null;
  durationMs: number;
  words: TranscriptWord[];
}

export interface Transcriber {
  /** [language] is a 2- or 3-letter code, or null to let the provider detect it. */
  transcribe(
    audio: Bytes,
    mimeType: string,
    language: string | null,
  ): Promise<Transcript>;
}

/** The provider refused the audio ([code] "bad_audio") or failed ("provider_failed", worth retrying). */
export class TranscribeError extends Error {
  constructor(readonly code: "bad_audio" | "provider_failed", message: string) {
    super(message);
  }
}

/** Returns [transcript] for any audio: tests, and a local stack with no provider key (see config.ts). */
export class FakeTranscriber implements Transcriber {
  constructor(private readonly transcript: Transcript) {}

  transcribe(): Promise<Transcript> {
    return Promise.resolve(this.transcript);
  }
}
