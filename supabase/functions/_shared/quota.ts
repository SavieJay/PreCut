import type { Subscription } from "./play_billing.ts";
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
  /** The install's plan (Phase 25); absent from stores that predate it. */
  pro?: Plan;
}

/** PreCut Pro for an install: whether it's Pro now, and its newest purchase's state for messages. */
export interface Plan {
  pro: boolean;
  state: string | null;
  productId?: string;
  basePlanId?: string | null;
  expiresAt?: string | null;
  autoRenewing?: boolean;
  trial?: boolean;
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
  /**
   * Saves Google's record of a purchase (keyed by [tokenHash]) and, with [installId], links it to
   * that install. Refused for an unregistered install.
   */
  savePurchase(
    installId: string | null,
    tokenHash: string,
    token: string,
    subscription: Subscription,
  ): Promise<{ ok: true } | { ok: false; code: "not_registered" }>;
  /** Whether a purchase was ever sent by an app (notifications for others are ignored). */
  purchaseKnown(tokenHash: string): Promise<boolean>;
  /** Stores a shared template (Phase 28) under [id], within the install's daily limit. */
  shareTemplate(
    installId: string,
    id: string,
    template: unknown,
  ): Promise<
    { ok: true } | { ok: false; code: "not_registered" | "templates_used" }
  >;
  /** The shared template [id], or null. */
  openTemplate(id: string): Promise<unknown | null>;
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
  async savePurchase(
    installId: string | null,
    tokenHash: string,
    token: string,
    sub: Subscription,
  ): Promise<{ ok: true } | { ok: false; code: "not_registered" }> {
    return await this.call("cloud_save_purchase", {
      p_install: installId,
      p_token_hash: tokenHash,
      p_token: token,
      p_product: sub.productId,
      p_base_plan: sub.basePlanId,
      p_state: sub.state,
      p_expires: sub.expiresAt,
      p_auto_renewing: sub.autoRenewing,
      p_trial: sub.trial,
    });
  }

  async purchaseKnown(tokenHash: string): Promise<boolean> {
    return await this.call("cloud_purchase_known", {
      p_token_hash: tokenHash,
    });
  }
  async shareTemplate(
    installId: string,
    id: string,
    template: unknown,
  ): Promise<
    { ok: true } | { ok: false; code: "not_registered" | "templates_used" }
  > {
    return await this.call("cloud_share_template", {
      p_install: installId,
      p_id: id,
      p_template: template,
    });
  }

  async openTemplate(id: string): Promise<unknown | null> {
    return await this.call("cloud_open_template", { p_id: id });
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
