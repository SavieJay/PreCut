import type { PaystackRow, User } from "./accounts.ts";
import { signedIn } from "./account_routes.ts";
import { type Deps, errorResponse, json, shortHash } from "./handler.ts";
import {
  type PaystackPlanName,
  type PaystackSetup,
  type PaystackSubscription,
  PaystackUnavailable,
  planNameOf,
} from "./paystack.ts";

/**
 * PreCut Pro through Paystack (Phase 31, the website APK; docs/CLOUD_API.md). Every route needs
 * the signed-in user. The app opens the page from /paystack/checkout in the browser and, back in
 * PreCut, calls /paystack/confirm; renewals and cancellations are read from Paystack when the
 * saved answer is old ([refreshPaystack]).
 */

const STALE_MS = 6 * 60 * 60 * 1000;
const LIVE = new Set(["active", "non-renewing", "attention"]);

export function paystackRoute(
  method: string,
  path: string,
  request: Request,
  deps: Deps,
): Promise<Response> | null {
  const key = `${method} ${path}`;
  if (key === "POST /paystack/checkout") {
    return withPaystack(request, deps, (u, p) => checkout(request, u, p, deps));
  }
  if (key === "POST /paystack/confirm") {
    return withPaystack(request, deps, (u, p) => confirm(request, u, p, deps));
  }
  if (key === "POST /paystack/cancel") {
    return withPaystack(request, deps, (u, p) => cancel(u, p, deps));
  }
  return null;
}

function withPaystack(
  request: Request,
  deps: Deps,
  run: (user: User, paystack: PaystackSetup) => Promise<Response>,
) {
  return signedIn(request, deps, async (user) => {
    if (!deps.paystack) return errorResponse("not_configured");
    try {
      return await run(user, deps.paystack);
    } catch (e) {
      if (!(e instanceof PaystackUnavailable)) throw e;
      deps.log({
        event: "paystack",
        outcome: "unavailable",
        reason: e.message,
      });
      return errorResponse("billing_unavailable");
    }
  });
}

/** A Paystack payment page for the monthly or yearly plan, for the account's email. */
async function checkout(
  request: Request,
  user: User,
  paystack: PaystackSetup,
  deps: Deps,
): Promise<Response> {
  const body = await request.json().catch(() => null) as
    | { plan?: unknown }
    | null;
  const plan = body?.plan;
  if (plan !== "monthly" && plan !== "yearly") {
    return errorResponse("bad_request");
  }
  if (!user.email) return errorResponse("bad_request");
  const reference = `precut_${crypto.randomUUID().replaceAll("-", "")}`;
  const url = await paystack.api.initialize(
    user.email,
    paystack.plans[plan],
    reference,
    paystack.callbackUrl,
    {
      precut_user: user.id,
      precut_plan: plan,
    },
  );
  await deps.accounts.savePaystack(user.id, {
    mode: paystack.mode,
    plan,
    customer_code: null,
    subscription_code: null,
    email_token: null,
    status: null,
    paid_until: null,
    pending_reference: reference,
  });
  deps.log({
    event: "paystack_checkout",
    user: await shortHash(user.id),
    plan,
    outcome: "ok",
  });
  return json(200, { url, reference });
}

/**
 * Back from the payment page: Paystack's own record of [reference] decides. Only a successful
 * payment for one of PreCut's plans, made by this account, makes it Pro.
 */
async function confirm(
  request: Request,
  user: User,
  paystack: PaystackSetup,
  deps: Deps,
): Promise<Response> {
  const body = await request.json().catch(() => null) as
    | { reference?: unknown }
    | null;
  const reference = body?.reference;
  if (
    typeof reference !== "string" || !/^precut_[0-9a-f]{32}$/.test(reference)
  ) return errorResponse("bad_request");
  const paid = await paystack.api.verify(reference);
  const plan = planNameOf(paystack, paid?.planCode ?? null);
  if (
    !paid || paid.status !== "success" || paid.userId !== user.id || !plan ||
    !paid.customerId
  ) {
    deps.log({
      event: "paystack_confirm",
      user: await shortHash(user.id),
      outcome: paid?.status ?? "unknown",
    });
    return json(200, {
      paid: false,
      plan: await deps.accounts.plan(user.id, paystack.mode),
    });
  }
  // Paystack makes the subscription right after the first payment; until it shows, the payment
  // itself covers one period.
  const subscription = newest(
    (await paystack.api.subscriptionsOf(paid.customerId)).filter((s) =>
      s.planCode === paystack.plans[plan]
    ),
  );
  await deps.accounts.savePaystack(user.id, {
    mode: paystack.mode,
    plan,
    customer_code: paid.customerId,
    subscription_code: subscription?.code ?? null,
    email_token: subscription?.emailToken ?? null,
    status: subscription?.status ?? "active",
    paid_until: subscription?.nextPaymentDate ??
      periodAfter(paid.paidAt ?? new Date(deps.clock()).toISOString(), plan),
    pending_reference: null,
  });
  deps.log({
    event: "paystack_confirm",
    user: await shortHash(user.id),
    plan,
    outcome: "paid",
  });
  return json(200, {
    paid: true,
    plan: await deps.accounts.plan(user.id, paystack.mode),
  });
}

