import { assert, assertEquals, assertRejects } from "@std/assert";
import { PGlite } from "@electric-sql/pglite";

/**
 * The quota SQL in supabase/migrations/, run in PGlite (Postgres in-process). Supabase's
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

// AI edits (Phase 22C).

const RUN1 = "11111111-1111-4111-8111-111111111111";
const RUN2 = "22222222-2222-4222-8222-222222222222";
const beginEdit = (db: PGlite, id: string, run: string, at = OCT) =>
  call(db, "public.cloud_edit_begin($1, $2, $3)", [id, run, at]);
const settleEdit = (db: PGlite, run: string, cost: number, at = OCT) =>
  call(db, "public.cloud_edit_settle($1, $2, $3, $4, $5, $6, $7)", [
    run,
    1000,
    100,
    500,
    0,
    cost,
    at,
  ]);
const releaseEdit = (db: PGlite, run: string) =>
  db.query("select public.cloud_edit_release($1)", [run]);

sqlTest(
  "edit: a run uses one of the month's runs, and each step one of its steps",
  async (db) => {
    await register(db, A);
    const s = await status(db, A);
    assertEquals([
      s.editEnabled,
      s.editRunsLeft,
      s.editRunsPerMonth,
      s.maxEditSteps,
    ], [true, 10, 10, 8]);
    assertEquals(await beginEdit(db, A, RUN1), {
      ok: true,
      month: "2026-10",
      stepsLeft: 7,
      editRunsLeft: 9,
    });
    assertEquals(await beginEdit(db, A, RUN1), {
      ok: true,
      month: "2026-10",
      stepsLeft: 6,
      editRunsLeft: 9,
    });
    await db.exec("update public.cloud_config set max_edit_steps = 2");
    const used = await beginEdit(db, A, RUN1);
    assertEquals([used.ok, used.code, used.maxEditSteps], [
      false,
      "steps_used",
      2,
    ]);
    assertEquals((await status(db, A)).editRunsLeft, 9);
  },
);

sqlTest(
  "edit: the month's free runs run out, and come back next month",
  async (db) => {
    await register(db, A);
    await db.exec(
      "update public.cloud_config set free_edit_runs_per_month = 1",
    );
    assert((await beginEdit(db, A, RUN1)).ok);
    const refused = await beginEdit(db, A, RUN2);
    assertEquals([refused.ok, refused.code, refused.editRunsLeft], [
      false,
      "runs_used",
      0,
    ]);
    assertEquals(
      new Date(refused.resetsAt).toISOString(),
      "2026-11-01T00:00:00.000Z",
    );
    assert((await beginEdit(db, A, RUN2, NOV)).ok);
  },
);

sqlTest(
  "edit: settling adds tokens and cost; the month's budget stops new runs, not running ones",
  async (db) => {
    await register(db, A);
    await db.exec(
      "update public.cloud_config set edit_budget_microusd_per_month = 10000",
    );
    await beginEdit(db, A, RUN1);
    await settleEdit(db, RUN1, 12_000);
    const run = (await db.query<
      { steps: number; input_tokens: number; cost_microusd: number }
    >(
      "select steps, input_tokens, cost_microusd from public.cloud_edit_runs where id = $1",
      [RUN1],
    )).rows[0];
    assertEquals([
      run.steps,
      Number(run.input_tokens),
      Number(run.cost_microusd),
    ], [1, 1000, 12_000]);
    assertEquals((await status(db, A)).editEnabled, false);
    assertEquals((await beginEdit(db, A, RUN2)).code, "budget_reached");
    assert((await beginEdit(db, A, RUN1)).ok);
  },
);

sqlTest(
  "edit: a failed first step gives the run back; a run belongs to its install",
  async (db) => {
    await register(db, A);
    await register(db, B);
    await beginEdit(db, A, RUN1);
    assertEquals((await beginEdit(db, B, RUN1)).code, "bad_request");
    await releaseEdit(db, RUN1);
    assertEquals((await status(db, A)).editRunsLeft, 10);
    assertEquals(
      (await db.query("select * from public.cloud_edit_runs")).rows.length,
      0,
    );
    assertEquals((await beginEdit(db, A, RUN1)).editRunsLeft, 9);
  },
);

sqlTest(
  "edit: paused, unregistered, and forgotten installs",
  async (db) => {
    assertEquals((await beginEdit(db, A, RUN1)).code, "not_registered");
    await register(db, A);
    await beginEdit(db, A, RUN1);
    await call(db, "public.cloud_forget($1)", [A]);
    assertEquals(
      (await db.query("select * from public.cloud_edit_runs")).rows.length,
      0,
    );
    await register(db, A);
    await db.exec("update public.cloud_config set edit_paused = true");
    assertEquals((await beginEdit(db, A, RUN2)).code, "paused");
    for (const role of ["anon", "authenticated"]) {
      await db.exec(`reset role; set role ${role};`);
      await assertRejects(
        () => beginEdit(db, A, RUN2),
        Error,
        "permission denied",
      );
      await assertRejects(
        () => db.query("select * from public.cloud_edit_runs"),
        Error,
        "permission denied",
      );
    }
  },
);

sqlTest(
  "app config: zeros until set, then the row; readable by anyone, writable by no app",
  async (db) => {
    const config = (platform: string) =>
      call(db, "public.cloud_app_config($1)", [platform]);
    assertEquals((await config("android")).minSupportedVersionCode, 0);
    assertEquals((await config("android")).whatsNew, []);
    await db.exec(`update public.app_config
      set min_supported_version_code = 40, latest_version_code = 42,
          whats_new = '["AI edit", "Forced updates"]' where platform = 'android'`);
    const set = await config("android");
    assertEquals(set.minSupportedVersionCode, 40);
    assertEquals(set.latestVersionCode, 42);
    assertEquals(set.whatsNew, ["AI edit", "Forced updates"]);
    assert(set.updatedAt);
    // An unknown platform never blocks.
    assertEquals((await config("ios")).minSupportedVersionCode, 0);
    await assertRejects(
      () =>
        db.exec(
          "update public.app_config set whats_new = '\"text\"' where platform = 'android'",
        ),
      Error,
      "check",
    );
    for (const role of ["anon", "authenticated"]) {
      await db.exec(`reset role; set role ${role};`);
      const rows = await db.query("select * from public.app_config");
      assertEquals(rows.rows.length, 1);
      await assertRejects(
        () =>
          db.exec(
            "update public.app_config set min_supported_version_code = 0",
          ),
        Error,
        "permission denied",
      );
      await assertRejects(() => config("android"), Error, "permission denied");
    }
  },
);

const TOKEN_HASH = "a".repeat(64);
const YEAR_LATER = "2027-10-22T10:00:00Z";

function savePurchase(
  db: PGlite,
  install: string | null,
  state: string,
  expires: string | null,
  hash = TOKEN_HASH,
) {
  return call(
    db,
    "public.cloud_save_purchase($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
    [
      install,
      hash,
      "token",
      "precut_pro",
      "yearly",
      state,
      expires,
      true,
      false,
      OCT,
    ],
  );
}

sqlTest(
  "pro: a linked active purchase gives the Pro quotas; cancelled stays Pro until it ends",
  async (db) => {
    await register(db, A);
    assertEquals((await status(db, A)).pro.pro, false);
    assertEquals((await status(db, A)).secondsPerMonth, 1800);
    const saved = await savePurchase(db, A, "active", YEAR_LATER);
    assertEquals(saved.plan.pro, true);
    const pro = await status(db, A);
    assertEquals(pro.secondsPerMonth, 18000);
    assertEquals(pro.editRunsPerMonth, 100);
    // The reservation follows the plan too: a fourth 10-minute upload is past the free 30.
    for (let i = 0; i < 4; i++) {
      assertEquals(
        (await call(db, "public.cloud_reserve($1, $2, $3)", [A, 600, OCT])).ok,
        true,
      );
    }
    await savePurchase(db, null, "canceled", YEAR_LATER);
    assertEquals((await status(db, A)).pro.pro, true);
    await savePurchase(db, null, "canceled", "2026-10-01T00:00:00Z");
    const ended = await status(db, A);
    assertEquals(ended.pro.pro, false);
    assertEquals(ended.pro.state, "canceled");
    assertEquals(ended.secondsPerMonth, 1800);
  },
);

sqlTest(
  "pro: on hold isn't Pro; one purchase restores onto another install; forgetting drops unlinked purchases",
  async (db) => {
    await register(db, A);
    await register(db, B);
    await savePurchase(db, A, "on_hold", YEAR_LATER);
    assertEquals((await status(db, A)).pro, {
      pro: false,
      state: "on_hold",
      productId: "precut_pro",
      basePlanId: "yearly",
      expiresAt: "2027-10-22T10:00:00+00:00",
      autoRenewing: true,
      trial: false,
    });
    await savePurchase(db, B, "active", YEAR_LATER);
    assertEquals((await status(db, A)).pro.pro, true);
    assertEquals((await status(db, B)).pro.pro, true);
    assert(await call(db, "public.cloud_purchase_known($1)", [TOKEN_HASH]));
    await call(db, "public.cloud_forget($1)", [A]);
    assert(await call(db, "public.cloud_purchase_known($1)", [TOKEN_HASH]));
    await call(db, "public.cloud_forget($1)", [B]);
    assert(!(await call(db, "public.cloud_purchase_known($1)", [TOKEN_HASH])));
    assertEquals(
      (await savePurchase(
        db,
        "00000000-0000-4000-8000-000000000000",
        "active",
        YEAR_LATER,
      )).code,
      "not_registered",
    );
    for (const role of ["anon", "authenticated"]) {
      await db.exec(`reset role; set role ${role};`);
      await assertRejects(
        () => db.query("select * from public.cloud_purchases"),
        Error,
        "permission denied",
      );
    }
  },
);

sqlTest("pro: AI edit runs follow the plan", async (db) => {
  await register(db, A);
  await db.exec("update public.cloud_config set free_edit_runs_per_month = 0");
  const RUN = "6b0c2f8e-1d3a-4e5b-9c6d-0a1b2c3d4e5f";
  assertEquals(
    (await call(db, "public.cloud_edit_begin($1, $2, $3)", [A, RUN, OCT])).code,
    "runs_used",
  );
  await savePurchase(db, A, "grace", YEAR_LATER);
  const begun = await call(db, "public.cloud_edit_begin($1, $2, $3)", [
    A,
    RUN,
    OCT,
  ]);
  assertEquals(begun.ok, true);
  assertEquals(begun.editRunsLeft, 99);
});
