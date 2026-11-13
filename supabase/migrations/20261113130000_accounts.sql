-- Accounts and cloud projects (Phase 30). A Supabase Auth user owns projects and media; deleting
-- the user (DELETE /account) deletes every row here through the foreign keys. As before, apps never
-- touch these tables: the cloud function reads and writes them as the service role through the
-- functions below. The one exception is Storage: the phone uploads a file straight to the private
-- `media` bucket, at `<user id>/<sha256>`, and only after the function granted that file.

alter table public.cloud_config
  -- 2 GB free, 50 GB Pro, 4 GB a file, 2 MB of project JSON.
  add column free_storage_bytes bigint not null default 2147483648 check (free_storage_bytes >= 0),
  add column pro_storage_bytes bigint not null default 53687091200 check (pro_storage_bytes >= 0),
  add column max_media_bytes bigint not null default 4294967296 check (max_media_bytes > 0),
  add column max_project_bytes integer not null default 2097152 check (max_project_bytes > 0);

-- The account an install signed in to; Pro bought on any of its installs counts on all of them.
alter table public.cloud_installs
  add column user_id uuid references auth.users (id) on delete set null;
create index cloud_installs_user on public.cloud_installs (user_id) where user_id is not null;

create table public.cloud_projects (
  user_id uuid not null references auth.users (id) on delete cascade,
  -- The phone's project id.
  id uuid not null,
  -- Raised by every save and delete; a save names the revision it started from.
  revision integer not null default 1 check (revision > 0),
  name text not null default '' check (length(name) <= 200),
  -- The project JSON with its files as `cloud:<sha256>`; null once deleted.
  project jsonb,
  cover_sha256 text check (cover_sha256 ~ '^[0-9a-f]{64}$'),
  duration_ms bigint not null default 0 check (duration_ms >= 0),
  -- When the phone last edited it.
  edited_at timestamptz not null,
  -- When the server took the change: the order phones read changes in.
  updated_at timestamptz not null default clock_timestamp(),
  deleted_at timestamptz,
  primary key (user_id, id),
  check ((deleted_at is null) = (project is not null))
);
create index cloud_projects_changes on public.cloud_projects (user_id, updated_at, id);

create table public.cloud_media (
  user_id uuid not null references auth.users (id) on delete cascade,
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  size bigint not null check (size >= 0),
  mime text not null check (length(mime) between 1 and 100),
  -- Granted: the phone may upload it. Ready: uploaded, and its size checked.
  state text not null check (state in ('granted', 'ready')),
  granted_at timestamptz not null,
  -- Since when no project uses it; the clean-up removes it 30 days later. Null while one does.
  unused_since timestamptz,
  primary key (user_id, sha256)
);

create table public.cloud_project_media (
  user_id uuid not null,
  project_id uuid not null,
  sha256 text not null,
  primary key (user_id, project_id, sha256),
  foreign key (user_id, project_id) references public.cloud_projects (user_id, id) on delete cascade,
  foreign key (user_id, sha256) references public.cloud_media (user_id, sha256)
);
create index cloud_project_media_file on public.cloud_project_media (user_id, sha256);

alter table public.cloud_projects enable row level security;
alter table public.cloud_media enable row level security;
alter table public.cloud_project_media enable row level security;
revoke all on table public.cloud_projects, public.cloud_media, public.cloud_project_media
  from anon, authenticated;

-- Pro follows the account: a purchase linked to any install signed in to the same account counts.
create or replace function public.cloud_pro(p_install uuid, p_now timestamptz default now())
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

-- Whether any install signed in to [p_user] is Pro.
create function public.cloud_account_is_pro(p_user uuid, p_now timestamptz default now())
returns boolean
language sql
stable
set search_path = ''
as $$
  select coalesce(bool_or(public.cloud_is_pro(i.id, p_now)), false)
  from public.cloud_installs i where i.user_id = p_user;
$$;

-- Bytes [p_user] keeps: uploaded files, and grants from the last day (uploads still going).
create function public.cloud_storage_used(p_user uuid, p_now timestamptz default now())
returns bigint
language sql
stable
set search_path = ''
as $$
  select coalesce(sum(m.size), 0)::bigint from public.cloud_media m
  where m.user_id = p_user
    and (m.state = 'ready' or m.granted_at > p_now - interval '1 day');
$$;

