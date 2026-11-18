/**
 * Paystack (Phase 31): PreCut Pro for the website APK, in naira. The account's single webhook
 * serves the owner's other products, so nothing here relies on one: payments are checked with
 * `GET /transaction/verify` when the app comes back from the payment page, and subscriptions with
 * `GET /subscription/:code` when the saved answer is over 6 hours old or past its renewal date.
 */

export type PaystackPlanName = "monthly" | "yearly";

export interface PaystackTransaction {
  status: string;
  /** The customer's id (Paystack lists subscriptions by it). */
  customerId: string | null;
  planCode: string | null;
  /** Our metadata: the PreCut account that opened the payment page, and the plan. */
  userId: string | null;
  paidAt: string | null;
}

export interface PaystackSubscription {
  code: string;
  emailToken: string | null;
  /** active, non-renewing, attention, completed or cancelled. */
  status: string;
  nextPaymentDate: string | null;
  planCode: string | null;
}

export interface PaystackApi {
  /** A payment page for [planCode] (Paystack takes the plan's amount); its address. */
  initialize(
    email: string,
    planCode: string,
    reference: string,
    callbackUrl: string,
    metadata: Record<string, string>,
  ): Promise<string>;
  verify(reference: string): Promise<PaystackTransaction | null>;
  subscriptionsOf(customerId: string): Promise<PaystackSubscription[]>;
  subscription(code: string): Promise<PaystackSubscription | null>;
  disable(code: string, emailToken: string): Promise<boolean>;
}

export class PaystackUnavailable extends Error {}

/** The plans and keys in use; [mode] tells test payments from live ones. */
export interface PaystackSetup {
  api: PaystackApi;
  mode: "test" | "live";
  plans: Record<PaystackPlanName, string>;
  /** Where Paystack's page sends the buyer when the payment is done (a website page). */
  callbackUrl: string;
}

export function planNameOf(
  setup: PaystackSetup,
  code: string | null,
): PaystackPlanName | null {
  if (code === setup.plans.monthly) return "monthly";
  if (code === setup.plans.yearly) return "yearly";
  return null;
}

/** Paystack's REST API with the secret key. Network or 5xx failures throw [PaystackUnavailable]. */
export class HttpPaystack implements PaystackApi {
  constructor(
    private readonly secretKey: string,
    private readonly fetchFn: typeof fetch = fetch,
    private readonly base = "https://api.paystack.co",
  ) {}

  async initialize(
    email: string,
    planCode: string,
    reference: string,
    callbackUrl: string,
    metadata: Record<string, string>,
  ) {
    const data = await this.call("POST", "/transaction/initialize", {
      email,
      // Required by the API; a plan's own amount replaces it.
      amount: 100,
      plan: planCode,
      reference,
      callback_url: callbackUrl,
      metadata,
    });
    const url = (data as { authorization_url?: unknown } | null)
      ?.authorization_url;
    if (typeof url !== "string") {
      throw new PaystackUnavailable("no authorization_url");
    }
    return url;
  }

  async verify(reference: string): Promise<PaystackTransaction | null> {
    const data = await this.call(
      "GET",
      `/transaction/verify/${encodeURIComponent(reference)}`,
    ) as Record<string, unknown> | null;
    if (!data) return null;
    const customer = data.customer as { id?: unknown } | undefined;
    const planObject = data.plan_object as { plan_code?: unknown } | undefined;
    const meta = typeof data.metadata === "string"
      ? safeJson(data.metadata)
      : data.metadata as Record<string, unknown> | null;
    return {
      status: String(data.status ?? ""),
      customerId: customer?.id != null ? String(customer.id) : null,
      planCode: typeof planObject?.plan_code === "string"
        ? planObject.plan_code
        : typeof data.plan === "string"
        ? data.plan
        : null,
      userId: typeof meta?.precut_user === "string" ? meta.precut_user : null,
      paidAt: typeof data.paid_at === "string" ? data.paid_at : null,
    };
  }

