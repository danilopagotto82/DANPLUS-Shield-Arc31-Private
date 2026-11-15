import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const { PGlite } = await import(pathToFileURL(process.argv[2]));
const db = new PGlite();
const owner = "10000000-0000-0000-0000-000000000001";
const secondOwner = "10000000-0000-0000-0000-000000000002";
const profile = "20000000-0000-0000-0000-000000000001";
const otherProfile = "20000000-0000-0000-0000-000000000002";
const token = (last) => "30000000-0000-0000-0000-00000000000" + last;
const write = (revision, mutation, selection) => ({ expected_revision: revision, mutation, selection });
const pick = { id: "collection-1", style: "GRADIENT", gradient: -12216377 };
const moved = { id: "collection-2", style: "SOLID", solid: -16283890 };
const empty = { revision: 0, selection: null };
async function sync(payload = {}, who = owner, viewer = profile) {
    return (await db.query("select public.danplus_special_highlight_v1($1,$2,$3) as body",
        [who, viewer, JSON.stringify(payload)])).rows[0].body;
}
async function sql(path) { await db.exec(await readFile(new URL(path, import.meta.url), "utf8")); }

try {
    await db.exec("create role anon; create role authenticated; create role service_role bypassrls;");
    await sql("../migrations/20261113233333_nuvio_profile_indications.sql");
    await sql("../migrations/20261115231247_danplus_profile_special_highlight.sql");
    await db.exec("set role authenticated");
    await assert.rejects(() => sync(), /permission denied/);
    await assert.rejects(() => db.query("select * from danplus_nuvio.profile_special_highlights"), /permission denied/);
    await db.exec("set role service_role");
    assert.deepEqual(await sync(), empty);
    assert.deepEqual(await sync(write(0, token(1), pick)), { revision: 1, selection: pick });
    assert.deepEqual(await sync(write(0, token(1), pick)), { revision: 1, selection: pick });
    assert.deepEqual(await sync(write(0, token(2), moved)), { revision: 1, selection: pick });
    assert.deepEqual(await sync({}, owner, otherProfile), empty);
    assert.deepEqual(await sync({}, secondOwner), empty);
    await assert.rejects(() => sync(write(1, token(3), { id: "collection", style: "IMAGE" })), /D12_INVALID_HIGHLIGHT/);
    assert.deepEqual(await sync(write(1, token(4), null)), { revision: 2, selection: null });
    assert.deepEqual(await sync(write(1, token(5), moved)), { revision: 2, selection: null });
    await db.exec("reset role");
    await sql("../rollback/20261115231247_danplus_profile_special_highlight.sql");
    await db.exec("set role service_role");
    await assert.rejects(() => sync(), /does not exist/);
    await db.exec("reset role");
    await sql("../migrations/20261115231247_danplus_profile_special_highlight.sql");
    await db.exec("set role service_role");
    assert.deepEqual(await sync(), { revision: 2, selection: null });
    console.log("PASS special highlight selection, conflict, removal, role scope and rollback");
} finally {
    await db.close();
}
