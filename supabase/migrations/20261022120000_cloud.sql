-- PreCut cloud AI (Phase 20): registered installs, their monthly minutes, the month's total and
-- the limits. Only the `cloud` Edge Function touches these, with the project's secret key: RLS is
-- on with no policies, table grants are revoked, and the functions are executable by
-- service_role alone. Seconds are whole seconds of audio; months are UTC, 'YYYY-MM'.

create table public.cloud_config (
  -- A single row: the check makes `true` the only possible key.
  id boolean primary key default true check (id),
  -- 30 minutes per install per month.
  free_seconds_per_month integer not null default 1800 check (free_seconds_per_month >= 0),
  -- 5,400 minutes a month for everyone together (about $20 at $0.0037 a minute).
  budget_seconds_per_month integer not null default 324000 check (budget_seconds_per_month >= 0),
  -- The longest audio one request may send.
  max_seconds_per_upload integer not null default 600 check (max_seconds_per_upload > 0),
  -- Stops all cloud AI at once.
  paused boolean not null default false
);
insert into public.cloud_config default values;

create table public.cloud_installs (
  id uuid primary key,
  created_at timestamptz not null default now(),
  last_used_at timestamptz not null default now(),
  -- The month the two counters belong to; a new month starts them again.
  month text not null,
  used_seconds integer not null default 0 check (used_seconds >= 0),
  -- Held for requests still being transcribed.
  reserved_seconds integer not null default 0 check (reserved_seconds >= 0)
);
create index cloud_installs_last_used_at on public.cloud_installs (last_used_at);

create table public.cloud_months (
  month text primary key,
  used_seconds integer not null default 0 check (used_seconds >= 0),
  reserved_seconds integer not null default 0 check (reserved_seconds >= 0)
);

alter table public.cloud_config enable row level security;
alter table public.cloud_installs enable row level security;
alter table public.cloud_months enable row level security;
revoke all on table public.cloud_config, public.cloud_installs, public.cloud_months from anon, authenticated;

-- The month [p_now] falls in, and when the next one starts.
create function public.cloud_month(p_now timestamptz)
returns text
language sql
stable
set search_path = ''
as $$
  select to_char(p_now at time zone 'UTC', 'YYYY-MM')
$$;

create function public.cloud_next_month(p_now timestamptz)
returns timestamptz
language sql
stable
set search_path = ''
as $$
  select (date_trunc('month', p_now at time zone 'UTC') + interval '1 month') at time zone 'UTC'
$$;

-- What the app shows: minutes left this month and the limits. Null when [p_install] isn't registered.
create function public.cloud_status(p_install uuid, p_now timestamptz default now())
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
begin
  select * into cfg from public.cloud_config where id;
  select * into ins from public.cloud_installs where id = p_install;
  if not found then
    return null;
  end if;
  used := case when ins.month = cur then ins.used_seconds + ins.reserved_seconds else 0 end;
  select * into mon from public.cloud_months where month = cur;
  return jsonb_build_object(
    'cloudEnabled', not cfg.paused
      and coalesce(mon.used_seconds + mon.reserved_seconds, 0) < cfg.budget_seconds_per_month,
    'secondsLeft', greatest(cfg.free_seconds_per_month - used, 0),
    'secondsPerMonth', cfg.free_seconds_per_month,
    'resetsAt', public.cloud_next_month(p_now),
    'maxUploadSeconds', cfg.max_seconds_per_upload
  );
end;
$$;

-- Adds [p_install] (or touches it again) and returns its status. Also deletes installs unused for
-- 13 months: registrations are rare, so this is the cheapest place for that clean-up.
create function public.cloud_register(p_install uuid, p_now timestamptz default now())
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
begin
  delete from public.cloud_installs where last_used_at < p_now - interval '13 months';
  insert into public.cloud_installs (id, created_at, last_used_at, month)
  values (p_install, p_now, p_now, public.cloud_month(p_now))
  on conflict (id) do update set last_used_at = excluded.last_used_at;
  return public.cloud_status(p_install, p_now);
end;
$$;

-- Holds [p_seconds] for one request if this install's free minutes and everyone's monthly
-- budget both allow it. Returns {ok: true, month} or {ok: false, code, ...}. Locks the install's
-- row, then the month's, the same order as cloud_settle and cloud_release.
create function public.cloud_reserve(p_install uuid, p_seconds integer, p_now timestamptz default now())
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
  if ins.used_seconds + ins.reserved_seconds + p_seconds > cfg.free_seconds_per_month then
    return jsonb_build_object(
      'ok', false, 'code', 'quota_used',
      'secondsLeft', greatest(cfg.free_seconds_per_month - ins.used_seconds - ins.reserved_seconds, 0),
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

-- Ends a reservation made in [p_month]: frees [p_reserved] and charges [p_charged] (0 releases
-- it when the request failed). If the install's month has moved on meanwhile, only the month's total is
-- charged: the install's counters already started again.
create function public.cloud_settle(
  p_install uuid,
  p_month text,
  p_reserved integer,
  p_charged integer,
  p_now timestamptz default now()
)
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
begin
  update public.cloud_installs
  set reserved_seconds = greatest(reserved_seconds - p_reserved, 0),
      used_seconds = used_seconds + greatest(p_charged, 0),
      last_used_at = p_now
  where id = p_install and month = p_month;
  update public.cloud_months
  set reserved_seconds = greatest(reserved_seconds - p_reserved, 0),
      used_seconds = used_seconds + greatest(p_charged, 0)
  where month = p_month;
  return public.cloud_status(p_install, p_now);
end;
$$;

-- Deletes everything kept about [p_install] ("Delete my cloud data"). True when there was something.
create function public.cloud_forget(p_install uuid)
returns boolean
language plpgsql
volatile
set search_path = ''
as $$
begin
  delete from public.cloud_installs where id = p_install;
  return found;
end;
$$;

revoke execute on function
  public.cloud_month(timestamptz),
  public.cloud_next_month(timestamptz),
  public.cloud_status(uuid, timestamptz),
  public.cloud_register(uuid, timestamptz),
  public.cloud_reserve(uuid, integer, timestamptz),
  public.cloud_settle(uuid, text, integer, integer, timestamptz),
  public.cloud_forget(uuid)
from public, anon, authenticated;

grant execute on function
  public.cloud_month(timestamptz),
  public.cloud_next_month(timestamptz),
  public.cloud_status(uuid, timestamptz),
  public.cloud_register(uuid, timestamptz),
  public.cloud_reserve(uuid, integer, timestamptz),
  public.cloud_settle(uuid, text, integer, integer, timestamptz),
  public.cloud_forget(uuid)
to service_role;
