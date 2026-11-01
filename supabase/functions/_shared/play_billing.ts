import * as jose from "jose";
import type { ServiceAccount } from "./integrity.ts";

/**
 * PreCut Pro (Phase 25): what Google Play says about a subscription purchase, from the Google Play
 * Developer API (`purchases.subscriptionsv2.get`). The app's word is never taken: a purchase token
 * the app sends, or one a Real-time developer notification names, is always looked up here.
 */

export type PurchaseState =
  | "active"
  | "grace"
  | "on_hold"
  | "paused"
  | "canceled"
  | "expired"
  | "pending";

export interface Subscription {
  productId: string;
  basePlanId: string | null;
  state: PurchaseState;
  /** The end of what's been paid for (ISO 8601); null while pending. */
  expiresAt: string | null;
  autoRenewing: boolean;
  /** In a free trial. */
  trial: boolean;
  acknowledged: boolean;
}

export interface PlayBilling {
  /** Google's record of [token]; null when Google doesn't know it (forged, or another app's). */
  subscription(token: string): Promise<Subscription | null>;
  /** Acknowledges [token] (unacknowledged purchases are refunded by Google after three days). */
  acknowledge(productId: string, token: string): Promise<void>;
}

/** Google couldn't be asked (network, outage, our credentials): worth trying again. */
export class BillingUnavailable extends Error {}

const STATES: Record<string, PurchaseState> = {
  SUBSCRIPTION_STATE_ACTIVE: "active",
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "grace",
  SUBSCRIPTION_STATE_ON_HOLD: "on_hold",
  SUBSCRIPTION_STATE_PAUSED: "paused",
  SUBSCRIPTION_STATE_CANCELED: "canceled",
  SUBSCRIPTION_STATE_EXPIRED: "expired",
  SUBSCRIPTION_STATE_PENDING: "pending",
  SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: "expired",
};

// deno-lint-ignore no-explicit-any
type Json = any;

/** A `SubscriptionPurchaseV2` as PreCut uses it; null when it has no line item. */
export function subscriptionFrom(body: Json): Subscription | null {
  const items: Json[] = Array.isArray(body?.lineItems) ? body.lineItems : [];
  if (items.length === 0 || typeof items[0]?.productId !== "string") {
    return null;
  }
  // One product, but a renewal or upgrade can list several items: the latest end wins.
  const latest = items.reduce((a, b) =>
    String(b?.expiryTime ?? "") > String(a?.expiryTime ?? "") ? b : a
  );
  return {
    productId: latest.productId,
    basePlanId: latest?.offerDetails?.basePlanId ?? null,
    state: STATES[body?.subscriptionState] ?? "expired",
    expiresAt: typeof latest?.expiryTime === "string"
      ? latest.expiryTime
      : null,
    autoRenewing: latest?.autoRenewingPlan?.autoRenewEnabled === true,
    trial: latest?.offerPhase?.freeTrial !== undefined,
    acknowledged:
      body?.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
  };
}

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3";

/**
 * The Google Play Developer API with PreCut's service account (the one Play Integrity uses), which
 * needs access in Play Console → Users and permissions: "View financial data" and "Manage orders
 * and subscriptions" for PreCut.
 */
export class GooglePlayBilling implements PlayBilling {
  private accessToken: { value: string; expiresAt: number } | null = null;

  constructor(
    private readonly account: ServiceAccount,
    private readonly packageName: string,
    private readonly fetchFn: typeof fetch = fetch,
    private readonly clock: () => number = Date.now,
  ) {}

  async subscription(token: string): Promise<Subscription | null> {
    const url =
      `${API}/applications/${this.packageName}/purchases/subscriptionsv2/tokens/${
        encodeURIComponent(token)
      }`;
    const response = await this.call(url, { method: "GET" });
    // Google answers an unknown, malformed or foreign token with 400, 404 or 410.
    if ([400, 404, 410].includes(response.status)) return null;
    if (!response.ok) {
      throw new BillingUnavailable(`subscriptionsv2: HTTP ${response.status}`);
    }
    return subscriptionFrom(await response.json());
  }

  async acknowledge(productId: string, token: string): Promise<void> {
    const url =
      `${API}/applications/${this.packageName}/purchases/subscriptions/${
        encodeURIComponent(productId)
      }/tokens/${encodeURIComponent(token)}:acknowledge`;
    const response = await this.call(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}",
    });
    await response.body?.cancel();
    if (!response.ok) {
      throw new BillingUnavailable(`acknowledge: HTTP ${response.status}`);
    }
  }

  private async call(
    url: string,
    init: { method: string; headers?: Record<string, string>; body?: string },
  ): Promise<Response> {
    const authorization = `Bearer ${await this.googleToken()}`;
    try {
      return await this.fetchFn(url, {
        ...init,
        headers: { ...init.headers, Authorization: authorization },
      });
    } catch (e) {
      throw new BillingUnavailable(`${url}: ${e}`);
    }
  }

  private async googleToken(): Promise<string> {
    const now = this.clock();
    if (this.accessToken && this.accessToken.expiresAt - 60_000 > now) {
      return this.accessToken.value;
    }
    const key = await jose.importPKCS8(this.account.privateKeyPem, "RS256");
    const issuedAt = Math.floor(now / 1000);
    const assertion = await new jose.SignJWT({ scope: SCOPE })
      .setProtectedHeader({
        alg: "RS256",
        typ: "JWT",
        ...(this.account.privateKeyId
          ? { kid: this.account.privateKeyId }
          : {}),
      })
      .setIssuer(this.account.clientEmail)
      .setAudience(TOKEN_URL)
      .setIssuedAt(issuedAt)
      .setExpirationTime(issuedAt + 3600)
      .sign(key);
    let response: Response;
    try {
      response = await this.fetchFn(TOKEN_URL, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
          assertion,
        }),
      });
    } catch (e) {
      throw new BillingUnavailable(`Google token: ${e}`);
    }
    const body = await response.json().catch(() => null);
    if (!response.ok || typeof body?.access_token !== "string") {
      throw new BillingUnavailable(`Google token: HTTP ${response.status}`);
    }
    this.accessToken = {
      value: body.access_token,
      expiresAt: now + Number(body.expires_in ?? 3600) * 1000,
    };
    return body.access_token;
  }
}

/** For a local stack and tests: subscriptions by token, as given. */
export class FakePlayBilling implements PlayBilling {
  readonly subscriptions = new Map<string, Subscription>();
  readonly acknowledged: string[] = [];
  error: Error | null = null;

  subscription(token: string): Promise<Subscription | null> {
    if (this.error) return Promise.reject(this.error);
    return Promise.resolve(this.subscriptions.get(token) ?? null);
  }

  acknowledge(_productId: string, token: string): Promise<void> {
    if (this.error) return Promise.reject(this.error);
    this.acknowledged.push(token);
    const sub = this.subscriptions.get(token);
    if (sub) this.subscriptions.set(token, { ...sub, acknowledged: true });
    return Promise.resolve();
  }
}
