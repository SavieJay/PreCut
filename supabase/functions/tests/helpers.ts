import {
  EditAgentError,
  type EditStep,
  ScriptedEditAgent,
} from "../_shared/edit_agent.ts";
import { utf8 } from "../_shared/encoding.ts";
import type { Deps } from "../_shared/handler.ts";
import type {
  IntegrityResult,
  IntegrityVerifier,
} from "../_shared/integrity.ts";
import type {
  AppConfig,
  CloudStatus,
  EditGrant,
  EditUsageRecord,
  QuotaStore,
  Reservation,
} from "../_shared/quota.ts";
import { FakeTranscriber, type Transcript } from "../_shared/transcriber.ts";

/** Fakes for handler tests. The real quota rules are the SQL's, tested in sql_test.ts. */

export const INSTALL = "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f";
export const SECRET = utf8("a-test-secret-that-is-long-enough-1234");
export const NOW = new Date("2026-10-22T10:00:00Z");

export class MemoryQuota implements QuotaStore {
  readonly installs = new Map<string, { used: number; reserved: number }>();
  freeSeconds = 1800;
  maxUploadSeconds = 600;
  freeEditRuns = 10;
  maxEditSteps = 8;
  /** AI edit runs: their install, steps taken and usage recorded. */
  readonly runs = new Map<
    string,
    { installId: string; steps: number; usage: EditUsageRecord[] }
  >();
  failWith: Error | null = null;

  register(installId: string): Promise<CloudStatus> {
    this.check();
    if (!this.installs.has(installId)) {
      this.installs.set(installId, { used: 0, reserved: 0 });
    }
    return Promise.resolve(this.statusOf(installId));
  }

  status(installId: string): Promise<CloudStatus | null> {
    this.check();
    return Promise.resolve(
      this.installs.has(installId) ? this.statusOf(installId) : null,
    );
  }

  reserve(installId: string, seconds: number): Promise<Reservation> {
    this.check();
    const install = this.installs.get(installId);
    if (!install) return Promise.resolve({ ok: false, code: "not_registered" });
    if (seconds > this.maxUploadSeconds) {
      return Promise.resolve({
        ok: false,
        code: "too_long",
        maxUploadSeconds: this.maxUploadSeconds,
      });
    }
    const left = this.freeSeconds - install.used - install.reserved;
    if (seconds > left) {
      return Promise.resolve({
        ok: false,
        code: "quota_used",
        secondsLeft: left,
        resetsAt: "2026-11-01T00:00:00+00:00",
      });
    }
    install.reserved += seconds;
    return Promise.resolve({ ok: true, month: "2026-10" });
  }

  settle(
    installId: string,
    _month: string,
    reserved: number,
    charged: number,
  ): Promise<CloudStatus | null> {
    this.check();
    const install = this.installs.get(installId);
    if (!install) return Promise.resolve(null);
    install.reserved -= reserved;
    install.used += charged;
    return Promise.resolve(this.statusOf(installId));
  }

  forget(installId: string): Promise<boolean> {
    this.check();
    return Promise.resolve(this.installs.delete(installId));
  }

  beginEdit(installId: string, runId: string): Promise<EditGrant> {
    this.check();
    if (!this.installs.has(installId)) {
      return Promise.resolve({ ok: false, code: "not_registered" });
    }
    const run = this.runs.get(runId);
    if (run) {
      if (run.installId !== installId) {
        return Promise.resolve({ ok: false, code: "bad_request" });
      }
      if (run.steps >= this.maxEditSteps) {
        return Promise.resolve({
          ok: false,
          code: "steps_used",
          maxEditSteps: this.maxEditSteps,
        });
      }
      run.steps++;
    } else {
      if (this.runsOf(installId) >= this.freeEditRuns) {
        return Promise.resolve({
          ok: false,
          code: "runs_used",
          editRunsLeft: 0,
          resetsAt: "2026-11-01T00:00:00+00:00",
        });
      }
      this.runs.set(runId, { installId, steps: 1, usage: [] });
    }
    const steps = this.runs.get(runId)?.steps ?? 0;
    return Promise.resolve({
      ok: true,
      month: "2026-10",
      stepsLeft: this.maxEditSteps - steps,
      editRunsLeft: this.freeEditRuns - this.runsOf(installId),
    });
  }

  settleEdit(runId: string, usage: EditUsageRecord): Promise<void> {
    this.check();
    this.runs.get(runId)?.usage.push(usage);
    return Promise.resolve();
  }

  releaseEdit(runId: string): Promise<void> {
    this.check();
    const run = this.runs.get(runId);
    if (run && --run.steps === 0) this.runs.delete(runId);
    return Promise.resolve();
  }

  /** The update rules [appConfig] answers with. */
  config: AppConfig = {
    minSupportedVersionCode: 0,
    latestVersionCode: 0,
    forceMessage: null,
    whatsNew: [],
    downloadUrl: null,
    updatedAt: null,
  };

