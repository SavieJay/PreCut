-- PreCut Pro (Phase 25): Google Play subscriptions, verified here, never trusted from the app.
-- The cloud function checks each purchase token with the Google Play Developer API and saves what
-- Google says; Real-time developer notifications only prompt a fresh check. A purchase can belong
-- to several installs (the same Google account on another phone, or after reinstalling: Restore
-- purchases), so installs link to purchases. Pro installs get the Pro quotas. Like the rest of the
-- quota SQL, only service_role (the cloud function) can run these.

alter table public.cloud_config
  add column pro_seconds_per_month integer not null default 18000 check (pro_seconds_per_month >= 0),
  add column pro_edit_runs_per_month integer not null default 100 check (pro_edit_runs_per_month >= 0);

create table public.cloud_purchases (
  -- sha256 (hex) of the purchase token: the key notifications and links use.
  token_hash text primary key,
  purchase_token text not null,
  product_id text not null,
  base_plan_id text,
  state text not null check (state in ('active', 'grace', 'on_hold', 'paused', 'canceled', 'expired', 'pending', 'revoked')),
  expires_at timestamptz,
  auto_renewing boolean not null default false,
  trial boolean not null default false,
  updated_at timestamptz not null
);

create table public.cloud_install_purchases (
  install_id uuid not null references public.cloud_installs (id) on delete cascade,
  token_hash text not null references public.cloud_purchases (token_hash) on delete cascade,
  linked_at timestamptz not null,
  primary key (install_id, token_hash)
);

create index cloud_install_purchases_token on public.cloud_install_purchases (token_hash);

alter table public.cloud_purchases enable row level security;
alter table public.cloud_install_purchases enable row level security;
revoke all on table public.cloud_purchases, public.cloud_install_purchases from anon, authenticated;

-- The install's plan: Pro while one of its purchases is active, in its grace period, or cancelled
-- but not yet at the end of what was paid for. Otherwise the most recent purchase says why not
-- (on hold, paused, expired), so the app can explain.
create function public.cloud_pro(p_install uuid, p_now timestamptz default now())
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
        'trial', p.trial)
      from public.cloud_install_purchases l
      join public.cloud_purchases p on p.token_hash = l.token_hash
      where l.install_id = p_install
      order by (p.state in ('active', 'grace', 'canceled') and coalesce(p.expires_at > p_now, false)) desc,
        p.expires_at desc nulls last, p.updated_at desc
      limit 1),
    jsonb_build_object('pro', false, 'state', null)
  );
$$;

create function public.cloud_is_pro(p_install uuid, p_now timestamptz default now())
returns boolean
language sql
stable
set search_path = ''
as $$
  select (public.cloud_pro(p_install, p_now) ->> 'pro')::boolean;
$$;

-- Saves what Google says about a purchase and, with [p_install], links it to that install.
-- Returns the install's plan (or, without an install, null). An unregistered install gets nothing.
create function public.cloud_save_purchase(
  p_install uuid,
  p_token_hash text,
  p_token text,
  p_product text,
  p_base_plan text,
  p_state text,
  p_expires timestamptz,
  p_auto_renewing boolean,
  p_trial boolean,
  p_now timestamptz default now()
)
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
begin
  if p_install is not null and not exists (select 1 from public.cloud_installs where id = p_install) then
    return jsonb_build_object('ok', false, 'code', 'not_registered');
  end if;
  insert into public.cloud_purchases as p
    (token_hash, purchase_token, product_id, base_plan_id, state, expires_at, auto_renewing, trial, updated_at)
  values (p_token_hash, p_token, p_product, p_base_plan, p_state, p_expires, p_auto_renewing, p_trial, p_now)
  on conflict (token_hash) do update set
    product_id = excluded.product_id,
    base_plan_id = excluded.base_plan_id,
    state = excluded.state,
    expires_at = excluded.expires_at,
    auto_renewing = excluded.auto_renewing,
    trial = excluded.trial,
    updated_at = excluded.updated_at;
  if p_install is null then
    return jsonb_build_object('ok', true);
  end if;
  insert into public.cloud_install_purchases (install_id, token_hash, linked_at)
  values (p_install, p_token_hash, p_now)
  on conflict do nothing;
  return jsonb_build_object('ok', true, 'plan', public.cloud_pro(p_install, p_now));
