-- Template links (Phase 28): a template shared from the app is stored here under a short id and
-- opened by https://<website>/t/<id> (the app through Android App Links, or the website's page).
-- Templates hold no media (slots take the user's own; music isn't shared), only the edit. Deleting
-- an install's cloud data deletes the templates it shared. Only service_role (the function) runs these.

alter table public.cloud_config
  add column templates_per_day integer not null default 20 check (templates_per_day >= 0);

create table public.shared_templates (
  id text primary key check (id ~ '^[A-Za-z0-9]{8}$'),
  install_id uuid not null references public.cloud_installs (id) on delete cascade,
  template jsonb not null,
  created_at timestamptz not null,
  opens integer not null default 0
);

create index shared_templates_install on public.shared_templates (install_id, created_at);

alter table public.shared_templates enable row level security;
revoke all on table public.shared_templates from anon, authenticated;

-- Stores a template under p_id for p_install, within the day's limit.
create function public.cloud_share_template(p_install uuid, p_id text, p_template jsonb, p_now timestamptz default now())
returns jsonb
language plpgsql
volatile
set search_path = ''
as $$
declare
  cfg public.cloud_config;
  today integer;
begin
  select * into cfg from public.cloud_config where id;
  if not exists (select 1 from public.cloud_installs where id = p_install) then
    return jsonb_build_object('ok', false, 'code', 'not_registered');
  end if;
  select count(*) into today from public.shared_templates
  where install_id = p_install and created_at > p_now - interval '1 day';
  if today >= cfg.templates_per_day then
    return jsonb_build_object('ok', false, 'code', 'templates_used');
  end if;
  insert into public.shared_templates (id, install_id, template, created_at) values (p_id, p_install, p_template, p_now);
  return jsonb_build_object('ok', true, 'id', p_id);
end;
$$;

-- The template under p_id (counting the open), or null.
create function public.cloud_open_template(p_id text)
returns jsonb
language sql
volatile
set search_path = ''
as $$
  update public.shared_templates set opens = opens + 1 where id = p_id returning template;
$$;

revoke all on function public.cloud_share_template(uuid, text, jsonb, timestamptz), public.cloud_open_template(text) from public, anon, authenticated;
grant execute on function public.cloud_share_template(uuid, text, jsonb, timestamptz), public.cloud_open_template(text) to service_role;