  appConfig(_platform: string): Promise<AppConfig> {
    this.check();
    return Promise.resolve(this.config);
  }

  private runsOf(installId: string): number {
    return [...this.runs.values()].filter((run) => run.installId === installId)
      .length;
  }

  private statusOf(installId: string): CloudStatus {
    const install = this.installs.get(installId) ?? { used: 0, reserved: 0 };
    return {
      cloudEnabled: true,
      secondsLeft: this.freeSeconds - install.used - install.reserved,
      secondsPerMonth: this.freeSeconds,
      resetsAt: "2026-11-01T00:00:00+00:00",
      maxUploadSeconds: this.maxUploadSeconds,
      editEnabled: true,
      editRunsLeft: this.freeEditRuns - this.runsOf(installId),
      editRunsPerMonth: this.freeEditRuns,
      maxEditSteps: this.maxEditSteps,
    };
  }

  private check() {
    if (this.failWith) throw this.failWith;
  }
}

/** Accepts tokens whose hash matches, unless told to refuse or to fail; records what it was asked. */
export class ScriptedIntegrity implements IntegrityVerifier {
  readonly asked: { token: string; expectedHash: string }[] = [];
  result: IntegrityResult = { ok: true };
  error: Error | null = null;

  verify(token: string, expectedHash: string): Promise<IntegrityResult> {
    this.asked.push({ token, expectedHash });
    if (this.error) return Promise.reject(this.error);
    return Promise.resolve(this.result);
  }
}

export const TRANSCRIPT: Transcript = {
  language: "en",
  durationMs: 2_400,
  words: [{ text: "Hello", startMs: 100, endMs: 600 }, {
    text: "Lagos",
    startMs: 700,
    endMs: 1_300,
  }],
};

/** Records what it was asked to transcribe. */
export class RecordingTranscriber extends FakeTranscriber {
  readonly calls: {
    bytes: number;
    mimeType: string;
    language: string | null;
  }[] = [];
  error: Error | null = null;

  constructor(transcript: Transcript = TRANSCRIPT) {
    super(transcript);
  }

  override transcribe(
    audio?: Uint8Array,
    mimeType?: string,
    language?: string | null,
  ): Promise<Transcript> {
    this.calls.push({
      bytes: audio?.length ?? 0,
      mimeType: mimeType ?? "",
      language: language ?? null,
    });
    if (this.error) return Promise.reject(this.error);
    return super.transcribe();
  }
}

/** A step of Claude saying [text] (or calling [tools]), costing a little. */
export function stepReply(
  text: string,
  tools: { id: string; name: string; input: unknown }[] = [],
): EditStep {
  return {
    content: [
      { type: "text", text },
      ...tools.map((tool) => ({ type: "tool_use", ...tool })),
    ],
    stopReason: tools.length > 0 ? "tool_use" : "end_turn",
    usage: {
      inputTokens: 1_200,
      outputTokens: 80,
      cacheReadTokens: 900,
      cacheWriteTokens: 0,
      costMicrousd: 6_580,
    },
    model: "claude-opus-5-5",
  };
}

/** A scripted Claude that can be told to fail. */
export class FailingEditAgent extends ScriptedEditAgent {
  error: EditAgentError | null = null;

  override step(messages: unknown[]): Promise<EditStep> {
    if (this.error) {
      this.asked.push(messages);
      return Promise.reject(this.error);
    }
    return super.step(messages);
  }
}

export interface TestDeps extends Deps {
  quota: MemoryQuota;
  integrity: ScriptedIntegrity;
  transcriber: RecordingTranscriber;
  editAgent: FailingEditAgent;
  logs: Record<string, unknown>[];
  now: { value: Date };
}

export function testDeps(): TestDeps {
  const logs: Record<string, unknown>[] = [];
  const now = { value: NOW };
  return {
    quota: new MemoryQuota(),
    integrity: new ScriptedIntegrity(),
    transcriber: new RecordingTranscriber(),
    editAgent: new FailingEditAgent([stepReply("Trimmed the first clip.")]),
    installTokenSecret: SECRET,
    maxUploadBytes: 1024,
    minApiVersion: 1,
    clock: () => now.value,
    log: (entry) => logs.push(entry),
    logs,
    now,
  };
}

/** A request to the function as the platform delivers it locally ("/functions/v1/cloud/..."). */
export function cloudRequest(
  method: string,
  path: string,
  init: {
    headers?: Record<string, string>;
    body?: BodyInit;
    api?: string | null;
  } = {},
): Request {
  const headers = new Headers(init.headers);
  if (init.api !== null) headers.set("x-precut-api", init.api ?? "1");
  return new Request(`http://localhost:54321/functions/v1/cloud${path}`, {
    method,
    headers,
    body: init.body,
  });
}
