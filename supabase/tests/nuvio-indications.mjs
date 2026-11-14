import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const { PGlite } = await import(pathToFileURL(process.argv[2]));
const db = new PGlite();
const owner = "10000000-0000-0000-0000-000000000000";
const secondOwner = "20000000-0000-0000-0000-000000000000";
const sender = "30000000-0000-0000-0000-000000000001";
const spouse = "30000000-0000-0000-0000-000000000002";
const mother = "30000000-0000-0000-0000-000000000003";
const other = "30000000-0000-0000-0000-000000000004";
const shield = "40000000-0000-0000-0000-000000000001";
const fire = "40000000-0000-0000-0000-000000000002";
const remoteTv = "40000000-0000-0000-0000-000000000003";
const profile = (id, pin_enabled = false, updated_at = "1") => ({ id, pin_enabled, updated_at });
const profiles = [profile(sender), profile(spouse), profile(mother, true)];
const movie = {
    request_id: "50000000-0000-0000-0000-000000000001",
    recipients: [spouse, mother], media_type: "movie", media_id: "tt123",
    title: "Filme de teste", synopsis: "Sinopse", poster_path: "/teste.jpg",
};
const call = async (operation, profileId, payload = {}, session = shield, proof = null, current = profiles, account = owner) =>
    (await db.query("select public.nuvio_indications_v1($1,$2,$3,$4,$5,$6,$7) as result",
        [account, session, profileId, JSON.stringify(current), operation, JSON.stringify(payload), proof])).rows[0].result;

try {
    await db.exec("create role anon; create role authenticated; create role service_role bypassrls;");
    const file = "../migrations/20261113233333_nuvio_profile_indications.sql";
    await db.exec(await readFile(new URL(file, import.meta.url), "utf8"));
    await db.exec("set role authenticated");
    await assert.rejects(() => call("send", sender, movie), /permission denied/);
    await assert.rejects(() => db.query("select * from danplus_nuvio.recommendations"), /permission denied/);
    await db.exec("set role service_role");
    await assert.rejects(() => call("send", sender, { ...movie, recipients: [other] }), /D12_PROFILE_UNAVAILABLE/);
    assert.deepEqual(await call("send", sender, movie), { accepted: 2 });
    await call("send", sender, movie);
    const inbox = { box: "received" };
    const sameTv = await call("list", spouse, inbox);
    const anotherTv = await call("list", spouse, inbox, fire);
    assert.deepEqual(sameTv, anotherTv);
    assert.equal(sameTv.length, 1);
    assert.equal(sameTv[0].media_id, movie.media_id);
    assert.equal(sameTv[0].poster_path, movie.poster_path);
    assert.equal(sameTv[0].synopsis, movie.synopsis);
    await assert.rejects(() => call("list", mother, inbox, remoteTv), /D12_PROFILE_LOCKED/);
    const { proof } = await call("unlock", mother, {}, remoteTv);
    await assert.rejects(() => call("list", mother, inbox, shield, proof), /D12_PROFILE_LOCKED/);
    await assert.rejects(() => call("list", mother, inbox, remoteTv, proof,
        [profile(sender), profile(spouse), profile(mother, true, "2")]), /D12_PROFILE_LOCKED/);
    const distant = await call("list", mother, inbox, remoteTv, proof);
    assert.equal(distant[0].recipient_id, mother);
    assert.notEqual(distant[0].id, sameTv[0].id);
    assert.deepEqual(await call("list", other, inbox, fire, null, [profile(other)], secondOwner), []);
    await assert.rejects(() => call("read", mother, { id: sameTv[0].id }, remoteTv, proof), /D12_RECOMMENDATION_UNAVAILABLE/);
    await call("read", spouse, { id: sameTv[0].id }, fire);
    assert.ok((await call("list", spouse, inbox))[0].read_at);
    await call("dismiss", spouse, { id: sameTv[0].id }, fire);
    assert.deepEqual(await call("list", spouse, inbox), []);
    assert.equal((await call("list", sender, { box: "sent" })).length, 2);
    assert.deepEqual(await call("list", spouse, inbox, shield, null, [profile(spouse)]), []);
    await db.exec("reset role");
    const rollback = file.replace("/migrations/", "/rollback/");
    await db.exec(await readFile(new URL(rollback, import.meta.url), "utf8"));
    await assert.rejects(() => call("list", spouse, inbox), /does not exist/);
    assert.equal((await db.query("select count(*)::integer as total from danplus_nuvio.recommendations")).rows[0].total, 2);
    console.log("PASS — same account across sessions, profile isolation, retry, PIN, read, dismiss and rollback");
} finally {
    await db.close();
}
