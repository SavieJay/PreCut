-- Supabase's free plan (chosen 2026-11-14): at most 50 MB a file and 1 GB of storage for the whole
-- project. Files over 50 MB (long videos) stay on the phone that has them; their projects still
-- sync. Raise these (and the bucket's limit) when the project moves to the Pro plan
-- (docs/ACCOUNTS.md).
update public.cloud_config set
  max_media_bytes = 52428800,
  free_storage_bytes = 104857600,
  pro_storage_bytes = 314572800;

update storage.buckets set file_size_limit = 52428800 where id = 'media';
