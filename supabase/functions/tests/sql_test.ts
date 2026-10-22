import { assert, assertEquals, assertRejects } from "@std/assert";
import { PGlite } from "@electric-sql/pglite";

/**
 * The quota SQL in supabase/migrations/*_cloud.sql, run in PGlite (Postgres in-process). Supabase's
 * roles are recreated as the platform has them: service_role bypasses RLS and gets table grants by
 * default; anon and authenticated stand for the public API.
 */

const MIGRATIONS = new URL("../../migrations/", import.meta.url);
const A = "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f";
const B = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";
const OCT = "2026-10-22T10:00:00Z";
const NOV = "2026-11-03T08:00:00Z";

async function database(): Promise<PGlite> {
  const db = await PGlite.create();
  await db.exec(
    "create role anon; create role authenticated; create role service_role bypassrls;",
  );
  for await (const entry of Deno.readDir(MIGRATIONS)) {
    if (entry.name.endsWith("_cloud.sql")) {
      await db.exec(await Deno.readTextFile(new URL(entry.name, MIGRATIONS)));
    }
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
function sqlTest(name: string, fn: (db: PGlite) => Promise<void>) {
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

// deno-lint-ignore no-explicit-any
async function call(db: PGlite, sql: string, params: unknown[]): Promise<any> {
  const result = await db.query<{ r: unknown }>(`select ${sql} as r`, params);
  return result.rows[0].r;
}

const register = (db: PGlite, id: string, at = OCT) =>
  call(db, "public.cloud_register($1, $2)", [id, at]);
const status = (db: PGlite, id: string, at = OCT) =>
  call(db, "public.cloud_status($1, $2)", [id, at]);
const reserve = (db: PGlite, id: string, seconds: number, at = OCT) =>
  call(db, "public.cloud_reserve($1, $2, $3)", [id, seconds, at]);
const settle = (
  db: PGlite,
  id: string,
  month: string,
  reserved: number,
  charged: number,
  at = OCT,
) =>
  call(db, "public.cloud_settle($1, $2, $3, $4, $5)", [
    id,
    month,
    reserved,
    charged,
    at,
  ]);

async function monthTotal(db: PGlite, month: string) {
  const { rows } = await db.query<
    { used_seconds: number; reserved_seconds: number }
  >(
    "select used_seconds, reserved_seconds from public.cloud_months where month = $1",
    [month],
  );
  return rows[0] ?? null;
}

sqlTest(
  "a new install has 30 free minutes until the first of next month",
  async (db) => {
    const s = await register(db, A);
    assertEquals([
      s.cloudEnabled,
      s.secondsLeft,
      s.secondsPerMonth,
      s.maxUploadSeconds,
    ], [true, 1800, 1800, 600]);
    assertEquals(
      new Date(s.resetsAt).toISOString(),
      "2026-11-01T00:00:00.000Z",
    );
    assertEquals(await status(db, B), null);
  },
);

sqlTest(
  "reserve holds minutes, settle charges what was used and frees the rest",
  async (db) => {
    await register(db, A);
    assertEquals(await reserve(db, A, 120), { ok: true, month: "2026-10" });
    assertEquals((await status(db, A)).secondsLeft, 1680);
    assertEquals((await settle(db, A, "2026-10", 120, 125)).secondsLeft, 1675);
    assertEquals(await monthTotal(db, "2026-10"), {
      used_seconds: 125,
      reserved_seconds: 0,
    });
    // A failed request is settled with nothing charged.
    await reserve(db, A, 300);
    assertEquals((await settle(db, A, "2026-10", 300, 0)).secondsLeft, 1675);
  },
);

sqlTest(
  "an install can't go past its free minutes, and is told how many are left",
  async (db) => {
    await register(db, A);
    await reserve(db, A, 600);
    await reserve(db, A, 600);
    await reserve(db, A, 590);
    const refused = await reserve(db, A, 20);
    assertEquals([refused.ok, refused.code, refused.secondsLeft], [
      false,
      "quota_used",
      10,
    ]);
    assertEquals(
      new Date(refused.resetsAt).toISOString(),
      "2026-11-01T00:00:00.000Z",
    );
    assertEquals((await reserve(db, A, 10)).ok, true);
  },
);

sqlTest("limits: unregistered, too long, bad length, paused", async (db) => {
  await register(db, A);
  assertEquals(await reserve(db, B, 10), { ok: false, code: "not_registered" });
  assertEquals(await reserve(db, A, 601), {
    ok: false,
    code: "too_long",
    maxUploadSeconds: 600,
  });
  assertEquals((await reserve(db, A, 0)).code, "bad_audio");
  await db.exec("update public.cloud_config set paused = true where id");
  assertEquals(await reserve(db, A, 10), { ok: false, code: "paused" });
  assertEquals((await status(db, A)).cloudEnabled, false);
});

sqlTest("the monthly budget is shared by every install", async (db) => {
  await db.exec(
    "update public.cloud_config set budget_seconds_per_month = 1000 where id",
  );
  await register(db, A);
  await register(db, B);
  assertEquals((await reserve(db, A, 600)).ok, true);
  const refused = await reserve(db, B, 500);
  assertEquals([refused.ok, refused.code], [false, "budget_reached"]);
  assertEquals((await status(db, B)).cloudEnabled, true);
  await settle(db, A, "2026-10", 600, 600);
  assertEquals((await reserve(db, B, 400)).ok, true);
  assertEquals((await status(db, B)).cloudEnabled, false);
});

sqlTest(
  "a new month starts the install's minutes again; the old month keeps its total",
  async (db) => {
    await register(db, A);
    await reserve(db, A, 600);
    await settle(db, A, "2026-10", 600, 600);
    assertEquals((await status(db, A, NOV)).secondsLeft, 1800);
    assertEquals(await reserve(db, A, 100, NOV), {
      ok: true,
      month: "2026-11",
    });
    assertEquals((await status(db, A, NOV)).secondsLeft, 1700);
    assertEquals(await monthTotal(db, "2026-10"), {
      used_seconds: 600,
      reserved_seconds: 0,
    });
  },
);

sqlTest(
  "a request that started last month is charged to last month's total only",
  async (db) => {
    await register(db, A);
    await reserve(db, A, 200);
    // The month turns while transcribing; the next reservation starts November's counters.
    await reserve(db, A, 50, NOV);
    await settle(db, A, "2026-10", 200, 210, NOV);
    assertEquals(await monthTotal(db, "2026-10"), {
      used_seconds: 210,
      reserved_seconds: 0,
    });
    assertEquals((await status(db, A, NOV)).secondsLeft, 1750);
  },
);

sqlTest(
  "forget deletes the install; registering cleans up installs unused for 13 months",
  async (db) => {
    await register(db, A);
    assertEquals(await call(db, "public.cloud_forget($1)", [A]), true);
    assertEquals(await status(db, A), null);
    assertEquals(await call(db, "public.cloud_forget($1)", [A]), false);

    await register(db, A, "2025-08-01T00:00:00Z");
    await register(db, B, OCT);
    assertEquals(await status(db, A), null);
    assertEquals((await status(db, B)).secondsLeft, 1800);
  },
);

sqlTest(
  "the public API roles can't run the functions or read the tables",
  async (db) => {
    await register(db, A);
    for (const role of ["anon", "authenticated"]) {
      await db.exec(`reset role; set role ${role};`);
      await assertRejects(() => status(db, A), Error, "permission denied");
      await assertRejects(() => reserve(db, A, 10), Error, "permission denied");
      await assertRejects(
        () => db.query("select * from public.cloud_installs"),
        Error,
        "permission denied",
      );
    }
    await db.exec("reset role; set role service_role;");
    assert((await status(db, A)).secondsLeft === 1800);
  },
);
