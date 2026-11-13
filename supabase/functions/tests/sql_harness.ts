import { PGlite } from "@electric-sql/pglite";

/**
 * The SQL in supabase/migrations/, run in PGlite (Postgres in-process). Supabase's roles are
 * recreated as the platform has them: service_role bypasses RLS and gets table grants by default;
 * anon and authenticated stand for the public API.
 */

const MIGRATIONS = new URL("../../migrations/", import.meta.url);
export async function database(): Promise<PGlite> {
  const db = await PGlite.create();
  await db.exec(
    "create role anon; create role authenticated; create role service_role bypassrls;",
  );
  // What the platform's Auth and Storage schemas provide that the migrations use: users, the
  // signed-in user's id (from the request's JWT claims), buckets and stored objects.
  await db.exec(`
    create schema auth;
    create table auth.users (id uuid primary key, email text);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to anon, authenticated, service_role;
    grant execute on function auth.uid() to anon, authenticated, service_role;
    create schema storage;
    create table storage.buckets (id text primary key, name text not null, public boolean,
      file_size_limit bigint, allowed_mime_types text[]);
    create table storage.objects (id uuid primary key default gen_random_uuid(), bucket_id text,
      name text, owner uuid, metadata jsonb, created_at timestamptz default now());
    alter table storage.objects enable row level security;
    grant usage on schema storage to anon, authenticated, service_role;
    grant select, insert on storage.objects to authenticated;
    grant all on storage.objects, storage.buckets, auth.users to service_role;
  `);
  // Every migration, in the order Supabase applies them (by name).
  const names: string[] = [];
  for await (const entry of Deno.readDir(MIGRATIONS)) {
    if (entry.name.endsWith(".sql")) names.push(entry.name);
  }
  for (const name of names.sort()) {
    await db.exec(await Deno.readTextFile(new URL(name, MIGRATIONS)));
  }
  await db.exec(`
    grant usage on schema public to service_role, anon, authenticated;
    grant all on all tables in schema public to service_role;
    set role service_role;
  `);
  return db;
}

/**
 * A test with a fresh database, closed afterwards. PGlite's WASM runtime keeps timers of its own
 * across databases, so Deno's timer and resource checks are off for these tests only.
 */
export function sqlTest(name: string, fn: (db: PGlite) => Promise<void>) {
  Deno.test({
    name,
    sanitizeOps: false,
    sanitizeResources: false,
    fn: async () => {
      const db = await database();
      try {
        await fn(db);
      } finally {
        await db.close();
      }
    },
  });
}

export async function call(
  db: PGlite,
  sql: string,
  params: unknown[],
  // deno-lint-ignore no-explicit-any
): Promise<any> {
  const result = await db.query<{ r: unknown }>(`select ${sql} as r`, params);
  return result.rows[0].r;
}
