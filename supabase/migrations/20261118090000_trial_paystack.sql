-- PreCut Pro trial and Paystack (Phase 31). Every account has Pro for `trial_days` from sign-up
-- (accounts made before `trial_since` count from then); after that Pro needs a subscription,
-- through Google Play (Play builds) or Paystack (the website APK). An account's plan is its best
-- source, and every install signed in to it gets the same answer from cloud_pro, so the quotas and
-- older apps follow it too.

alter table public.cloud_config
  add column trial_days integer not null default 14 check (trial_days >= 0),
  -- Accounts made before this get their days from here (the trial's launch).
  add column trial_since timestamptz not null default now(),
  -- Which Paystack keys the function has (PAYSTACK_MODE): test payments never count in live.
  add column paystack_mode text not null default 'test' check (paystack_mode in ('test', 'live'));

create table public.cloud_paystack (
  user_id uuid primary key references auth.users (id) on delete cascade,
  -- Which Paystack keys it belongs to: test payments never make a live account Pro.
  mode text not null check (mode in ('test', 'live')),
  plan text check (plan in ('monthly', 'yearly')),
  customer_code text,
  subscription_code text,
  -- Paystack needs it to cancel the subscription.
  email_token text,
  -- Paystack's subscription status: active, non-renewing, attention, completed, cancelled.
  status text,
  -- The end of what's paid for (Paystack's next payment date).
  paid_until timestamptz,
  -- A payment page opened and not yet confirmed.
  pending_reference text,
  checked_at timestamptz not null default now()
);

alter table public.cloud_paystack enable row level security;
revoke all on table public.cloud_paystack from anon, authenticated;

-- What used to be cloud_pro (Phase 30A): the best Google Play purchase of the install and of
-- every install signed in to the same account.
create function public.cloud_play_plan(p_install uuid, p_now timestamptz default now())
returns jsonb
language sql
stable
set search_path = ''
as $$
  select coalesce(
    (select jsonb_build_object(
        'pro', p.state in ('active', 'grace', 'canceled') and coalesce(p.expires_at > p_now, false),
        'state', p.state,
        'productId', p.product_id,
        'basePlanId', p.base_plan_id,
        'expiresAt', p.expires_at,
        'autoRenewing', p.auto_renewing,
        'trial', p.trial,
        'source', 'play')
      from public.cloud_install_purchases l
      join public.cloud_purchases p on p.token_hash = l.token_hash
      where l.install_id = p_install
        or l.install_id in (
          select o.id from public.cloud_installs o
          where o.user_id is not null
            and o.user_id = (select i.user_id from public.cloud_installs i where i.id = p_install))
      order by (p.state in ('active', 'grace', 'canceled') and coalesce(p.expires_at > p_now, false)) desc,
        p.expires_at desc nulls last, p.updated_at desc
      limit 1),
    jsonb_build_object('pro', false, 'state', null)
  );
$$;

-- The account's Paystack subscription as a plan; null without one (or one in the other mode).
create function public.cloud_paystack_plan(p_user uuid, p_mode text, p_now timestamptz default now())
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
      -- Paid until its date; a failed renewal (attention) keeps 3 days while Paystack retries.
      'pro', s.status in ('active', 'non-renewing', 'attention')
        and coalesce(s.paid_until + case when s.status = 'attention' then interval '3 days' else interval '0' end > p_now, false),
      'state', case s.status
        when 'active' then 'active'
        when 'non-renewing' then 'canceled'
        when 'attention' then 'grace'
        else 'expired' end,
      'productId', 'precut_pro',
      'basePlanId', s.plan,
      'expiresAt', s.paid_until,
      'autoRenewing', s.status = 'active',
      'trial', false,
      'source', 'paystack')
  from public.cloud_paystack s
  where s.user_id = p_user and s.mode = p_mode and s.status is not null;
$$;

-- The account's trial as a plan: Pro while it runs, then an expired plan that says it was a trial.
create function public.cloud_trial_plan(p_user uuid, p_now timestamptz default now())
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
      'pro', p_now >= greatest(u.created_at, c.trial_since)
        and p_now < greatest(u.created_at, c.trial_since) + make_interval(days => c.trial_days),
      'state', case when p_now >= greatest(u.created_at, c.trial_since)
          and p_now < greatest(u.created_at, c.trial_since) + make_interval(days => c.trial_days)
        then 'active' else 'expired' end,
      'productId', 'precut_pro',
      'basePlanId', null,
      'expiresAt', greatest(u.created_at, c.trial_since) + make_interval(days => c.trial_days),
      'autoRenewing', false,
      'trial', true,
      'source', 'trial')
  from auth.users u, public.cloud_config c
  where u.id = p_user;
$$;

-- An account's plan: the first that makes it Pro of Play, Paystack and the trial; otherwise the
-- most telling one for messages (a lapsed subscription before an ended trial).
create function public.cloud_user_plan(
  p_user uuid,
  p_install uuid,
  p_mode text,
  p_now timestamptz default now()
) returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  play jsonb := case when p_install is null then null else public.cloud_play_plan(p_install, p_now) end;
  paystack jsonb := public.cloud_paystack_plan(p_user, p_mode, p_now);
  trial jsonb := public.cloud_trial_plan(p_user, p_now);