  async subscriptionsOf(customerId: string): Promise<PaystackSubscription[]> {
    const data = await this.call(
      "GET",
      `/subscription?customer=${encodeURIComponent(customerId)}&perPage=100`,
    );
    return Array.isArray(data)
      ? data.map(subscriptionFrom).filter((s): s is PaystackSubscription =>
        s !== null
      )
      : [];
  }

  async subscription(code: string): Promise<PaystackSubscription | null> {
    return subscriptionFrom(
      await this.call("GET", `/subscription/${encodeURIComponent(code)}`),
    );
  }

  async disable(code: string, emailToken: string): Promise<boolean> {
    try {
      await this.call("POST", "/subscription/disable", {
        code,
        token: emailToken,
      });
      return true;
    } catch (e) {
      if (e instanceof PaystackUnavailable) throw e;
      return false;
    }
  }

  /** The response's `data`; null for a 404; throws on other refusals and outages. */
  private async call(
    method: string,
    path: string,
    body?: unknown,
  ): Promise<unknown> {
    let response: Response;
    try {
      response = await this.fetchFn(this.base + path, {
        method,
        headers: {
          authorization: `Bearer ${this.secretKey}`,
          "content-type": "application/json",
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (e) {
      throw new PaystackUnavailable(e instanceof Error ? e.message : String(e));
    }
    const json = await response.json().catch(() => null) as {
      status?: boolean;
      data?: unknown;
      message?: string;
    } | null;
    if (response.status === 404) return null;
    if (response.status >= 500 || json === null) {
      throw new PaystackUnavailable(`paystack ${response.status}`);
    }
    if (!response.ok || json.status !== true) {
      throw new Error(`paystack ${response.status}: ${json.message ?? ""}`);
    }
    return json.data ?? null;
  }
}

function subscriptionFrom(raw: unknown): PaystackSubscription | null {
  if (typeof raw !== "object" || raw === null) return null;
  const s = raw as Record<string, unknown>;
  if (typeof s.subscription_code !== "string") return null;
  const plan = s.plan as { plan_code?: unknown } | undefined;
  return {
    code: s.subscription_code,
    emailToken: typeof s.email_token === "string" ? s.email_token : null,
    status: String(s.status ?? ""),
    nextPaymentDate: typeof s.next_payment_date === "string"
      ? s.next_payment_date
      : null,
    planCode: typeof plan?.plan_code === "string" ? plan.plan_code : null,
  };
}

function safeJson(text: string): Record<string, unknown> | null {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** A recorded Paystack for tests: transactions, subscriptions, and what was asked. */
export class FakePaystack implements PaystackApi {
  transactions = new Map<string, PaystackTransaction>();
  subscriptions = new Map<
    string,
    PaystackSubscription & { customerId: string }
  >();
  initialized: {
    email: string;
    planCode: string;
    reference: string;
    metadata: Record<string, string>;
  }[] = [];
  disabled: string[] = [];
  down = false;

  initialize(
    email: string,
    planCode: string,
    reference: string,
    _callbackUrl: string,
    metadata: Record<string, string>,
  ) {
    if (this.down) return Promise.reject(new PaystackUnavailable("down"));
    this.initialized.push({ email, planCode, reference, metadata });
    return Promise.resolve(`https://checkout.paystack.com/${reference}`);
  }
  verify(reference: string) {
    if (this.down) return Promise.reject(new PaystackUnavailable("down"));
    return Promise.resolve(this.transactions.get(reference) ?? null);
  }
  subscriptionsOf(customerId: string) {
    return Promise.resolve(
      [...this.subscriptions.values()].filter((s) =>
        s.customerId === customerId
      ),
    );
  }
  subscription(code: string) {
    if (this.down) return Promise.reject(new PaystackUnavailable("down"));
    return Promise.resolve(this.subscriptions.get(code) ?? null);
  }
  disable(code: string, _token: string) {
    this.disabled.push(code);
    const s = this.subscriptions.get(code);
    if (s) this.subscriptions.set(code, { ...s, status: "non-renewing" });
    return Promise.resolve(s !== undefined);
  }
}