end;
$$;

-- Whether a purchase is known (a notification for one never seen is ignored until an app sends it).
create function public.cloud_purchase_known(p_token_hash text)
returns boolean
language sql
stable
set search_path = ''
as $$
  select exists (select 1 from public.cloud_purchases where token_hash = p_token_hash);
$$;

-- "Delete my cloud data" now also drops purchases no other install links to.
create or replace function public.cloud_forget(p_install uuid)
returns boolean
language plpgsql
volatile
set search_path = ''
as $$
declare
  existed boolean;
begin
  delete from public.cloud_installs where id = p_install;
  existed := found;
  delete from public.cloud_purchases p
  where not exists (select 1 from public.cloud_install_purchases l where l.token_hash = p.token_hash);
  return existed;
end;
$$;

-- Quotas per plan: the free ones, or the Pro ones while Pro.
create or replace function public.cloud_status(p_install uuid, p_now timestamptz default now())
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
declare
  cfg public.cloud_config;
  ins public.cloud_installs;
  mon public.cloud_months;
  cur text := public.cloud_month(p_now);
  used integer;
  runs integer;
  plan jsonb := public.cloud_pro(p_install, p_now);
  pro boolean := (plan ->> 'pro')::boolean;
  seconds integer;
  edit_runs integer;
begin
  select * into cfg from public.cloud_config where id;
  select * into ins from public.cloud_installs where id = p_install;
  if not found then
    return null;
  end if;
  used := case when ins.month = cur then ins.used_seconds + ins.reserved_seconds else 0 end;
  runs := case when ins.edit_month = cur then ins.edit_runs else 0 end;
  select * into mon from public.cloud_months where month = cur;
  seconds := case when pro then cfg.pro_seconds_per_month else cfg.free_seconds_per_month end;
  edit_runs := case when pro then cfg.pro_edit_runs_per_month else cfg.free_edit_runs_per_month end;
  return jsonb_build_object(
    'cloudEnabled', not cfg.paused
      and coalesce(mon.used_seconds + mon.reserved_seconds, 0) < cfg.budget_seconds_per_month,
    'secondsLeft', greatest(seconds - used, 0),
    'secondsPerMonth', seconds,
    'resetsAt', public.cloud_next_month(p_now),
    'maxUploadSeconds', cfg.max_seconds_per_upload,
    'editEnabled', not cfg.edit_paused
      and coalesce(mon.edit_cost_microusd, 0) < cfg.edit_budget_microusd_per_month,
    'editRunsLeft', greatest(edit_runs - runs, 0),
    'editRunsPerMonth', edit_runs,
    'maxEditSteps', cfg.max_edit_steps,
    'pro', plan
  );
end;
$$;

create or replace function public.cloud_reserve(p_install uuid, p_seconds integer, p_now timestamptz default now())
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
declare
  cfg public.cloud_config;
  ins public.cloud_installs;
  mon public.cloud_months;
  cur text := public.cloud_month(p_now);
  allowed integer;
begin
  select * into cfg from public.cloud_config where id;
  if cfg.paused then
    return jsonb_build_object('ok', false, 'code', 'paused');
  end if;
  if p_seconds is null or p_seconds <= 0 then
    return jsonb_build_object('ok', false, 'code', 'bad_audio');
  end if;
  if p_seconds > cfg.max_seconds_per_upload then
    return jsonb_build_object('ok', false, 'code', 'too_long', 'maxUploadSeconds', cfg.max_seconds_per_upload);
  end if;

  select * into ins from public.cloud_installs where id = p_install for update;
  if not found then
    return jsonb_build_object('ok', false, 'code', 'not_registered');
  end if;
  if ins.month <> cur then
    update public.cloud_installs set month = cur, used_seconds = 0, reserved_seconds = 0
    where id = p_install returning * into ins;
  end if;
  allowed := case when public.cloud_is_pro(p_install, p_now) then cfg.pro_seconds_per_month else cfg.free_seconds_per_month end;
  if ins.used_seconds + ins.reserved_seconds + p_seconds > allowed then
    return jsonb_build_object(
      'ok', false, 'code', 'quota_used',
      'secondsLeft', greatest(allowed - ins.used_seconds - ins.reserved_seconds, 0),
      'resetsAt', public.cloud_next_month(p_now)
    );
  end if;

  insert into public.cloud_months (month) values (cur) on conflict (month) do nothing;
  select * into mon from public.cloud_months where month = cur for update;
  if mon.used_seconds + mon.reserved_seconds + p_seconds > cfg.budget_seconds_per_month then
    return jsonb_build_object('ok', false, 'code', 'budget_reached', 'resetsAt', public.cloud_next_month(p_now));
  end if;

  update public.cloud_installs
  set reserved_seconds = reserved_seconds + p_seconds, last_used_at = p_now
  where id = p_install;
  update public.cloud_months set reserved_seconds = reserved_seconds + p_seconds where month = cur;
  return jsonb_build_object('ok', true, 'month', cur);
