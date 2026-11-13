import {
  EditAgentError,
  type EditStep,
  ScriptedEditAgent,
} from "../_shared/edit_agent.ts";
import { utf8 } from "../_shared/encoding.ts";
import type { Deps } from "../_shared/handler.ts";
import type {
  Account,
  AccountStore,
  ConfirmResult,
  DeleteResult,
  GrantResult,
  MediaStorage,
  ProjectChanges,
  ProjectSave,
  SaveResult,
  User,
  UserAdmin,
  UserVerifier,
} from "../_shared/accounts.ts";
import type {
  IntegrityResult,
  IntegrityVerifier,
} from "../_shared/integrity.ts";
import type {
  AppConfig,
  CloudStatus,
  EditGrant,
  EditUsageRecord,
  Plan,
  QuotaStore,
  Release,
  Reservation,
} from "../_shared/quota.ts";
import { FakeTranscriber, type Transcript } from "../_shared/transcriber.ts";
import { FakePlayBilling, type Subscription } from "../_shared/play_billing.ts";
import { FakeTranslator } from "../_shared/translator.ts";

/** Fakes for handler tests. The real quota rules are the SQL's, tested in sql_test.ts. */

export const INSTALL = "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f";
export const SECRET = utf8("a-test-secret-that-is-long-enough-1234");
export const NOW = new Date("2026-10-22T10:00:00Z");
export const RTDN_SECRET = "a-push-endpoint-secret";
export const RELEASE_SECRET = "the-publish-workflow-secret";

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
    latestVersionName: null,
    forceMessage: null,
    whatsNew: [],
    downloadUrl: null,
    updatedAt: null,
  };

  appConfig(_platform: string): Promise<AppConfig> {
    this.check();
    return Promise.resolve(this.config);
  }

  publishRelease(_platform: string, release: Release): Promise<AppConfig> {
    this.check();
    if (this.config.latestVersionCode <= release.versionCode) {
      this.config = {
        ...this.config,
        latestVersionCode: release.versionCode,
        latestVersionName: release.versionName,
        whatsNew: release.whatsNew,
      };
    }
    return Promise.resolve(this.config);
  }

  /** Purchases by token hash, and the installs each is linked to. */
  readonly purchases = new Map<
    string,
    { token: string; subscription: Subscription; installs: Set<string> }
  >();

  savePurchase(
    installId: string | null,
    tokenHash: string,
    token: string,
    subscription: Subscription,
  ): Promise<{ ok: true } | { ok: false; code: "not_registered" }> {
    this.check();
    if (installId !== null && !this.installs.has(installId)) {
      return Promise.resolve({ ok: false, code: "not_registered" });
    }
    const saved = this.purchases.get(tokenHash) ??
      { token, subscription, installs: new Set<string>() };
    saved.subscription = subscription;
    if (installId !== null) saved.installs.add(installId);
    this.purchases.set(tokenHash, saved);
    return Promise.resolve({ ok: true });
  }

  purchaseKnown(tokenHash: string): Promise<boolean> {
    this.check();
    return Promise.resolve(this.purchases.has(tokenHash));
  }

  readonly templates = new Map<
    string,
    { installId: string; template: unknown; opens: number }
  >();
  templatesPerDay = 20;

  shareTemplate(
    installId: string,
    id: string,
    template: unknown,
  ): Promise<
    { ok: true } | { ok: false; code: "not_registered" | "templates_used" }
  > {
    this.check();
    if (!this.installs.has(installId)) {
      return Promise.resolve({ ok: false, code: "not_registered" });
    }
    if (
      [...this.templates.values()].filter((t) => t.installId === installId)
        .length >= this.templatesPerDay
    ) {
      return Promise.resolve({ ok: false, code: "templates_used" });
    }
    this.templates.set(id, { installId, template, opens: 0 });
    return Promise.resolve({ ok: true });
  }

  openTemplate(id: string): Promise<unknown | null> {
    this.check();
    const saved = this.templates.get(id);
    if (saved) saved.opens++;
    return Promise.resolve(saved?.template ?? null);
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
      pro: this.planOf(installId),
    };
  }

  /** Pro while a linked purchase is active, in grace or cancelled but paid up (as the SQL). */
  private planOf(installId: string): Plan {
    const linked = [...this.purchases.values()].filter((p) =>
      p.installs.has(installId)
    ).map((p) => p.subscription);
    const entitled = linked.find((sub) =>
      ["active", "grace", "canceled"].includes(sub.state) &&
      sub.expiresAt !== null && Date.parse(sub.expiresAt) > NOW.getTime()
    );
    const shown = entitled ?? linked[0];
    return shown
      ? {
        pro: entitled !== undefined,
        state: shown.state,
        expiresAt: shown.expiresAt,
      }
      : { pro: false, state: null };
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
  billing: FakePlayBilling;
  translator: FakeTranslator;
  users: FakeUsers;
  accounts: ScriptedAccounts;
  media: FakeMediaStorage;
  userAdmin: FakeUserAdmin;
  logs: Record<string, unknown>[];
  now: { value: Date };
}

