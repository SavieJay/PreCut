import type { SupabaseClient } from "@supabase/supabase-js";

/**
 * Free minutes per install and the monthly budget, kept by the database functions in
 * supabase/migrations/*_cloud.sql (which do the locking). All in seconds of audio.
 */

export interface CloudStatus {
  /** False while cloud AI is paused or this month's budget is used up. */
  cloudEnabled: boolean;
  secondsLeft: number;
  secondsPerMonth: number;
  /** When the free minutes start again (ISO 8601). */
  resetsAt: string;
  maxUploadSeconds: number;
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

  private async call<T>(
    name: string,
    args: Record<string, unknown>,
  ): Promise<T> {
    const { data, error } = await this.client.rpc(name, args);
    if (error) throw new Error(`${name}: ${error.message}`);
    return data as T;
  }
}
