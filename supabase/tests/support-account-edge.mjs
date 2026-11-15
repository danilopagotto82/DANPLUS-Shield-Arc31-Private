import assert from "node:assert/strict";
import { handleSupportAccount } from "../functions/danplus-report-account/index.ts";

const actor = "10000000-0000-0000-0000-000000000001";
const account = "20000000-0000-0000-0000-000000000001";
const other = "20000000-0000-0000-0000-000000000002";
const install = "30000000-0000-0000-0000-000000000001";
const session = "40000000-0000-0000-0000-000000000001";
const receipt = "50000000-0000-0000-0000-000000000001";
const token = (claims = {}) => "header." + Buffer.from(JSON.stringify({
    sub: actor, session_id: session, role: "authenticated", ...claims,
})).toString("base64url") + ".signature";

let selectedAccount = account;
let rejected = false;
let limited = false;
let calls = [];
const deps = {
    origin: "https://support.test",
    serviceKey: "test-service-key",
    async fetch(url, options) {
        calls.push({ url, options });
        if (url === "https://api.nuvio.tv/auth/v1/user") {
            assert.match(options.headers.Authorization, /^Bearer header\./);
            assert.notEqual(options.headers.apikey, deps.serviceKey);
            return rejected ? Response.json({}, { status: 401 }) : Response.json({ id: actor });
        }
        if (url === "https://api.nuvio.tv/rest/v1/rpc/get_sync_owner") return Response.json(selectedAccount);
        const body = JSON.parse(options.body);
        assert.equal(options.headers.Authorization, "Bearer " + deps.serviceKey);
        assert.equal(body.p_account, selectedAccount);
        if (url.endsWith("/danplus_support_my_reports")) return Response.json([]);
        assert.ok(url.endsWith("/danplus_support_receive_account"));
        assert.match(body.p_hash, /^[a-f0-9]{64}$/);
        assert.equal(body.p_install, install);
        return Response.json(limited ? { rateLimited: true } : { reportId: receipt });
    },
};
const request = (body, bearer = token()) => new Request("https://support.test/functions/v1/danplus-report-account", {
    method: "POST",
    headers: { Authorization: "Bearer " + bearer, "X-Nuvio-Publishable-Key": "test-publishable-key-0000" },
    body: JSON.stringify(body),
});
async function call(body, bearer) {
    const response = await handleSupportAccount(request(body, bearer), deps);
    return [response.status, await response.json()];
}

assert.deepEqual(await call({ operation: "list", p_account: other }), [200, []]);
assert.equal(JSON.parse(calls.at(-1).options.body).p_account, account);
calls = [];
assert.equal((await call({ operation: "list" }, token({ sub: other })))[0], 401);
assert.equal(calls.length, 1);
calls = [];
rejected = true;
assert.equal((await call({ operation: "list" }))[0], 401);
assert.equal(calls.length, 1);
rejected = false;
const report = { operation: "submit", route: "app", install, expected_owner: account, report: { schemaVersion: 1, kind: "bug" } };
assert.deepEqual(await call(report), [200, { reportId: receipt }]);
const hashA = JSON.parse(calls.at(-1).options.body).p_hash;
await call(report);
assert.equal(JSON.parse(calls.at(-1).options.body).p_hash, hashA);
selectedAccount = other;
assert.equal((await call(report))[0], 409);
await call({ ...report, expected_owner: other });
assert.notEqual(JSON.parse(calls.at(-1).options.body).p_hash, hashA);
limited = true;
assert.equal((await call({ ...report, expected_owner: other }))[0], 429);
assert.equal((await call({ ...report, report: { large: "x".repeat(940_000) } }))[0], 413);
assert.equal((await call({ ...report, install: "invalid" }))[0], 400);
console.log("PASS: gateway validates account, ignores claimed owner, scopes retries and handles invalid and rate-limited reports");
