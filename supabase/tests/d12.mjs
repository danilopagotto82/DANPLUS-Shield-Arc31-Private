import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const runtime = process.argv[2];
if (!runtime) throw new Error("Provide the absolute path to PGlite's dist/index.js");
const { PGlite } = await import(pathToFileURL(runtime));
const { pgcrypto } = await import(new URL("./contrib/pgcrypto.js", pathToFileURL(runtime)));
const db = new PGlite({ extensions: { pgcrypto } });
const load = (file) => readFile(new URL(file, import.meta.url), "utf8");
const migration = "20261113041420_d12_account_profile_recommendations.sql";
const profileMigration = "20261113184656_d12_create_recommendation_profile.sql";
const uid = (id) => "00000000-0000-0000-0000-" + String(id).padStart(12, "0");
const a = uid(1), b = uid(2), a1 = uid(11), a2 = uid(12), b1 = uid(21), a3 = uid(13);
const sa = uid(31), sb = uid(32), saOtherDevice = uid(33);

async function session(user, sessionId, role = "authenticated") {
    await db.exec("reset role");
    await db.query("select set_config('request.jwt.claim.sub', $1, false), set_config('request.jwt.claims', $2, false)",
        [user, JSON.stringify({ session_id: sessionId })]);
    await db.exec(role === "anon" ? "set role anon" : "set role authenticated");
}

async function call(operation, profile, payload = {}, proof = null) {
    const result = await db.query("select public.recommendations_v1($1, $2, $3::jsonb, $4) as data",
        [operation, profile, JSON.stringify(payload), proof]);
    return result.rows[0].data;
}

const send = (requestId, recipients, mediaId = "tmdb:123") =>
    call("send", a1, { request_id: requestId, recipients, media_id: mediaId, media_type: "movie", title: "Filme de teste" });
const received = (profile, proof) => call("list", profile, { box: "received" }, proof);
const sent = () => call("list", a1, { box: "sent" });