begin
  if (play ->> 'pro')::boolean then return play; end if;
  if (paystack ->> 'pro')::boolean then return paystack; end if;
  if (trial ->> 'pro')::boolean then return trial; end if;
  if play is not null and play ->> 'state' is not null then return play; end if;
  return coalesce(paystack, trial, jsonb_build_object('pro', false, 'state', null));
end;
$$;

-- Which Paystack keys are in use (cloud_config.paystack_mode, kept equal to the function's PAYSTACK_MODE).
create function public.cloud_paystack_mode()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select c.paystack_mode from public.cloud_config c;
$$;

-- An install's plan: its account's when signed in, else its own Play purchases (Phase 25).
create or replace function public.cloud_pro(p_install uuid, p_now timestamptz default now())
returns jsonb
language sql
stable
set search_path = ''
as $$
  select case
    when i.user_id is not null then public.cloud_user_plan(i.user_id, p_install, public.cloud_paystack_mode(), p_now)
    else public.cloud_play_plan(p_install, p_now)
  end
  from (select (select o.user_id from public.cloud_installs o where o.id = p_install) as user_id) i;
$$;

create function public.cloud_is_play_pro(p_install uuid, p_now timestamptz default now())
returns boolean
language sql
stable
set search_path = ''
as $$
  select coalesce((public.cloud_play_plan(p_install, p_now) ->> 'pro')::boolean, false);
$$;

-- Whether an account is Pro (storage limits, GET /account).
create or replace function public.cloud_account_is_pro(p_user uuid, p_now timestamptz default now())
returns boolean
language sql
stable
set search_path = ''
as $$
  select coalesce((public.cloud_user_plan(p_user,
    (select i.id from public.cloud_installs i where i.user_id = p_user
      order by public.cloud_is_play_pro(i.id, p_now) desc limit 1),
    public.cloud_paystack_mode(), p_now) ->> 'pro')::boolean, false);
$$;


-- The account's plan for GET /account and the Paystack routes.
create function public.cloud_account_plan(p_user uuid, p_mode text, p_now timestamptz default now())
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select public.cloud_user_plan(p_user,
    (select i.id from public.cloud_installs i where i.user_id = p_user
      order by public.cloud_is_play_pro(i.id, p_now) desc limit 1),
    p_mode, p_now);
$$;

-- The account's Paystack row (the function decides from it whether to ask Paystack again).
create function public.cloud_paystack_get(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select to_jsonb(s) - 'user_id' from public.cloud_paystack s where s.user_id = p_user;
$$;

-- Saves what Paystack says (or a payment page just opened); fields left null keep their value.
create function public.cloud_paystack_save(
  p_user uuid,
  p_mode text,
  p_plan text,
  p_customer text,
  p_subscription text,
  p_email_token text,
  p_status text,
  p_paid_until timestamptz,
  p_pending_reference text,
  p_now timestamptz default now()
) returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.cloud_paystack as s
    (user_id, mode, plan, customer_code, subscription_code, email_token, status, paid_until, pending_reference, checked_at)
  values (p_user, p_mode, p_plan, p_customer, p_subscription, p_email_token, p_status, p_paid_until, p_pending_reference, p_now)
  on conflict (user_id) do update set
    mode = excluded.mode,
    plan = coalesce(excluded.plan, s.plan),
    customer_code = coalesce(excluded.customer_code, s.customer_code),
    subscription_code = coalesce(excluded.subscription_code, s.subscription_code),
    email_token = coalesce(excluded.email_token, s.email_token),
    status = coalesce(excluded.status, s.status),
    paid_until = coalesce(excluded.paid_until, s.paid_until),
    pending_reference = excluded.pending_reference,
    checked_at = excluded.checked_at;
$$;

-- Accounts with a Paystack subscription due a check (for the daily clean-up).
create function public.cloud_paystack_due(p_now timestamptz default now())
returns uuid[]
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(array_agg(s.user_id), '{}') from public.cloud_paystack s
  where s.status in ('active', 'non-renewing', 'attention')
    and (s.checked_at < p_now - interval '6 hours' or s.paid_until < p_now);
$$;

-- The account an install is signed in to (null when none), so /status can refresh its Paystack plan.
create function public.cloud_install_user(p_install uuid)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
  select i.user_id from public.cloud_installs i where i.id = p_install;
$$;

do $$
declare
  f text;
begin
  foreach f in array array[
    'public.cloud_play_plan(uuid, timestamptz)',
    'public.cloud_paystack_plan(uuid, text, timestamptz)',
    'public.cloud_trial_plan(uuid, timestamptz)',
    'public.cloud_user_plan(uuid, uuid, text, timestamptz)',
    'public.cloud_paystack_mode()',
    'public.cloud_is_play_pro(uuid, timestamptz)',
    'public.cloud_account_plan(uuid, text, timestamptz)',
    'public.cloud_paystack_get(uuid)',
    'public.cloud_paystack_save(uuid, text, text, text, text, text, text, timestamptz, text, timestamptz)',
    'public.cloud_paystack_due(timestamptz)',
    'public.cloud_install_user(uuid)'
  ] loop
    execute format('revoke all on function %s from public, anon, authenticated', f);
    execute format('grant execute on function %s to service_role', f);
  end loop;
end;
$$;
