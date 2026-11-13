import { createClient } from "npm:@supabase/supabase-js@2.108.2";

const url = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const publicKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const options = { auth: { autoRefreshToken: false, persistSession: false } };

function json(status: number, body: Record<string, unknown>): Response {
    return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

Deno.serve(async (request) => {
    if (request.method !== "POST") return json(405, { error: "Method not allowed" });
    const reader = request.body?.getReader();
    if (!reader) return json(400, { error: "Invalid request" });
    let body: { code?: unknown; device_nonce?: unknown } | null;
    try {
        const decoder = new TextDecoder("utf-8", { fatal: true });
        let size = 0;
        let input = "";
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            size += value.byteLength;
            if (size > 2048) {
                await reader.cancel();
                return json(413, { error: "Request too large" });
            }
            input += decoder.decode(value, { stream: true });
        }
        body = JSON.parse(input + decoder.decode());
    } catch {
        return json(400, { error: "Invalid request" });
    } finally {
        reader.releaseLock();
    }
    if (typeof body?.code !== "string" || !/^[A-Za-z0-9_-]{6,128}$/.test(body.code) ||
        typeof body.device_nonce !== "string" || !/^[A-Za-z0-9_-]{12,128}$/.test(body.device_nonce)) {
        return json(400, { error: "Invalid device code or nonce" });
    }
    try {
        // A caller has no TV session yet. This RPC consumes the approved code and
        // its matching device nonce exactly once, before a token can be issued.
        const admin = createClient(url, serviceKey, options);
        const { data, error } = await admin.rpc("consume_tv_login_session", {
            p_code: body.code,
            p_device_nonce: body.device_nonce,
        });
        const approvedUserId = data?.[0]?.approved_user_id;
        if (error || !approvedUserId) return json(400, { error: "TV login not approved or expired" });

        const { data: account, error: accountError } = await admin.auth.admin.getUserById(approvedUserId);
        if (accountError || !account.user?.email) return json(503, { error: "Unable to issue TV session" });
        const { data: link, error: linkError } = await admin.auth.admin.generateLink({
            type: "magiclink",
            email: account.user.email,
        });
        if (linkError || !link.properties?.hashed_token) return json(503, { error: "Unable to issue TV session" });

        const client = createClient(url, publicKey, options);
        const { data: verified, error: verifyError } = await client.auth.verifyOtp({
            type: "magiclink",
            token_hash: link.properties.hashed_token,
        });
        const session = verified.session;
        if (verifyError || !session || session.user.id !== approvedUserId) {
            return json(503, { error: "Unable to issue TV session" });
        }
        return json(200, {
            access_token: session.access_token,
            refresh_token: session.refresh_token,
            token_type: session.token_type,
            expires_in: session.expires_in,
        });
    } catch {
        return json(503, { error: "Unable to issue TV session" });
    }
});