end;
$$;

create or replace function public.cloud_edit_begin(p_install uuid, p_run uuid, p_now timestamptz default now())
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
declare
  cfg public.cloud_config;
  ins public.cloud_installs;
  mon public.cloud_months;
  run public.cloud_edit_runs;
  cur text := public.cloud_month(p_now);
  allowed integer;
begin
  select * into cfg from public.cloud_config where id;
  if cfg.edit_paused then
    return jsonb_build_object('ok', false, 'code', 'paused');
  end if;
  select * into ins from public.cloud_installs where id = p_install for update;
  if not found then
    return jsonb_build_object('ok', false, 'code', 'not_registered');
  end if;
  if ins.edit_month is distinct from cur then
    update public.cloud_installs set edit_month = cur, edit_runs = 0
    where id = p_install returning * into ins;
  end if;
  allowed := case when public.cloud_is_pro(p_install, p_now) then cfg.pro_edit_runs_per_month else cfg.free_edit_runs_per_month end;
  select * into run from public.cloud_edit_runs where id = p_run for update;
  if found then
    if run.install_id <> p_install then
      return jsonb_build_object('ok', false, 'code', 'bad_request');
    end if;
    if run.steps >= cfg.max_edit_steps then
      return jsonb_build_object('ok', false, 'code', 'steps_used', 'maxEditSteps', cfg.max_edit_steps);
    end if;
    update public.cloud_edit_runs set steps = steps + 1, updated_at = p_now where id = p_run returning * into run;
  else
    if ins.edit_runs >= allowed then
      return jsonb_build_object(
        'ok', false, 'code', 'runs_used',
        'editRunsLeft', 0,
        'resetsAt', public.cloud_next_month(p_now)
      );
    end if;
    insert into public.cloud_months (month) values (cur) on conflict (month) do nothing;
    select * into mon from public.cloud_months where month = cur for update;
    if mon.edit_cost_microusd >= cfg.edit_budget_microusd_per_month then
      return jsonb_build_object('ok', false, 'code', 'budget_reached', 'resetsAt', public.cloud_next_month(p_now));
    end if;
    insert into public.cloud_edit_runs (id, install_id, month, created_at, updated_at, steps)
    values (p_run, p_install, cur, p_now, p_now, 1) returning * into run;
    update public.cloud_installs set edit_runs = edit_runs + 1, last_used_at = p_now where id = p_install;
    update public.cloud_months set edit_runs = edit_runs + 1 where month = cur;
    ins.edit_runs := ins.edit_runs + 1;
  end if;
  return jsonb_build_object(
    'ok', true,
    'month', run.month,
    'stepsLeft', greatest(cfg.max_edit_steps - run.steps, 0),
    'editRunsLeft', greatest(allowed - ins.edit_runs, 0)
  );
end;
$$;

revoke execute on function
  public.cloud_pro(uuid, timestamptz),
  public.cloud_is_pro(uuid, timestamptz),
  public.cloud_save_purchase(uuid, text, text, text, text, text, timestamptz, boolean, boolean, timestamptz),
  public.cloud_purchase_known(text)
from public, anon, authenticated;

grant execute on function
  public.cloud_pro(uuid, timestamptz),
  public.cloud_is_pro(uuid, timestamptz),
  public.cloud_save_purchase(uuid, text, text, text, text, text, timestamptz, boolean, boolean, timestamptz),
  public.cloud_purchase_known(text)
to service_role;
