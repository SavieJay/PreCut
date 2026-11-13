-- The publish workflow (.github/workflows/publish-apk.yml) announces every release it publishes
-- through the cloud function's POST /release: the newest version code, its name and "What's new".
-- It only ever raises latest_version_code (an older run finishing late changes nothing) and never
-- touches min_supported_version_code: forcing an update stays a person's choice (docs/RELEASING.md).

create function public.cloud_publish_release(
  p_platform text,
  p_code integer,
  p_name text,
  p_whats_new jsonb
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.app_config
    set latest_version_code = p_code,
        latest_version_name = p_name,
        whats_new = p_whats_new
    where platform = p_platform and latest_version_code <= p_code;
  return public.cloud_app_config(p_platform);
end;
$$;

revoke all on function public.cloud_publish_release(text, integer, text, jsonb) from public, anon, authenticated;
grant execute on function public.cloud_publish_release(text, integer, text, jsonb) to service_role;
