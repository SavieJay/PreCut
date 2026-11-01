-- Forced updates (Phase 23): one row per platform says the oldest app version still allowed to run
-- and the newest one released. Raising min_supported_version_code makes every older app show a
-- blocking "Update now" screen (docs/RELEASING.md). Anyone may read it (it's public by nature;
-- the app reads it through the cloud function's GET /config); only the dashboard writes it.

create table public.app_config (
  platform text primary key check (platform in ('android')),
  min_supported_version_code integer not null default 0 check (min_supported_version_code >= 0),
  latest_version_code integer not null default 0 check (latest_version_code >= 0),
  -- Shown on the blocking screen; null shows the app's own words.
  force_message text,
  -- "What's new" after an update: a JSON list of short strings.
  whats_new jsonb not null default '[]'::jsonb check (jsonb_typeof(whats_new) = 'array'),
  -- Where an app not installed from Google Play gets the new version; null opens the Play listing.
  download_url text,
  updated_at timestamptz not null default now()
);

insert into public.app_config (platform) values ('android');

alter table public.app_config enable row level security;
revoke all on table public.app_config from anon, authenticated;
grant select on table public.app_config to anon, authenticated;
create policy "app config is public" on public.app_config for select to anon, authenticated using (true);

-- Keeps updated_at current for edits made in the dashboard.
create function public.app_config_touch() returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger app_config_touch before update on public.app_config
  for each row execute function public.app_config_touch();

-- The row for [p_platform] as the API returns it; zeros (never block) when there's none.
create function public.cloud_app_config(p_platform text) returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (select jsonb_build_object(
      'minSupportedVersionCode', c.min_supported_version_code,
      'latestVersionCode', c.latest_version_code,
      'forceMessage', c.force_message,
      'whatsNew', c.whats_new,
      'downloadUrl', c.download_url,
      'updatedAt', c.updated_at
    ) from public.app_config c where c.platform = p_platform),
    jsonb_build_object('minSupportedVersionCode', 0, 'latestVersionCode', 0, 'forceMessage', null,
      'whatsNew', '[]'::jsonb, 'downloadUrl', null, 'updatedAt', null)
  );
$$;

revoke all on function public.cloud_app_config(text) from public, anon, authenticated;
revoke all on function public.app_config_touch() from public, anon, authenticated;
grant execute on function public.cloud_app_config(text) to service_role;
