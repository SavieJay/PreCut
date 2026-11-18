import { assertEquals, assertRejects } from "@std/assert";
import type { PGlite } from "@electric-sql/pglite";
import { call, sqlTest } from "./sql_harness.ts";

/** Phase 31: the 14-day trial, Paystack subscriptions, and which one an account's plan is. */

const ANA = "11111111-1111-4111-8111-111111111111";
const INSTALL = "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f";
const SIGNED_UP = "2026-11-20T10:00:00Z";
const DAY = 24 * 60 * 60 * 1000;
const after = (days: number) =>
  new Date(Date.parse(SIGNED_UP) + days * DAY).toISOString();

async function setUp(db: PGlite, trialSince = "2026-11-18T00:00:00Z") {
  await db.exec(`
    update public.cloud_config set trial_days = 14, trial_since = '${trialSince}', paystack_mode = 'test';
    insert into auth.users (id, email, created_at) values ('${ANA}', 'ana@example.com', '${SIGNED_UP}');
  `);
  await call(db, "public.cloud_register($1, $2)", [INSTALL, SIGNED_UP]);
  await call(db, "public.cloud_link_install($1, $2)", [ANA, INSTALL]);
}

const plan = (db: PGlite, at: string) =>
  call(db, "public.cloud_account_plan($1, $2, $3)", [ANA, "test", at]);
const installPlan = (db: PGlite, at: string) =>
  call(db, "public.cloud_pro($1, $2)", [INSTALL, at]);
const paystack = (
  db: PGlite,
  status: string | null,
  paidUntil: string | null,
  mode = "test",
) =>
  call(
    db,
    "public.cloud_paystack_save($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
    [
      ANA,
      mode,
      "monthly",
      "CUS_1",
      "SUB_1",
      "tok",
      status,
      paidUntil,
      null,
      SIGNED_UP,
    ],
  );

sqlTest(
  "trial: 14 days of Pro from sign-up, then a plan that says it ended",
  async (db) => {
    await setUp(db);
    const during = await plan(db, after(13));
    assertEquals([during.pro, during.source, during.trial, during.state], [
      true,
      "trial",
      true,
      "active",
    ]);
    assertEquals(during.expiresAt, "2026-12-04T10:00:00+00:00");
    const ended = await plan(db, after(15));
    assertEquals([ended.pro, ended.source, ended.state], [
      false,
      "trial",
      "expired",
    ]);
    // The install signed in to the account gets the same answer (quotas, older apps).
    assertEquals((await installPlan(db, after(13))).source, "trial");
    assertEquals(
      (await call(db, "public.cloud_status($1, $2)", [INSTALL, after(13)])).pro
        .pro,
      true,
    );
  },
);

sqlTest(
  "trial: accounts made before the launch count from the launch",
  async (db) => {
    await setUp(db, after(30)); // the trial launched a month after Ana signed up
    assertEquals((await plan(db, after(40))).pro, true);
    assertEquals((await plan(db, after(45))).pro, false);
  },
);

sqlTest(
  "paystack: paid until its date; cancelled keeps the period; a failed renewal keeps 3 days",
  async (db) => {
    await setUp(db);
    await paystack(db, "active", after(45));
    const active = await plan(db, after(20));
    assertEquals([
      active.pro,
      active.source,
      active.state,
      active.autoRenewing,
      active.basePlanId,
    ], [true, "paystack", "active", true, "monthly"]);
    await paystack(db, "non-renewing", after(45));
    assertEquals([
      (await plan(db, after(44))).pro,
      (await plan(db, after(44))).state,
    ], [true, "canceled"]);
    assertEquals((await plan(db, after(46))).pro, false);
    await paystack(db, "attention", after(45));
    assertEquals([
      (await plan(db, after(47))).pro,
      (await plan(db, after(47))).state,
    ], [true, "grace"]);
    assertEquals((await plan(db, after(49))).pro, false);
    await paystack(db, "cancelled", after(45));
    assertEquals((await plan(db, after(20))).pro, false);
    // A lapsed subscription is the plan shown after the trial, not the trial.
    assertEquals((await plan(db, after(50))).source, "paystack");
  },
);

sqlTest("paystack: test payments never count with live keys", async (db) => {
  await setUp(db);
  await paystack(db, "active", after(45), "test");
  assertEquals(
    (await call(db, "public.cloud_account_plan($1, $2, $3)", [
      ANA,
      "live",
      after(20),
    ])).pro,
    false,
  );
  await db.exec("update public.cloud_config set paystack_mode = 'live'");
  assertEquals((await installPlan(db, after(20))).pro, false);
});

sqlTest(
  "plan: Google Play first, then Paystack, then the trial",
  async (db) => {
    await setUp(db);
    await paystack(db, "active", after(45));
    assertEquals((await plan(db, after(5))).source, "paystack"); // paid beats the trial
    await call(
      db,
      "public.cloud_save_purchase($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
      [
        INSTALL,
        "d".repeat(64),
        "token",
        "precut_pro",
        "yearly",
        "active",
        after(400),
        true,
        false,
        SIGNED_UP,
      ],
    );
    assertEquals((await plan(db, after(5))).source, "play");
    assertEquals(
      await call(db, "public.cloud_account_is_pro($1, $2)", [ANA, after(100)]),
      true,
    );
  },
);

sqlTest(
  "paystack: saved fields stay unless given; due checks; the service only",
  async (db) => {
    await setUp(db);
    await call(
      db,
      "public.cloud_paystack_save($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
      [
        ANA,
        "test",
        "yearly",
        null,
        null,
        null,
        null,
        null,
        "ref_1",
        SIGNED_UP,
      ],
    );
    const pending = await call(db, "public.cloud_paystack_get($1)", [ANA]);
    assertEquals([pending.plan, pending.pending_reference, pending.status], [
      "yearly",
      "ref_1",
      null,
    ]);
    assertEquals((await plan(db, after(20))).source, "trial"); // a payment page alone isn't a plan
    await paystack(db, "active", after(45));
    const saved = await call(db, "public.cloud_paystack_get($1)", [ANA]);
    assertEquals([saved.plan, saved.customer_code, saved.pending_reference], [
      "monthly",
      "CUS_1",
      null,
    ]);
    assertEquals(
      await call(db, "public.cloud_paystack_due($1)", [after(0.1)]),
      [],
    );
    assertEquals(await call(db, "public.cloud_paystack_due($1)", [after(1)]), [
      ANA,
    ]);
    for (const role of ["anon", "authenticated"]) {
      await db.exec(`reset role; set role ${role};`);
      await assertRejects(() => plan(db, after(1)), Error, "permission denied");
      await assertRejects(
        () => db.query("select * from public.cloud_paystack"),
        Error,
        "permission denied",
      );
    }
  },
);