try {
    await db.exec("create schema extensions; create extension pgcrypto schema extensions;");
    await db.exec(await load("./d12_fixture.sql"));
    await db.exec(await load("../migrations/" + migration));
    await db.exec(await load("../migrations/" + profileMigration));
    await db.query("insert into auth.users values ($1), ($2)", [a, b]);
    await db.query("insert into auth.sessions values ($1, $2, null), ($3, $4, null), ($5, $2, null)",
        [sa, a, sb, b, saOtherDevice]);
    await db.query("insert into public.profiles values ($1, $2, 1, false, null, null), " +
        "($3, $2, 2, true, '1234', now()), ($4, $5, 1, false, null, null), ($6, $2, 3, false, null, null)",
        [a1, a, a2, b1, b, a3]);
    await session(a, sa, "anon");
    await assert.rejects(sent, /permission denied/i);
    const create = async (name, color, pin) =>
        (await db.query("select public.d12_create_profile($1, $2, $3) as id", [name, color, pin])).rows[0].id;
    await assert.rejects(() => create("Quarto", "#000000", null), /permission denied/i);
    await session(a, sa);
    await assert.rejects(() => create("Quarto", "invalid", null), /D12_INVALID_PROFILE/);
    const created = await create("Quarto", "#1E88E5", "9753");
    assert.match(created, /^[0-9a-f-]{36}$/);
    await assert.rejects(() => received(created), /D12_PROFILE_LOCKED/);
    assert.equal((await call("unlock", created, { pin: "9753" })).unlocked, true);
    await session(a, saOtherDevice);
    assert.equal((await call("unlock", created, { pin: "9753" })).unlocked, true);
    await session(b, sb);
    await assert.rejects(() => received(created), /D12_PROFILE_UNAVAILABLE/);
    await session(a, sa);
    await assert.rejects(() => db.query("select * from danplus_d12.recommendations"), /permission denied/i);
    await assert.rejects(() => send(uid(41), [a2, b1]), /D12_PROFILE_UNAVAILABLE/);
    assert.deepEqual(await sent(), []);
    assert.deepEqual(await send(uid(41), [a2, a3, a2]), { accepted: 2 });
    await send(uid(41), [a2, a3]);
    await send(uid(42), [a2]);
    assert.equal((await sent()).length, 2);
    await assert.rejects(() => send(uid(41), [a2], "tmdb:other"), /D12_REQUEST_REUSED/);
    await assert.rejects(() => received(a2), /D12_PROFILE_LOCKED/);
    assert.equal((await call("unlock", a2, { pin: "0000" })).unlocked, false);
    const proof = (await call("unlock", a2, { pin: "1234" })).proof;
    const [row] = await received(a2, proof);
    assert.equal(row.watched, false);
    await call("read", a2, { id: row.id }, proof);
    const [read] = await received(a2, proof);
    assert.ok(read.read_at);
    assert.equal(read.watched, false);
    await session(a, saOtherDevice);
    await assert.rejects(() => received(a2, proof), /D12_PROFILE_LOCKED/);
    const otherProof = (await call("unlock", a2, { pin: "1234" })).proof;
    assert.equal((await received(a2, otherProof))[0].id, row.id);
    await session(b, sb);
    await assert.rejects(() => received(a2, proof), /D12_PROFILE_UNAVAILABLE/);
    assert.deepEqual(await received(b1), []);
    await assert.rejects(() => call("read", b1, { id: row.id }), /D12_RECOMMENDATION_UNAVAILABLE/);
    await db.exec("reset role");
    await db.query("insert into public.watched_items(user_id, profile_id, content_id, content_type, watched_at) " +
        "values ($1, 1, 'tmdb:123', 'movie', (extract(epoch from now()) * 1000)::bigint), " +
        "($1, 2, 'tmdb:123', 'movie', (extract(epoch from now()) * 1000)::bigint)", [a]);
    await session(a, sa);
    assert.equal((await received(a2, proof))[0].watched, true);
    assert.equal((await sent()).find((r) => r.id === row.id).watched, false);
    await call("dismiss", a2, { id: row.id }, proof);
    await call("dismiss", a2, { id: row.id }, proof);
    assert.deepEqual(await received(a2, proof), []);
    assert.equal((await sent()).length, 2);
    await send(uid(41), [a2]);
    assert.deepEqual(await received(a2, proof), []);
    await send(uid(43), [a2]);
    assert.deepEqual(await received(a2, proof), []);
    assert.equal((await sent()).length, 2);
    await call("dismiss", a1, { id: row.id });
    await send(uid(44), [a2]);
    assert.equal((await received(a2, proof)).length, 1);
    await db.exec("reset role");
    await db.query("update public.profiles set pin_updated_at = now() where id = $1", [a2]);
    await session(a, sa);
    await assert.rejects(() => received(a2, proof), /D12_PROFILE_LOCKED/);
    await db.exec("reset role");
    await db.query("delete from public.profiles where id = $1", [a2]);
    await db.query("insert into public.profiles values ($1, $2, 2, false, null, null)", [uid(15), a]);
    await session(a, sa);
    assert.deepEqual(await received(uid(15)), []);
    assert.equal((await sent()).length, 1);
    await db.exec("reset role");
    await db.query("delete from auth.sessions where id = $1", [sa]);
    await session(a, sa);
    await assert.rejects(sent, /D12_SESSION_REQUIRED/);
    await assert.rejects(() => create("Após revogação", "#000000", null), /D12_SESSION_REQUIRED/);
    await db.exec("reset role");
    await db.exec(await load("../rollback/" + profileMigration));
    await db.exec(await load("../rollback/" + migration));
    const result = await db.query("select to_regnamespace('danplus_d12') as namespace, to_regclass('public.profiles') as profiles");
    assert.equal(result.rows[0].namespace, null);
    assert.equal(result.rows[0].profiles, "profiles");
    console.log("PASS D12 SQL: privacidade, contas, PIN, sessão, idempotência, leitura, assistido, exclusão, slot reutilizado e rollback.");
} finally {
    await db.close();
}