-- Signs [p_install] in to [p_user] (and out of any other account). False for an unknown install.
create function public.cloud_link_install(p_user uuid, p_install uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.cloud_installs set user_id = p_user where id = p_install;
  return found;
end;
$$;

-- The account as Settings shows it.
create function public.cloud_account(p_user uuid, p_now timestamptz default now())
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'pro', public.cloud_account_is_pro(p_user, p_now),
    'storageUsed', public.cloud_storage_used(p_user, p_now),
    'storageLimit', case when public.cloud_account_is_pro(p_user, p_now)
      then c.pro_storage_bytes else c.free_storage_bytes end,
    'maxFileBytes', c.max_media_bytes,
    'projects', (select count(*) from public.cloud_projects p
      where p.user_id = p_user and p.deleted_at is null))
  from public.cloud_config c;
$$;

-- One project as phones read it.
create function public.cloud_project_json(p public.cloud_projects)
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p.id,
    'revision', p.revision,
    'name', p.name,
    'project', p.project,
    'coverSha256', p.cover_sha256,
    'durationMs', p.duration_ms,
    'editedAt', p.edited_at,
    'updatedAt', p.updated_at,
    'deleted', p.deleted_at is not null,
    'media', coalesce((select jsonb_agg(m.sha256 order by m.sha256) from public.cloud_project_media m
      where m.user_id = p.user_id and m.project_id = p.id), '[]'::jsonb));
$$;

-- Projects (and deletions) the server took after (p_since, p_after), oldest first, [p_limit] at a
-- time. `next` is where the following page starts; `more` says whether there is one.
create function public.cloud_project_changes(
  p_user uuid,
  p_since timestamptz,
  p_after uuid,
  p_limit integer
) returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with page as (
    select p as proj, p.updated_at, p.id from public.cloud_projects p
    where p.user_id = p_user
      and (p.updated_at, p.id) > (coalesce(p_since, '-infinity'::timestamptz),
        coalesce(p_after, '00000000-0000-0000-0000-000000000000'::uuid))
    order by p.updated_at, p.id
    limit p_limit
  ), last as (
    select updated_at, id from page order by updated_at desc, id desc limit 1
  )
  select jsonb_build_object(
    'projects', coalesce((select jsonb_agg(public.cloud_project_json(page.proj) order by page.updated_at, page.id) from page), '[]'::jsonb),
    'next', (select jsonb_build_object('since', last.updated_at, 'after', last.id) from last),
    'more', (select count(*) from page) = p_limit);
$$;

-- Files [p_user] has in no project any more get their unused date; files back in one lose it.
create function public.cloud_mark_unused(p_user uuid, p_now timestamptz)
returns void
language sql
security definer
set search_path = ''
as $$
  update public.cloud_media m
  set unused_since = case
    when exists (select 1 from public.cloud_project_media l where l.user_id = m.user_id and l.sha256 = m.sha256)
      then null
    else coalesce(m.unused_since, p_now) end
  where m.user_id = p_user;
$$;

