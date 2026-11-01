import type { SupabaseClient } from "@supabase/supabase-js";

/**
 * Free minutes per install and the monthly budget, kept by the database functions in
 * supabase/migrations/ (which do the locking), in seconds of audio; and AI edit runs, steps and
 * their monthly cost (Phase 22C).
 */

export interface CloudStatus {
  /** False while cloud AI is paused or this month's budget is used up. */
  cloudEnabled: boolean;
  secondsLeft: number;
  secondsPerMonth: number;
  /** When the free minutes start again (ISO 8601). */
  resetsAt: string;
  maxUploadSeconds: number;
  /** False while AI edits are paused or this month's AI edit budget is used up. */
  editEnabled: boolean;
  editRunsLeft: number;
  editRunsPerMonth: number;
  maxEditSteps: number;
}

export type ReservationRefusal =
  | "paused"
  | "bad_audio"
  | "too_long"
  | "not_registered"
  | "quota_used"
  | "budget_reached";

export type Reservation =
  | { ok: true; month: string }
  | {
    ok: false;
    code: ReservationRefusal;
    secondsLeft?: number;
    resetsAt?: string;
    maxUploadSeconds?: number;
  };

export type EditRefusal =
  | "paused"
  | "not_registered"
  | "bad_request"
  | "runs_used"
  | "steps_used"
  | "budget_reached";

export type EditGrant =
  | { ok: true; month: string; stepsLeft: number; editRunsLeft: number }
  | {
    ok: false;
    code: EditRefusal;
    editRunsLeft?: number;
    maxEditSteps?: number;
    resetsAt?: string;
  };

/** What one AI edit step used: tokens, and their cost in millionths of a US dollar. */
export interface EditUsageRecord {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  costMicrousd: number;
}

/** Forced updates (Phase 23): the oldest app version allowed, the newest released, and what's new. */
export interface AppConfig {
  minSupportedVersionCode: number;
  latestVersionCode: number;
  forceMessage: string | null;
  whatsNew: string[];
  downloadUrl: string | null;
  updatedAt: string | null;
}

export interface QuotaStore {
  /** Adds the install (or touches it again). */
  register(installId: string): Promise<CloudStatus>;
  /** Null when the install isn't registered. */
  status(installId: string): Promise<CloudStatus | null>;
  /** Holds [seconds] for one request, if the install's minutes and the budget allow it. */
  reserve(installId: string, seconds: number): Promise<Reservation>;
  /** Ends a reservation from [reserve], charging [charged] seconds (0 when the request failed). */
  settle(
    installId: string,
    month: string,
    reserved: number,
    charged: number,
  ): Promise<CloudStatus | null>;
  /** Deletes what's kept about the install. True when there was something. */
  forget(installId: string): Promise<boolean>;
  /** Starts AI edit run [runId] (one of the install's runs) or takes its next step. */
  beginEdit(installId: string, runId: string): Promise<EditGrant>;
  /** Adds what a step used to its run and to the month's cost. */
  settleEdit(runId: string, usage: EditUsageRecord): Promise<void>;
  /** Gives back a step that failed at the provider (and the run, when it was the first step). */
  releaseEdit(runId: string): Promise<void>;
  /** The update rules for [platform] (zeros when there are none). */
  appConfig(platform: string): Promise<AppConfig>;
}

/** The database functions through PostgREST, with the project's secret key (they're granted to service_role only). */
export class PostgresQuotaStore implements QuotaStore {
  constructor(private readonly client: SupabaseClient) {}

  async register(installId: string): Promise<CloudStatus> {
    return await this.call("cloud_register", { p_install: installId });
  }

  async status(installId: string): Promise<CloudStatus | null> {
    return await this.call("cloud_status", { p_install: installId });
  }

  async reserve(installId: string, seconds: number): Promise<Reservation> {
    return await this.call("cloud_reserve", {
      p_install: installId,
      p_seconds: seconds,
    });
  }

  async settle(
    installId: string,
    month: string,
    reserved: number,
    charged: number,
  ): Promise<CloudStatus | null> {
    return await this.call("cloud_settle", {
      p_install: installId,
      p_month: month,
      p_reserved: reserved,
      p_charged: charged,
    });
  }

  async forget(installId: string): Promise<boolean> {
    return await this.call("cloud_forget", { p_install: installId });
  }

  async beginEdit(installId: string, runId: string): Promise<EditGrant> {
    return await this.call("cloud_edit_begin", {
      p_install: installId,
      p_run: runId,
    });
  }

  async settleEdit(runId: string, usage: EditUsageRecord): Promise<void> {
    await this.call("cloud_edit_settle", {
      p_run: runId,
      p_input: usage.inputTokens,
      p_output: usage.outputTokens,
      p_cache_read: usage.cacheReadTokens,
      p_cache_write: usage.cacheWriteTokens,
      p_cost: usage.costMicrousd,
    });
  }

  async releaseEdit(runId: string): Promise<void> {
    await this.call("cloud_edit_release", { p_run: runId });
  }
  async appConfig(platform: string): Promise<AppConfig> {
    return await this.call("cloud_app_config", { p_platform: platform });
  }

  private async call<T>(
    name: string,
    args: Record<string, unknown>,
  ): Promise<T> {
    const { data, error } = await this.client.rpc(name, args);
    if (error) throw new Error(`${name}: ${error.message}`);
    return data as T;
  }
}
