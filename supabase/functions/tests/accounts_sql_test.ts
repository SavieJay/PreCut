import { assert, assertEquals, assertRejects } from "@std/assert";
import type { PGlite } from "@electric-sql/pglite";
import { call, sqlTest } from "./sql_harness.ts";

/** Accounts, cloud projects and their files (Phase 30), run in PGlite (see sql_harness.ts). */

const ANA = "11111111-1111-4111-8111-111111111111";
const BEN = "22222222-2222-4222-8222-222222222222";
const P1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const P2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const INSTALL_1 = "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f";
const INSTALL_2 = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";
const NOW = "2026-11-13T10:00:00Z";
const VIDEO = "a".repeat(64);
const SONG = "b".repeat(64);
const COVER = "c".repeat(64);

async function users(db: PGlite) {
  await db.exec(
    `insert into auth.users (id, email) values ('${ANA}', 'ana@example.com'), ('${BEN}', 'ben@example.com')`,
  );
}

const grant = (db: PGlite, user: string, sha: string, size: number, at = NOW) =>
  call(db, "public.cloud_media_grant($1, $2, $3, $4, $5)", [
    user,
    sha,
    size,
    "video/mp4",
    at,
  ]);
const confirm = (db: PGlite, user: string, sha: string) =>
  call(db, "public.cloud_media_confirm($1, $2)", [user, sha]);

/** What a finished upload leaves in Storage. */
const store = (db: PGlite, user: string, sha: string, size: number) =>
  db.query(
    "insert into storage.objects (bucket_id, name, metadata) values ('media', $1, $2)",
    [`${user}/${sha}`, JSON.stringify({ size })],
  );

/** Grants, "uploads" and confirms a file. */
async function upload(db: PGlite, user: string, sha: string, size: number) {
  assertEquals((await grant(db, user, sha, size)).ok, true);
  await store(db, user, sha, size);
  assertEquals((await confirm(db, user, sha)).ok, true);
}

const save = (
  db: PGlite,
  user: string,
  id: string,
  base: number | null,
  media: string[] = [],
  cover: string | null = null,
  name = "Trip",
  at = NOW,
) =>
  call(
    db,
    "public.cloud_project_save($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
    [
      user,
      id,
      base,
      name,
      JSON.stringify({ clips: media.map((m) => `cloud:${m}`) }),
      media,
      cover,
      12_000,
      at,
      at,
    ],
  );
const remove = (
  db: PGlite,
  user: string,
  id: string,
  base: number | null,
  at = NOW,
) =>
  call(db, "public.cloud_project_delete($1, $2, $3, $4)", [user, id, base, at]);
const changes = (
  db: PGlite,
  user: string,
  since: string | null = null,
  after: string | null = null,
  limit = 50,
) =>
  call(db, "public.cloud_project_changes($1, $2, $3, $4)", [
    user,
    since,
    after,
    limit,
  ]);
const account = (db: PGlite, user: string) =>
  call(db, "public.cloud_account($1, $2)", [user, NOW]);

sqlTest(
  "projects: saved with revisions; a stale save gets the server's copy",
  async (db) => {
    await users(db);
    await upload(db, ANA, VIDEO, 1000);
    const first = await save(db, ANA, P1, null, [VIDEO]);
    assertEquals(first, { ok: true, revision: 1, updatedAt: first.updatedAt });
    // A second phone that never saw revision 1 can't overwrite it.
    const stale = await save(db, ANA, P1, null, [], null, "Other phone");
    assertEquals(stale.code, "conflict");
    assertEquals(stale.project.revision, 1);
    assertEquals(stale.project.name, "Trip");
    assertEquals(stale.project.media, [VIDEO]);
    assertEquals((await save(db, ANA, P1, 7)).code, "conflict");
    assertEquals(
      (await save(db, ANA, P1, 1, [VIDEO], null, "Trip 2")).revision,
      2,
    );
    // A save the server never had, starting from a revision, is a conflict with no copy.
    const unknown = await save(db, ANA, P2, 3);
    assertEquals(unknown, { ok: false, code: "conflict", project: null });
    // Another account's project with the same id is its own.
    assertEquals((await save(db, BEN, P1, null)).revision, 1);
    assertEquals((await account(db, ANA)).projects, 1);
  },
);

sqlTest(
  "projects: files must be uploaded first; over 2 MB is refused",
  async (db) => {
    await users(db);
    assertEquals(await save(db, ANA, P1, null, [VIDEO], COVER), {
      ok: false,
      code: "missing_media",
      missing: [VIDEO, COVER],
    });
    await grant(db, ANA, VIDEO, 10);
    assertEquals((await save(db, ANA, P1, null, [VIDEO])).missing, [VIDEO]); // granted, not uploaded
    // Ben's upload is not Ana's.
    await upload(db, BEN, VIDEO, 10);
    assertEquals(
      (await save(db, ANA, P1, null, [VIDEO])).code,
      "missing_media",
    );
    await db.exec("update public.cloud_config set max_project_bytes = 10");
    assertEquals((await save(db, ANA, P1, null)).code, "too_large");
  },
);