/** Signed-in users by access token (the route tests' stand-in for JWKS checks). */
export class FakeUsers implements UserVerifier {
  tokens = new Map<string, User>([
    [ANA_TOKEN, { id: ANA, email: "ana@example.com" }],
  ]);
  verify(token: string): Promise<User | null> {
    return Promise.resolve(this.tokens.get(token) ?? null);
  }
}

export const ANA = "11111111-1111-4111-8111-111111111111";
export const ANA_TOKEN = "ana-access-token";

/**
 * Answers each account call with what a test set in [replies] (the SQL itself is tested in
 * PGlite), and records the calls.
 */
export class ScriptedAccounts implements AccountStore {
  calls: { name: string; args: unknown[] }[] = [];
  replies: Record<string, unknown> = {
    changes: { projects: [], next: null, more: false },
    save: { ok: true, revision: 1, updatedAt: "2026-11-13T10:00:00Z" },
    delete: { ok: true, revision: 2 },
    grant: { ok: true, exists: false },
    confirm: { ok: true },
    ready: true,
    account: {
      pro: false,
      storageUsed: 0,
      storageLimit: 2147483648,
      maxFileBytes: 4294967296,
      projects: 0,
    },
    linkInstall: true,
    files: [],
    cleanup: [],
  };
  // deno-lint-ignore no-explicit-any
  private reply(name: string, args: unknown[]): Promise<any> {
    this.calls.push({ name, args });
    return Promise.resolve(this.replies[name]);
  }
  changes(
    ...args: [string, string | null, string | null, number]
  ): Promise<ProjectChanges> {
    return this.reply("changes", args);
  }
  save(...args: [string, string, ProjectSave]): Promise<SaveResult> {
    return this.reply("save", args);
  }
  delete(...args: [string, string, number | null]): Promise<DeleteResult> {
    return this.reply("delete", args);
  }
  grant(...args: [string, string, number, string]): Promise<GrantResult> {
    return this.reply("grant", args);
  }
  confirm(...args: [string, string]): Promise<ConfirmResult> {
    return this.reply("confirm", args);
  }
  ready(...args: [string, string]): Promise<boolean> {
    return this.reply("ready", args);
  }
  account(...args: [string]): Promise<Account> {
    return this.reply("account", args);
  }
  linkInstall(...args: [string, string]): Promise<boolean> {
    return this.reply("linkInstall", args);
  }
  files(...args: [string]): Promise<string[]> {
    return this.reply("files", args);
  }
  cleanup(): Promise<{ userId: string; sha256: string }[]> {
    return this.reply("cleanup", []);
  }
}

export class FakeMediaStorage implements MediaStorage {
  readonly uploadUrl =
    "https://ref.storage.supabase.co/storage/v1/upload/resumable";
  removed: string[] = [];
  signedUrl(path: string, seconds: number): Promise<string | null> {
    return Promise.resolve(
      `https://ref.supabase.co/storage/v1/object/sign/media/${path}?t=${seconds}`,
    );
  }
  remove(paths: string[]): Promise<void> {
    this.removed.push(...paths);
    return Promise.resolve();
  }
}

export class FakeUserAdmin implements UserAdmin {
  deleted: string[] = [];
  deleteUser(id: string): Promise<void> {
    this.deleted.push(id);
    return Promise.resolve();
  }
}

export function testDeps(): TestDeps {
  const logs: Record<string, unknown>[] = [];
  const now = { value: NOW };
  return {
    quota: new MemoryQuota(),
    integrity: new ScriptedIntegrity(),
    transcriber: new RecordingTranscriber(),
    editAgent: new FailingEditAgent([stepReply("Trimmed the first clip.")]),
    billing: new FakePlayBilling(),
    translator: new FakeTranslator(),
    proProducts: ["precut_pro"],
    rtdnSecret: RTDN_SECRET,
    releaseSecret: RELEASE_SECRET,
    users: new FakeUsers(),
    accounts: new ScriptedAccounts(),
    media: new FakeMediaStorage(),
    userAdmin: new FakeUserAdmin(),
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
