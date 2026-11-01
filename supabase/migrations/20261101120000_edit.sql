-- AI edits (Phase 22C prompt-to-edit; Phase 27 AI Edit uses the same runs). A run is one request
-- worked on by Claude over a few steps; each install has some free runs a month, every run a
-- limit of steps, and everyone together a monthly budget in US dollars (millionths). Only token
-- counts and costs are kept, never what was asked or answered. Like the rest of the quota SQL,
-- only service_role (the cloud function) can run these.

alter table public.cloud_config
  add column free_edit_runs_per_month integer not null default 10 check (free_edit_runs_per_month >= 0),
  add column max_edit_steps integer not null default 8 check (max_edit_steps > 0),
  add column edit_budget_microusd_per_month bigint not null default 20000000 check (edit_budget_microusd_per_month >= 0),
  add column edit_paused boolean not null default false;

alter table public.cloud_installs
  add column edit_month text,
  add column edit_runs integer not null default 0 check (edit_runs >= 0);

alter table public.cloud_months
  add column edit_runs integer not null default 0 check (edit_runs >= 0),
  add column edit_cost_microusd bigint not null default 0 check (edit_cost_microusd >= 0);

create table public.cloud_edit_runs (
  id uuid primary key,
  install_id uuid not null references public.cloud_installs (id) on delete cascade,
  month text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  steps integer not null default 0 check (steps >= 0),
  input_tokens bigint not null default 0,
  output_tokens bigint not null default 0,
  cache_read_tokens bigint not null default 0,
  cache_write_tokens bigint not null default 0,
  cost_microusd bigint not null default 0
);

create index cloud_edit_runs_install_id on public.cloud_edit_runs (install_id);

alter table public.cloud_edit_runs enable row level security;
revoke all on table public.cloud_edit_runs from anon, authenticated;

-- The status now also says how many AI edit runs are left this month.
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
begin
  select * into cfg from public.cloud_config where id;
  select * into ins from public.cloud_installs where id = p_install;
  if not found then
    return null;
  end if;
  used := case when ins.month = cur then ins.used_seconds + ins.reserved_seconds else 0 end;
  runs := case when ins.edit_month = cur then ins.edit_runs else 0 end;
  select * into mon from public.cloud_months where month = cur;
  return jsonb_build_object(
    'cloudEnabled', not cfg.paused
      and coalesce(mon.used_seconds + mon.reserved_seconds, 0) < cfg.budget_seconds_per_month,
    'secondsLeft', greatest(cfg.free_seconds_per_month - used, 0),
    'secondsPerMonth', cfg.free_seconds_per_month,
    'resetsAt', public.cloud_next_month(p_now),
    'maxUploadSeconds', cfg.max_seconds_per_upload,
    'editEnabled', not cfg.edit_paused
      and coalesce(mon.edit_cost_microusd, 0) < cfg.edit_budget_microusd_per_month,
    'editRunsLeft', greatest(cfg.free_edit_runs_per_month - runs, 0),
    'editRunsPerMonth', cfg.free_edit_runs_per_month,
    'maxEditSteps', cfg.max_edit_steps
  );
end;
$$;

-- Starts run p_run (the first step, using one of the install's runs) or takes its next step.
create function public.cloud_edit_begin(p_install uuid, p_run uuid, p_now timestamptz default now())
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
    if ins.edit_runs >= cfg.free_edit_runs_per_month then
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
    'editRunsLeft', greatest(cfg.free_edit_runs_per_month - ins.edit_runs, 0)
  );
end;
$$;

-- What a step used, added to its run and to its month's cost.
create function public.cloud_edit_settle(
  p_run uuid,
  p_input bigint,
  p_output bigint,
  p_cache_read bigint,
  p_cache_write bigint,
  p_cost bigint,
  p_now timestamptz default now()
)
returns void
language plpgsql
volatile
set search_path = ''
as $$
declare
  run public.cloud_edit_runs;
begin
  update public.cloud_edit_runs
  set input_tokens = input_tokens + greatest(p_input, 0),
      output_tokens = output_tokens + greatest(p_output, 0),
      cache_read_tokens = cache_read_tokens + greatest(p_cache_read, 0),
      cache_write_tokens = cache_write_tokens + greatest(p_cache_write, 0),
      cost_microusd = cost_microusd + greatest(p_cost, 0),
      updated_at = p_now
  where id = p_run
  returning * into run;
  if found then
    update public.cloud_months set edit_cost_microusd = edit_cost_microusd + greatest(p_cost, 0)
    where month = run.month;
  end if;
end;
$$;

-- A step that failed at the provider gives its step back; a run whose first step failed gives back the run.
create function public.cloud_edit_release(p_run uuid)
returns void
language plpgsql
volatile
set search_path = ''
as $$
declare
  run public.cloud_edit_runs;
begin
  update public.cloud_edit_runs set steps = greatest(steps - 1, 0)
  where id = p_run
  returning * into run;
  if found and run.steps = 0 then
    delete from public.cloud_edit_runs where id = p_run;
    update public.cloud_installs set edit_runs = greatest(edit_runs - 1, 0)
    where id = run.install_id and edit_month = run.month;
    update public.cloud_months set edit_runs = greatest(edit_runs - 1, 0) where month = run.month;
  end if;
end;
$$;

revoke execute on function
  public.cloud_edit_begin(uuid, uuid, timestamptz),
  public.cloud_edit_settle(uuid, bigint, bigint, bigint, bigint, bigint, timestamptz),
  public.cloud_edit_release(uuid)
from public, anon, authenticated;

grant execute on function
  public.cloud_edit_begin(uuid, uuid, timestamptz),
  public.cloud_edit_settle(uuid, bigint, bigint, bigint, bigint, bigint, timestamptz),
  public.cloud_edit_release(uuid)
to service_role;
