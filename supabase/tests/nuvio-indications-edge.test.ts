import assert from "node:assert/strict";
import { serve } from "../functions/nuvio-indications/index.ts";

const user = "10000000-0000-0000-0000-000000000001";
const owner = "10000000-0000-0000-0000-000000000002";
const session = "10000000-0000-0000-0000-000000000003";
const profileId = "10000000-0000-0000-0000-000000000004";
const wrongProfileId = "10000000-0000-0000-0000-000000000005";
const token = "header." + btoa(JSON.stringify({ sub: user, role: "authenticated", session_id: session })).replace(/=+$/, "") + ".signature";
const profile = {
    id: profileId, user_id: owner, profile_index: 2, name: "Esposa",
    avatar_color_hex: "#123456", pin_enabled: true, updated_at: "2026-11-13T00:00:00Z",
};
const send = { operation: "send", profile_id: profileId, payload: {} };
const indication = { recipient_id: profileId, media_id: "tt123", media_type: "movie", created_at: "2026-11-13T00:00:00Z", episode_keys: [], watched: false };

Deno.test("Nuvio authorizes the account and PIN before Supabase accepts a write", async () => {
    const nativeFetch = globalThis.fetch;
    let authenticate = false;
    let pinVerified = false;
    const mutations: Record<string, unknown>[] = [];
    Deno.env.set("SUPABASE_URL", "https://danplus.test");
    Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "test-service-key");
    globalThis.fetch = async (input, init) => {
        const url = new URL(String(input));
        const headers = new Headers(init?.headers);
        if (url.host === "api.nuvio.tv") {
            assert.equal(headers.get("Authorization"), "Bearer " + token);
            if (url.pathname === "/auth/v1/user") return Response.json({ id: user }, { status: authenticate ? 200 : 401 });
            if (url.pathname.endsWith("/get_sync_owner")) return Response.json(owner);
            if (url.pathname.endsWith("/sync_pull_profiles")) return Response.json([profile]);
            if (url.pathname.endsWith("/verify_profile_pin")) return Response.json([{ unlocked: pinVerified }]);
            if (url.pathname.endsWith("/sync_pull_watched_items")) {
                assert.equal(url.searchParams.get("content_id"), 'in.("tt123")');
                return Response.json([{ content_id: "tt123", content_type: "movie", season: null, episode: null, watched_at: Date.parse(indication.created_at) + 1000 }]);
            }
        }
        assert.equal(url.href, "https://danplus.test/rest/v1/rpc/nuvio_indications_v1");
        assert.equal(headers.get("Authorization"), "Bearer test-service-key");
        const mutation = JSON.parse(String(init?.body));
        mutations.push(mutation);
        if (mutation.operation === "list") return Response.json([indication]);
        return Response.json({ unlocked: true, proof: wrongProfileId });
    };
    const request = (body: Record<string, unknown>) => serve(new Request("https://danplus.test", {
        method: "POST", headers: { Authorization: "Bearer " + token, "X-Nuvio-Publishable-Key": "test-key-example-01234" },
        body: JSON.stringify(body),
    }));
    try {
        assert.equal((await request(send)).status, 401);
        assert.equal(mutations.length, 0);
        authenticate = true;
        assert.equal((await request({ ...send, profile_id: wrongProfileId })).status, 403);
        assert.equal(mutations.length, 0);
        const profileList = await (await request({ operation: "profiles" })).json();
        assert.equal(profileList[0].id, profileId);
        assert.equal(profileList[0].user_id, undefined);
        const unlock = { operation: "unlock", profile_id: profileId, payload: { pin: "1234" } };
        assert.deepEqual(await (await request(unlock)).json(), { unlocked: false, retry_after_seconds: 0 });
        assert.equal(mutations.length, 0);
        pinVerified = true;
        assert.equal((await request(unlock)).status, 200);
        const mutation = mutations[0];
        assert.ok(mutation);
        assert.equal(mutation.owner, owner);
        assert.equal(mutation.caller_session, session);
        assert.deepEqual(mutation.payload, {});
        const inbox = await request({ operation: "list", profile_id: profileId, proof: wrongProfileId, payload: { box: "received" } });
        assert.deepEqual(await inbox.json(), [{ ...indication, watched: true }]);
    } finally {
        globalThis.fetch = nativeFetch;
        Deno.env.delete("SUPABASE_URL");
        Deno.env.delete("SUPABASE_SERVICE_ROLE_KEY");
    }
});