-- Saves a project. [p_base] is the revision the phone started from (null for a project the server
-- never had). Refuses a stale save (`conflict`, with the server's copy), a project over the size
-- limit (`too_large`) and one naming files not uploaded yet (`missing_media`).
create function public.cloud_project_save(
  p_user uuid,
  p_id uuid,
  p_base integer,
  p_name text,
  p_project jsonb,
  p_media text[],
  p_cover text,
  p_duration bigint,
  p_edited_at timestamptz,
  p_now timestamptz default now()
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  cur public.cloud_projects;
  missing text[];
  saved public.cloud_projects;
begin
  if octet_length(p_project::text) > (select c.max_project_bytes from public.cloud_config c) then
    return jsonb_build_object('ok', false, 'code', 'too_large');
  end if;
  select * into cur from public.cloud_projects p
    where p.user_id = p_user and p.id = p_id for update;
  if (cur.id is null and p_base is not null) or (cur.id is not null and cur.revision is distinct from p_base) then
    return jsonb_build_object('ok', false, 'code', 'conflict',
      'project', case when cur.id is null then null else public.cloud_project_json(cur) end);
  end if;
  select coalesce(array_agg(f.sha256), '{}') into missing
    from unnest(coalesce(p_media, '{}') || case when p_cover is null then '{}'::text[] else array[p_cover] end) as f(sha256)
    where not exists (select 1 from public.cloud_media m
      where m.user_id = p_user and m.sha256 = f.sha256 and m.state = 'ready');
  if cardinality(missing) > 0 then
    return jsonb_build_object('ok', false, 'code', 'missing_media', 'missing', to_jsonb(missing));
  end if;
  insert into public.cloud_projects as p
    (user_id, id, revision, name, project, cover_sha256, duration_ms, edited_at, updated_at, deleted_at)
  values (p_user, p_id, 1, p_name, p_project, p_cover, p_duration, p_edited_at, clock_timestamp(), null)
  on conflict (user_id, id) do update set
    revision = p.revision + 1,
    name = excluded.name,
    project = excluded.project,
    cover_sha256 = excluded.cover_sha256,
    duration_ms = excluded.duration_ms,
    edited_at = excluded.edited_at,
    updated_at = excluded.updated_at,
    deleted_at = null
  returning * into saved;
  delete from public.cloud_project_media l where l.user_id = p_user and l.project_id = p_id;
  insert into public.cloud_project_media (user_id, project_id, sha256)
    select distinct p_user, p_id, f.sha256
    from unnest(coalesce(p_media, '{}') || case when p_cover is null then '{}'::text[] else array[p_cover] end) as f(sha256);
  perform public.cloud_mark_unused(p_user, p_now);
  return jsonb_build_object('ok', true, 'revision', saved.revision, 'updatedAt', saved.updated_at);
end;
$$;

-- Deletes a project (a tombstone other phones read). [p_base] as in cloud_project_save; deleting
-- one the server doesn't have is fine.
create function public.cloud_project_delete(
  p_user uuid,
  p_id uuid,
  p_base integer,
  p_now timestamptz default now()
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  cur public.cloud_projects;
  saved public.cloud_projects;
begin
  select * into cur from public.cloud_projects p
    where p.user_id = p_user and p.id = p_id for update;
  if cur.id is null then
    return jsonb_build_object('ok', true, 'revision', null);
  end if;
  if cur.deleted_at is not null then
    return jsonb_build_object('ok', true, 'revision', cur.revision);
  end if;
  if cur.revision is distinct from p_base then
    return jsonb_build_object('ok', false, 'code', 'conflict', 'project', public.cloud_project_json(cur));
  end if;
  update public.cloud_projects p set
    revision = p.revision + 1, project = null, deleted_at = p_now, updated_at = clock_timestamp()
  where p.user_id = p_user and p.id = p_id
  returning * into saved;
  delete from public.cloud_project_media l where l.user_id = p_user and l.project_id = p_id;
  perform public.cloud_mark_unused(p_user, p_now);
  return jsonb_build_object('ok', true, 'revision', saved.revision);
end;
$$;

-- Lets the phone upload one file, within the account's storage. `exists` when it's already up.
create function public.cloud_media_grant(
  p_user uuid,
  p_sha256 text,
  p_size bigint,
  p_mime text,
  p_now timestamptz default now()
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  config public.cloud_config;
  cur public.cloud_media;
  used bigint;
  quota bigint;
begin
  select * into config from public.cloud_config;
  if p_size > config.max_media_bytes then
    return jsonb_build_object('ok', false, 'code', 'too_large', 'maxFileBytes', config.max_media_bytes);
  end if;
  select * into cur from public.cloud_media m
    where m.user_id = p_user and m.sha256 = p_sha256 for update;
  if cur.state = 'ready' then
    return jsonb_build_object('ok', true, 'exists', true);
  end if;
  used := public.cloud_storage_used(p_user, p_now)
    - case when cur.sha256 is not null and cur.granted_at > p_now - interval '1 day' then cur.size else 0 end;
  quota := case when public.cloud_account_is_pro(p_user, p_now)
    then config.pro_storage_bytes else config.free_storage_bytes end;
  if used + p_size > quota then
    return jsonb_build_object('ok', false, 'code', 'storage_full', 'storageUsed', used, 'storageLimit', quota);
  end if;
  insert into public.cloud_media as m (user_id, sha256, size, mime, state, granted_at, unused_since)
  values (p_user, p_sha256, p_size, p_mime, 'granted', p_now, p_now)
  on conflict (user_id, sha256) do update set
    size = excluded.size, mime = excluded.mime, granted_at = excluded.granted_at;
  return jsonb_build_object('ok', true, 'exists', false);
end;
$$;

-- Storage's upload policy: the signed-in user may write `<own id>/<sha256>` for a file granted in
-- the last day and not uploaded yet.
create function public.cloud_media_upload_allowed(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.cloud_media m
    where m.user_id = auth.uid()
      and p_name = m.user_id::text || '/' || m.sha256
      and m.state = 'granted'
      and m.granted_at > now() - interval '1 day');
$$;

-- After an upload: the stored object must have the granted size. `not_found` while it isn't up,
-- `size_mismatch` when it differs (the function then removes it, so the phone uploads it again).
create function public.cloud_media_confirm(p_user uuid, p_sha256 text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  cur public.cloud_media;
  stored bigint;
begin
  select * into cur from public.cloud_media m
    where m.user_id = p_user and m.sha256 = p_sha256 for update;
  if cur.sha256 is null then
    return jsonb_build_object('ok', false, 'code', 'not_found');
  end if;
  if cur.state = 'ready' then
    return jsonb_build_object('ok', true);
  end if;
  select (o.metadata ->> 'size')::bigint into stored from storage.objects o
    where o.bucket_id = 'media' and o.name = p_user::text || '/' || p_sha256;
  if stored is null then
    return jsonb_build_object('ok', false, 'code', 'not_found');
  end if;
  if stored <> cur.size then
    return jsonb_build_object('ok', false, 'code', 'size_mismatch');
  end if;
  update public.cloud_media m set state = 'ready'
    where m.user_id = p_user and m.sha256 = p_sha256;
  return jsonb_build_object('ok', true);
end;
$$;

-- Whether [p_user] has [p_sha256] uploaded (before the function signs a download link).
create function public.cloud_media_ready(p_user uuid, p_sha256 text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.cloud_media m
    where m.user_id = p_user and m.sha256 = p_sha256 and m.state = 'ready');
$$;

-- Every file of [p_user], so DELETE /account can remove the stored objects before the user.
create function public.cloud_account_media(p_user uuid)
returns text[]
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(array_agg(m.sha256 order by m.sha256), '{}') from public.cloud_media m
  where m.user_id = p_user;
$$;

-- The daily clean-up: drops grants never uploaded within a day, files no project has used for 30
-- days, and deletions older than 90 days. Answers the dropped files, whose stored objects the
-- function then removes.
create function public.cloud_cleanup(p_now timestamptz default now())
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  dropped jsonb;
begin
  delete from public.cloud_projects p where p.deleted_at < p_now - interval '90 days';
  with gone as (
    delete from public.cloud_media m
    where not exists (select 1 from public.cloud_project_media l
        where l.user_id = m.user_id and l.sha256 = m.sha256)
      and ((m.state = 'granted' and m.granted_at < p_now - interval '1 day')
        or (m.unused_since < p_now - interval '30 days'))
    returning m.user_id, m.sha256
  )
  select coalesce(jsonb_agg(jsonb_build_object('userId', gone.user_id, 'sha256', gone.sha256)), '[]'::jsonb)
    into dropped from gone;
  return dropped;
end;
$$;

do $$
declare
  f text;
begin
  foreach f in array array[
    'public.cloud_account_is_pro(uuid, timestamptz)',
    'public.cloud_storage_used(uuid, timestamptz)',
    'public.cloud_link_install(uuid, uuid)',
    'public.cloud_account(uuid, timestamptz)',
    'public.cloud_project_json(public.cloud_projects)',
    'public.cloud_project_changes(uuid, timestamptz, uuid, integer)',
    'public.cloud_mark_unused(uuid, timestamptz)',
    'public.cloud_project_save(uuid, uuid, integer, text, jsonb, text[], text, bigint, timestamptz, timestamptz)',
    'public.cloud_project_delete(uuid, uuid, integer, timestamptz)',
    'public.cloud_media_grant(uuid, text, bigint, text, timestamptz)',
    'public.cloud_media_upload_allowed(text)',
    'public.cloud_media_confirm(uuid, text)',
    'public.cloud_media_ready(uuid, text)',
    'public.cloud_account_media(uuid)',
    'public.cloud_cleanup(timestamptz)'
  ] loop
    execute format('revoke all on function %s from public, anon, authenticated', f);
    execute format('grant execute on function %s to service_role', f);
  end loop;
end;
$$;
-- Storage checks the upload policy as the signed-in user.
grant execute on function public.cloud_media_upload_allowed(text) to authenticated;

-- Private: files are read through short-lived signed links the function makes.
insert into storage.buckets (id, name, public, file_size_limit)
values ('media', 'media', false, 4294967296)
on conflict (id) do nothing;

create policy "precut media: upload granted files" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'media' and public.cloud_media_upload_allowed(name));
