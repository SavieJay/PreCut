import { assertEquals, assertRejects } from "@std/assert";
import {
  ElevenLabsTranscriber,
  scribeTranscript,
} from "../_shared/elevenlabs.ts";
import { TranscribeError } from "../_shared/transcriber.ts";

// Shaped like the single-channel example in ElevenLabs' OpenAPI description of POST
// /v1/speech-to-text, with the spacing and sound-tag entries Scribe also returns.
const SCRIBE = JSON.parse(
  await Deno.readTextFile(
    new URL("./fixtures/scribe_response.json", import.meta.url),
  ),
);

Deno.test("Scribe's response becomes our words in milliseconds, without spacing or sound tags", () => {
  assertEquals(scribeTranscript(SCRIBE), {
    language: "en",
    durationMs: 2_000,
    words: [{ text: "Hello", startMs: 0, endMs: 500 }, {
      text: "world!",
      startMs: 600,
      endMs: 1_055,
    }],
  });
  // Without a reported duration, the last word's end stands in.
  assertEquals(
    scribeTranscript({ ...SCRIBE, audio_duration_secs: null }).durationMs,
    1_055,
  );
  // Silence: no words, still a transcript.
  assertEquals(
    scribeTranscript({
      language_code: "en",
      words: [],
      audio_duration_secs: 3,
    }),
    { language: "en", durationMs: 3_000, words: [] },
  );
});

Deno.test("a response with no word list is a provider failure", () => {
  try {
    scribeTranscript({ detail: "oops" });
    throw new Error("accepted");
  } catch (e) {
    assertEquals((e as TranscribeError).code, "provider_failed");
  }
});

Deno.test("the request: scribe_v2, word timings, no speakers or sound tags, the language when given", async () => {
  let sent: { url: string; key: string | null; form: FormData } | null = null;
  const fetchFn = ((input: string | URL | Request, init?: RequestInit) => {
    sent = {
      url: String(input),
      key: new Headers(init?.headers).get("xi-api-key"),
      form: init?.body as FormData,
    };
    return Promise.resolve(Response.json(SCRIBE));
  }) as typeof fetch;
  const transcript = await new ElevenLabsTranscriber("el-key", false, fetchFn)
    .transcribe(new Uint8Array([1, 2, 3]), "audio/wav", "yo");
  assertEquals(transcript.words.length, 2);
  const request = sent as unknown as {
    url: string;
    key: string | null;
    form: FormData;
  };
  assertEquals(request.url, "https://api.elevenlabs.io/v1/speech-to-text");
  assertEquals(request.key, "el-key");
  const form = request.form;
  assertEquals(
    [
      form.get("model_id"),
      form.get("timestamps_granularity"),
      form.get("tag_audio_events"),
      form.get("diarize"),
      form.get("language_code"),
    ],
    ["scribe_v2", "word", "false", "false", "yo"],
  );
  const file = form.get("file") as File;
  assertEquals([file.name, file.type, file.size], [
    "audio.wav",
    "audio/wav",
    3,
  ]);
});

Deno.test("zero retention adds enable_logging=false; no language leaves detection to Scribe", async () => {
  let url = "";
  let form = new FormData();
  const fetchFn = ((input: string | URL | Request, init?: RequestInit) => {
    url = String(input);
    form = init?.body as FormData;
    return Promise.resolve(Response.json(SCRIBE));
  }) as typeof fetch;
  await new ElevenLabsTranscriber("k", true, fetchFn).transcribe(
    new Uint8Array([1]),
    "audio/mp4",
    null,
  );
  assertEquals(
    url,
    "https://api.elevenlabs.io/v1/speech-to-text?enable_logging=false",
  );
  assertEquals(form.get("language_code"), null);
  assertEquals((form.get("file") as File).name, "audio.m4a");
});

Deno.test("refused audio and provider trouble map to their codes", async () => {
  const replying = (status: number) =>
    (() => Promise.resolve(new Response("{}", { status }))) as typeof fetch;
  const audio = new Uint8Array([1]);
  for (
    const [status, code] of [
      [422, "bad_audio"],
      [400, "bad_audio"],
      [401, "provider_failed"],
      [429, "provider_failed"],
      [500, "provider_failed"],
    ] as const
  ) {
    const error = await assertRejects(
      () =>
        new ElevenLabsTranscriber("k", false, replying(status)).transcribe(
          audio,
          "audio/mp4",
          null,
        ),
      TranscribeError,
    );
    assertEquals(error.code, code, `HTTP ${status}`);
  }
  const offline =
    (() => Promise.reject(new TypeError("offline"))) as typeof fetch;
  const error = await assertRejects(
    () =>
      new ElevenLabsTranscriber("k", false, offline).transcribe(
        audio,
        "audio/mp4",
        null,
      ),
    TranscribeError,
  );
  assertEquals(error.code, "provider_failed");
});