/** Stops renewing; Pro lasts until the paid period ends. */
async function cancel(
  user: User,
  paystack: PaystackSetup,
  deps: Deps,
): Promise<Response> {
  let row = await deps.accounts.paystack(user.id);
  if (row && !row.subscription_code) {
    await refreshPaystack(user.id, deps, true);
    row = await deps.accounts.paystack(user.id);
  }
  if (!row?.subscription_code || !LIVE.has(row.status ?? "")) {
    return errorResponse("not_found");
  }
  const token = row.email_token ??
    (await paystack.api.subscription(row.subscription_code))?.emailToken;
  if (!token || !(await paystack.api.disable(row.subscription_code, token))) {
    return errorResponse("billing_unavailable");
  }
  await refreshPaystack(user.id, deps, true);
  deps.log({
    event: "paystack_cancel",
    user: await shortHash(user.id),
    outcome: "ok",
  });
  return json(200, { plan: await deps.accounts.plan(user.id, paystack.mode) });
}

/**
 * Reads the account's subscription from Paystack again when [force]d, when the saved answer is
 * over 6 hours old, or when its paid period has passed (a renewal). Paystack being down keeps the
 * saved answer.
 */
export async function refreshPaystack(
  userId: string,
  deps: Deps,
  force = false,
): Promise<void> {
  const paystack = deps.paystack;
  if (!paystack) return;
  const row = await deps.accounts.paystack(userId);
  if (!row || row.mode !== paystack.mode || !row.customer_code) return;
  if (!force && !due(row, deps.clock())) return;
  try {
    const subscription = row.subscription_code
      ? await paystack.api.subscription(row.subscription_code)
      : newest(
        (await paystack.api.subscriptionsOf(row.customer_code)).filter((s) =>
          planNameOf(paystack, s.planCode) !== null
        ),
      );
    await deps.accounts.savePaystack(userId, {
      mode: row.mode,
      plan: planNameOf(paystack, subscription?.planCode ?? null) ?? row.plan,
      customer_code: row.customer_code,
      subscription_code: subscription?.code ?? null,
      email_token: subscription?.emailToken ?? null,
      status: subscription?.status ?? null,
      paid_until: subscription?.nextPaymentDate ?? null,
      pending_reference: row.pending_reference,
    });
  } catch (e) {
    if (!(e instanceof PaystackUnavailable)) throw e;
    deps.log({ event: "paystack_refresh", outcome: "unavailable" });
  }
}

function due(row: PaystackRow, now: Date): boolean {
  if (!LIVE.has(row.status ?? "")) return false;
  if (now.getTime() - Date.parse(row.checked_at) > STALE_MS) return true;
  return row.paid_until !== null && Date.parse(row.paid_until) < now.getTime();
}

function newest(
  subscriptions: PaystackSubscription[],
): PaystackSubscription | null {
  const live = subscriptions.filter((s) => LIVE.has(s.status));
  const pool = live.length > 0 ? live : subscriptions;
  return pool.sort((a, b) =>
    Date.parse(b.nextPaymentDate ?? "0") - Date.parse(a.nextPaymentDate ?? "0")
  )[0] ?? null;
}

function periodAfter(iso: string, plan: PaystackPlanName): string {
  const date = new Date(iso);
  if (plan === "monthly") date.setUTCMonth(date.getUTCMonth() + 1);
  else date.setUTCFullYear(date.getUTCFullYear() + 1);
  return date.toISOString();
}