sqlTest(
  "projects: changes come oldest first, a page at a time, with deletions",
  async (db) => {
    await users(db);
    await save(db, ANA, P1, null);
    await save(db, ANA, P2, null);
    await save(db, BEN, P1, null);
    const page1 = await changes(db, ANA, null, null, 1);
    assertEquals(page1.projects.map((p: { id: string }) => p.id), [P1]);
    assertEquals(page1.more, true);
    const page2 = await changes(db, ANA, page1.next.since, page1.next.after, 1);
    assertEquals(page2.projects.map((p: { id: string }) => p.id), [P2]);
    const end = await changes(db, ANA, page2.next.since, page2.next.after, 1);
    assertEquals(end, { projects: [], next: null, more: false });
    // A deletion is a change too: the tombstone has no project and frees the files.
    assertEquals((await remove(db, ANA, P1, 2)).code, "conflict");
    assertEquals(await remove(db, ANA, P1, 1), { ok: true, revision: 2 });
    assertEquals(await remove(db, ANA, P1, 1), { ok: true, revision: 2 }); // again: already gone
    assertEquals(await remove(db, ANA, P2 + "", 1).then((r) => r.ok), true);
    const after = await changes(db, ANA, page2.next.since, page2.next.after);
    assertEquals(
      after.projects.map((
        p: { id: string; deleted: boolean; project: unknown },
      ) => [p.id, p.deleted, p.project]),
      [
        [P1, true, null],
        [P2, true, null],
      ],
    );
    assertEquals((await account(db, ANA)).projects, 0);
    // Saving over a deletion from its revision brings the project back.
    assertEquals((await save(db, ANA, P1, 2)).revision, 3);
    // Unknown deletes are fine.
    assertEquals(
      (await remove(db, ANA, "cccccccc-cccc-4ccc-8ccc-cccccccccccc", null)).ok,
      true,
    );
  },
);

sqlTest(
  "files: granted within the account's storage; Pro gets more",
  async (db) => {
    await users(db);
    await db.exec(
      "update public.cloud_config set free_storage_bytes = 1000, pro_storage_bytes = 5000, max_media_bytes = 3000",
    );
    assertEquals(await grant(db, ANA, VIDEO, 600), { ok: true, exists: false });
    // A grant counts while its upload may still be going.
    assertEquals(await grant(db, ANA, SONG, 600), {
      ok: false,
      code: "storage_full",
      storageUsed: 600,
      storageLimit: 1000,
    });
    // Granting the same file again doesn't count it twice.
    assertEquals((await grant(db, ANA, VIDEO, 700)).ok, true);
    assertEquals((await grant(db, ANA, SONG, 3001)).code, "too_large");
    // An old grant that was never uploaded stops counting after a day.
    assertEquals(
      (await grant(db, ANA, SONG, 600, "2026-11-15T10:00:00Z")).ok,
      true,
    );
    // Pro on any install signed in to the account.
    await call(db, "public.cloud_register($1, $2)", [INSTALL_1, NOW]);
    await call(db, "public.cloud_register($1, $2)", [INSTALL_2, NOW]);
    assertEquals(
      await call(db, "public.cloud_link_install($1, $2)", [ANA, INSTALL_1]),
      true,
    );
    assertEquals(
      await call(db, "public.cloud_link_install($1, $2)", [
        ANA,
        "4f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f",
      ]),
      false,
    );
    await call(
      db,
      "public.cloud_save_purchase($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
      [
        INSTALL_2,
        "d".repeat(64),
        "token",
        "precut_pro",
        "yearly",
        "active",
        "2027-11-13T10:00:00Z",
        true,
        false,
        NOW,
      ],
    );
    assertEquals((await account(db, ANA)).pro, false);
    await call(db, "public.cloud_link_install($1, $2)", [ANA, INSTALL_2]);
    assertEquals((await account(db, ANA)).pro, true);
    assertEquals((await account(db, ANA)).storageLimit, 5000);
    // Pro bought on install 2 now counts on install 1.
    assertEquals(
      (await call(db, "public.cloud_pro($1, $2)", [INSTALL_1, NOW])).pro,
      true,
    );
    assertEquals((await grant(db, ANA, SONG, 2000)).ok, true);
  },
);

