-- The newest release's version name ("1.2.0"), so the update screen can say "You have 1.1.0 ·
-- Latest 1.2.0" (the app knows only its own name). Null leaves the latest out. Set it together
-- with latest_version_code (docs/RELEASING.md).

alter table public.app_config add column latest_version_name text
  check (latest_version_name is null or length(latest_version_name) between 1 and 32);

-- As before, plus latestVersionName. CREATE OR REPLACE keeps the grants below as they were.
create or replace function public.cloud_app_config(p_platform text) returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (select jsonb_build_object(
      'minSupportedVersionCode', c.min_supported_version_code,
      'latestVersionCode', c.latest_version_code,
      'latestVersionName', c.latest_version_name,
      'forceMessage', c.force_message,
      'whatsNew', c.whats_new,
      'downloadUrl', c.download_url,
      'updatedAt', c.updated_at
    ) from public.app_config c where c.platform = p_platform),
    jsonb_build_object('minSupportedVersionCode', 0, 'latestVersionCode', 0, 'latestVersionName', null,
      'forceMessage', null, 'whatsNew', '[]'::jsonb, 'downloadUrl', null, 'updatedAt', null)
  );
$$;

revoke all on function public.cloud_app_config(text) from public, anon, authenticated;
grant execute on function public.cloud_app_config(text) to service_role;
