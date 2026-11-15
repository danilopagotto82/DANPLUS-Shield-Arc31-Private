const OFFICIAL_ACCOUNT_SERVER = "https://api.nuvio.tv";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const encoder = new TextEncoder();
const MAX_BYTES = 925_000;

type Body = Record<string, unknown>;
type Dependencies = {
    fetch: typeof fetch;
    origin: string;
    serviceKey: string;
};

class SupportError extends Error {
    constructor(readonly status: number, message: string) { super(message); }
}

function isObject(value: unknown): value is Body {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isUuid(value: unknown): value is string {
    return typeof value === "string" && UUID.test(value);
}

async function readBody(request: Request): Promise<Body> {
    const reader = request.body?.getReader();
    if (!reader) throw new SupportError(400, "DANPLUS_SUPPORT_INVALID_REQUEST");
    const decoder = new TextDecoder("utf-8", { fatal: true });
    let bytes = 0;
    let text = "";
    try {
        for (;;) {
            const next = await reader.read();
            if (next.done) break;
            bytes += next.value.byteLength;
            if (bytes > MAX_BYTES) {
                await reader.cancel();
                throw new SupportError(413, "DANPLUS_SUPPORT_TOO_LARGE");
            }
            text += decoder.decode(next.value, { stream: true });
        }
        const value: unknown = JSON.parse(text + decoder.decode());
        if (isObject(value)) return value;
    } catch (error) {
        if (error instanceof SupportError) throw error;
    }
    throw new SupportError(400, "DANPLUS_SUPPORT_INVALID_REQUEST");
}

async function resolveAccount(request: Request, fetcher: typeof fetch): Promise<string> {
    const bearer = request.headers.get("Authorization") ?? "";
    const key = request.headers.get("X-Nuvio-Publishable-Key") ?? "";
    if (!/^Bearer [\w.-]{1,4096}$/.test(bearer) || !/^[\w.-]{20,4096}$/.test(key)) {
        throw new SupportError(401, "DANPLUS_SUPPORT_SIGN_IN");
    }
    const headers = { apikey: key, Authorization: bearer, "Content-Type": "application/json" };
    const userResponse = await fetcher(OFFICIAL_ACCOUNT_SERVER + "/auth/v1/user", {
        headers, redirect: "error", signal: AbortSignal.timeout(6000),
    });
    if (userResponse.status === 401 || userResponse.status === 403) throw new SupportError(401, "DANPLUS_SUPPORT_SIGN_IN");
    if (!userResponse.ok) throw new SupportError(502, "DANPLUS_SUPPORT_ACCOUNT_UNAVAILABLE");
    const user: unknown = await userResponse.json();
    let claims: unknown;
    try {
        const encoded = bearer.slice(7).split(".")[1];
        claims = JSON.parse(atob(encoded.replaceAll("-", "+").replaceAll("_", "/")));
    } catch {
        throw new SupportError(401, "DANPLUS_SUPPORT_SIGN_IN");
    }
    if (!isObject(user) || !isUuid(user.id) || user.is_anonymous === true ||
        !isObject(claims) || claims.sub !== user.id || !isUuid(claims.session_id) ||
        claims.role !== "authenticated" || claims.is_anonymous === true) {
        throw new SupportError(401, "DANPLUS_SUPPORT_SIGN_IN");
    }
    const accountResponse = await fetcher(OFFICIAL_ACCOUNT_SERVER + "/rest/v1/rpc/get_sync_owner", {
        method: "POST", body: "{}", headers, redirect: "error", signal: AbortSignal.timeout(6000),
    });
    if (accountResponse.status === 401 || accountResponse.status === 403) throw new SupportError(401, "DANPLUS_SUPPORT_SIGN_IN");
    if (!accountResponse.ok) throw new SupportError(502, "DANPLUS_SUPPORT_ACCOUNT_UNAVAILABLE");
    const account: unknown = await accountResponse.json();
    if (!isUuid(account)) throw new SupportError(401, "DANPLUS_SUPPORT_SIGN_IN");
    return account.toLowerCase();
}

async function rpc(deps: Dependencies, name: string, payload: Body): Promise<unknown> {
    if (!deps.origin || !deps.serviceKey) throw new SupportError(503, "DANPLUS_SUPPORT_UNAVAILABLE");
    const response = await deps.fetch(deps.origin.replace(/\/$/, "") + "/rest/v1/rpc/" + name, {
        method: "POST",
        headers: { apikey: deps.serviceKey, Authorization: "Bearer " + deps.serviceKey, "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        redirect: "error",
        signal: AbortSignal.timeout(8000),
    });
    if (response.status === 400) throw new SupportError(400, "DANPLUS_SUPPORT_INVALID_REQUEST");
    if (response.status === 403) throw new SupportError(403, "DANPLUS_SUPPORT_FORBIDDEN");
    if (!response.ok) throw new SupportError(502, "DANPLUS_SUPPORT_STORAGE_UNAVAILABLE");
    return await response.json();
}

export async function handleSupportAccount(request: Request, deps: Dependencies): Promise<Response> {
    const headers = { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" };
    const reply = (value: unknown, status = 200) => Response.json(value, { status, headers });
    try {
        if (request.method !== "POST") throw new SupportError(405, "DANPLUS_SUPPORT_INVALID_METHOD");
        const body = await readBody(request);
        if (body.operation === "list") {
            const account = await resolveAccount(request, deps.fetch);
            return reply(await rpc(deps, "danplus_support_my_reports", { p_account: account }));
        }
        if (body.operation !== "submit" || !isUuid(body.install) || !isUuid(body.expected_owner) ||
            (body.route !== "app" && body.route !== "playback") || !isObject(body.report)) {
            throw new SupportError(400, "DANPLUS_SUPPORT_INVALID_REQUEST");
        }
        const account = await resolveAccount(request, deps.fetch);
        if (body.expected_owner.toLowerCase() !== account) throw new SupportError(409, "DANPLUS_SUPPORT_ACCOUNT_CHANGED");
        const digest = await crypto.subtle.digest("SHA-256",
            encoder.encode(JSON.stringify(["danplus-account-report-v1", account, body.route, body.report])));
        const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
        const result = await rpc(deps, "danplus_support_receive_account", {
            p_account: account, p_install: body.install, p_route: body.route, p_hash: hash, p_payload: body.report,
        });
        if (isObject(result) && result.rateLimited === true) return reply({ code: "DANPLUS_SUPPORT_RATE_LIMITED" }, 429);
        if (!isObject(result) || !isUuid(result.reportId)) throw new SupportError(502, "DANPLUS_SUPPORT_STORAGE_UNAVAILABLE");
        return reply({ reportId: result.reportId });
    } catch (error) {
        if (error instanceof SupportError) return reply({ code: error.message }, error.status);
        return reply({ code: "DANPLUS_SUPPORT_UNAVAILABLE" }, 502);
    }
}

// Deploy this route without the local JWT gateway check: resolveAccount verifies the Nuvio issuer.
if (import.meta.main) {
    Deno.serve(request => handleSupportAccount(request, {
        fetch,
        origin: Deno.env.get("SUPABASE_URL") ?? "",
        serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
    }));
}