sqlTest(
  "files: confirmed only once stored with the granted size",
  async (db) => {
    await users(db);
    assertEquals(await confirm(db, ANA, VIDEO), {
      ok: false,
      code: "not_found",
    });
    await grant(db, ANA, VIDEO, 1000);
    assertEquals(await confirm(db, ANA, VIDEO), {
      ok: false,
      code: "not_found",
    });
    await store(db, ANA, VIDEO, 999);
    assertEquals(await confirm(db, ANA, VIDEO), {
      ok: false,
      code: "size_mismatch",
    });
    await db.exec("update storage.objects set metadata = '{\"size\": 1000}'");
    assertEquals(await confirm(db, ANA, VIDEO), { ok: true });
    assertEquals(
      await call(db, "public.cloud_media_ready($1, $2)", [ANA, VIDEO]),
      true,
    );
    assertEquals(
      await call(db, "public.cloud_media_ready($1, $2)", [BEN, VIDEO]),
      false,
    );
    assertEquals(await grant(db, ANA, VIDEO, 1000), { ok: true, exists: true });
    assertEquals((await account(db, ANA)).storageUsed, 1000);
    assertEquals(await call(db, "public.cloud_account_media($1)", [ANA]), [
      VIDEO,
    ]);
  },
);

sqlTest(
  "storage: a user uploads only granted files, into their own folder",
  async (db) => {
    await users(db);
    await grant(db, ANA, VIDEO, 10);
    await db.exec(
      `reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${ANA}', false);`,
    );
    const put = (name: string) =>
      db.query(
        "insert into storage.objects (bucket_id, name, metadata) values ('media', $1, '{}')",
        [name],
      );
    await put(`${ANA}/${VIDEO}`);
    await assertRejects(
      () => put(`${ANA}/${SONG}`),
      Error,
      "row-level security",
    );
    await assertRejects(
      () => put(`${BEN}/${VIDEO}`),
      Error,
      "row-level security",
    );
    await db.exec(
      `select set_config('request.jwt.claim.sub', '${BEN}', false);`,
    );
    await assertRejects(
      () => put(`${ANA}/${VIDEO}`),
      Error,
      "row-level security",
    );
    // Apps can't reach the tables or the functions.
    await assertRejects(
      () => db.query("select * from public.cloud_projects"),
      Error,
      "permission denied",
    );
    await assertRejects(() => account(db, BEN), Error, "permission denied");
    await assertRejects(
      () => grant(db, BEN, SONG, 1),
      Error,
      "permission denied",
    );
  },
);

sqlTest(
  "clean-up: unused files after 30 days, stale grants after a day, deletions after 90",
  async (db) => {
    await users(db);
    await upload(db, ANA, VIDEO, 10);
    await upload(db, ANA, SONG, 10);
    await upload(db, ANA, COVER, 10);
    await save(db, ANA, P1, null, [VIDEO, SONG]);
    await save(db, ANA, P2, null, [SONG]);
    await grant(db, BEN, VIDEO, 10); // never uploaded
    // P1 deleted: VIDEO is now unused; SONG is still in P2. COVER was never in a project.
    await remove(db, ANA, P1, 1);
    const cleanup = (at: string) => call(db, "public.cloud_cleanup($1)", [at]);
    assertEquals(await cleanup("2026-11-13T12:00:00Z"), []);
    assertEquals(await cleanup("2026-11-14T12:00:00Z"), [{
      userId: BEN,
      sha256: VIDEO,
    }]);
    const dropped = await cleanup("2026-12-14T12:00:00Z");
    assertEquals(
      dropped.map((d: { sha256: string }) => d.sha256).sort(),
      [VIDEO, COVER].sort(),
    );
    assertEquals(
      await call(db, "public.cloud_media_ready($1, $2)", [ANA, SONG]),
      true,
    );
    assertEquals((await changes(db, ANA)).projects.length, 2);
    await cleanup("2027-02-14T12:00:00Z");
    assertEquals(
      (await changes(db, ANA)).projects.map((p: { id: string }) => p.id),
      [P2],
    );
  },
);

sqlTest(
  "deleting the user deletes their projects, files and install links",
  async (db) => {
    await users(db);
    await call(db, "public.cloud_register($1, $2)", [INSTALL_1, NOW]);
    await call(db, "public.cloud_link_install($1, $2)", [ANA, INSTALL_1]);
    await upload(db, ANA, VIDEO, 10);
    await save(db, ANA, P1, null, [VIDEO]);
    await save(db, BEN, P1, null);
    await db.exec(`delete from auth.users where id = '${ANA}'`);
    assertEquals((await changes(db, ANA)).projects, []);
    assertEquals(await call(db, "public.cloud_account_media($1)", [ANA]), []);
    assertEquals((await changes(db, BEN)).projects.length, 1);
    const install = await db.query<{ user_id: string | null }>(
      "select user_id from public.cloud_installs where id = $1",
      [INSTALL_1],
    );
    assertEquals(install.rows[0].user_id, null);
    assert(
      (await call(db, "public.cloud_status($1, $2)", [INSTALL_1, NOW])) !==
        null,
    );
  },
);
