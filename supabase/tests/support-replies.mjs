import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const { PGlite } = await import(pathToFileURL(process.argv[2]));
const db = new PGlite();
const admin = "10000000-0000-0000-0000-000000000001";
const accountA = "20000000-0000-0000-0000-000000000001";
const accountB = "20000000-0000-0000-0000-000000000002";
const install = "30000000-0000-0000-0000-000000000001";
const request = "40000000-0000-0000-0000-000000000001";
const fixture = { schemaVersion: "1", kind: "bug", note: "Falha sintética" };
const fixtureHash = "a".repeat(64);
const legacyHash = "b".repeat(64);
const call = async (name, parameters = []) => (await db.query(
    "select public." + name + "(" + parameters.map((_, index) => "$" + (index + 1)).join(",") + ") as result",
    parameters,
)).rows[0].result;
const setUser = async (id, anonymous = false) => {
    await db.exec("reset role");
    await db.query("select set_config('request.jwt.claims', $1, false)", [JSON.stringify({ sub: id, is_anonymous: anonymous })]);
    await db.exec("set role authenticated");
};

try {
    await db.exec(`
        create role anon; create role authenticated; create role service_role bypassrls;
        create schema auth;
        create table auth.users(id uuid primary key);
        create function auth.jwt() returns jsonb language sql stable as
            $$ select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb $$;
        create function auth.uid() returns uuid language sql stable as
            $$ select (auth.jwt()->>'sub')::uuid $$;
    `);
    await db.exec(await readFile(new URL("../migrations/20261113192808_danplus_report_inbox.sql", import.meta.url), "utf8"));
    await db.exec(await readFile(new URL("../migrations/20261115213905_danplus_support_replies.sql", import.meta.url), "utf8"));
    await db.query("insert into auth.users values ($1),($2)", [admin, accountA]);
    await db.query("insert into danplus_support.admins values ($1)", [admin]);

    await db.exec("set role anon");
    await assert.rejects(() => call("danplus_support_queue"), /permission denied/);
    await assert.rejects(() => call("danplus_support_my_reports", [accountA]), /permission denied/);
    await setUser(accountA);
    await assert.rejects(() => call("danplus_support_queue"), /DANPLUS_SUPPORT_FORBIDDEN/);
    await assert.rejects(() => call("danplus_support_my_reports", [accountA]), /permission denied/);
    await setUser(admin, true);
    await assert.rejects(() => call("danplus_support_queue"), /DANPLUS_SUPPORT_FORBIDDEN/);

    await db.exec("reset role; set role service_role");
    const legacy = await call("receive_danplus_report", [install, "app", legacyHash, JSON.stringify(fixture)]);
    const owned = await call("danplus_support_receive_account", [accountA, install, "app", fixtureHash, JSON.stringify(fixture)]);
    assert.ok(legacy.reportId && owned.reportId && legacy.reportId !== owned.reportId);
    assert.deepEqual(await call("danplus_support_receive_account", [accountA, install, "app", fixtureHash, JSON.stringify(fixture)]), owned);
    await assert.rejects(() => call("danplus_support_receive_account", [accountB, install, "app", fixtureHash, JSON.stringify(fixture)]), /ACCOUNT_MISMATCH/);
    await assert.rejects(() => call("danplus_support_receive_account", [accountA, install, "app", legacyHash, JSON.stringify(fixture)]), /ACCOUNT_MISMATCH/);

    await setUser(admin);
    const queue = await call("danplus_support_queue");
    assert.equal(queue.length, 2);
    assert.equal((await call("danplus_support_queue", [null, null, null, null, legacy.reportId]))[0].id, legacy.reportId);
    assert.equal(queue.find(row => row.id === legacy.reportId).can_reply, false);
    assert.equal(queue.find(row => row.id === owned.reportId).can_reply, true);
    await assert.rejects(() => call("danplus_support_update", [legacy.reportId, request, "closed", "Resposta", null]), /LEGACY_NO_RECIPIENT/);
    const args = [owned.reportId, request, "fixed", "Resposta pública", "Nota reservada"];
    const updated = await call("danplus_support_update", args);
    assert.equal(updated.status, "fixed");
    assert.equal(updated.events[0].note, "Nota reservada");
    assert.equal((await call("danplus_support_update", args)).events.length, 1);
    await assert.rejects(() => call("danplus_support_update", [owned.reportId, request, "closed", null, null]), /REQUEST_REUSED/);
    assert.equal((await call("danplus_support_queue", ["bug", "fixed", null, null])).length, 1);
    await setUser(accountB);
    await assert.rejects(() => call("danplus_support_thread", [owned.reportId]), /DANPLUS_SUPPORT_FORBIDDEN/);
    await db.exec("reset role; set role service_role");
    assert.deepEqual(await call("danplus_support_my_reports", [accountB]), []);
    const inbox = await call("danplus_support_my_reports", [accountA]);
    assert.equal(inbox.length, 1);
    assert.equal(inbox[0].id, owned.reportId);
    assert.equal(inbox[0].status, "fixed");
    assert.equal(inbox[0].replies[0].text, "Resposta pública");
    assert.doesNotMatch(JSON.stringify(inbox), /Nota reservada|actor_id|payload/);

    await db.exec("reset role");
    await db.exec(await readFile(new URL("../rollback/20261115213905_danplus_support_replies.sql", import.meta.url), "utf8"));
    assert.equal((await db.query("select count(*) as count from danplus_support.events")).rows[0].count, 1);
    assert.equal((await db.query("select count(*) as count from public.danplus_reports")).rows[0].count, 2);
    await db.exec("set role service_role");
    await assert.rejects(() => call("danplus_support_my_reports", [accountA]), /permission denied/);
    assert.deepEqual(await call("receive_danplus_report", [install, "app", legacyHash, JSON.stringify(fixture)]), legacy);
    console.log("PASS: admin and owner isolation, legacy intake, replies, notes, filters, idempotency and reversible shutdown");
} finally {
    await db.close();
}
