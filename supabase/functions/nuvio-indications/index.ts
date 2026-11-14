const NUVIO_URL = "https://api.nuvio.tv";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const OPERATIONS = new Set(["profiles", "unlock", "send", "list", "read", "dismiss"]);

type Profile = {
    id: string;
    user_id: string;
    profile_index: number;
    name: string;
    avatar_color_hex: string;
    pin_enabled: boolean;
    updated_at: string;
};

class RequestError extends Error {
    constructor(readonly status: number, readonly code: string) {
        super(code);
    }
}

function isUuid(value: unknown): value is string {
    return typeof value === "string" && UUID.test(value);
}

function isObject(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}

function tokenClaims(bearer: string): Record<string, unknown> {
    try {
        const encoded = bearer.slice(7).split(".")[1];
        const claims = JSON.parse(atob(encoded.replaceAll("-", "+").replaceAll("_", "/")));
        if (isObject(claims)) return claims;
    } catch {
        // Claims are usable only after Nuvio accepts the token.
    }
    throw new RequestError(401, "D12_SESSION_REQUIRED");
}

async function upstream<T>(
    path: string,
    headers: Record<string, string>,
    payload?: Record<string, unknown>,
): Promise<T> {
    const response = await fetch(NUVIO_URL + path, {
        method: payload === undefined ? "GET" : "POST",
        headers,
        body: payload === undefined ? undefined : JSON.stringify(payload),
        redirect: "error",
        signal: AbortSignal.timeout(6000),
    });
    if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
            throw new RequestError(401, "D12_SESSION_REQUIRED");
        }
        throw new RequestError(502, "D12_NUVIO_UNAVAILABLE");
    }
    return await response.json();
}

async function profilesFor(owner: string, headers: Record<string, string>): Promise<Profile[]> {
    const profiles = await upstream<Profile[]>("/rest/v1/rpc/sync_pull_profiles", headers, {});
    if (!Array.isArray(profiles) || profiles.some((p) =>
        p?.user_id !== owner || !isUuid(p.id) || !Number.isInteger(p.profile_index) ||
        typeof p.name !== "string" || typeof p.avatar_color_hex !== "string" ||
        typeof p.pin_enabled !== "boolean" || typeof p.updated_at !== "string"
    )) {
        throw new RequestError(502, "D12_NUVIO_PROFILES_UNAVAILABLE");
    }
    return profiles;
}

async function database(payload: Record<string, unknown>): Promise<unknown> {
    const origin = Deno.env.get("SUPABASE_URL");
    const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!origin || !key) throw new RequestError(503, "D12_STORAGE_UNAVAILABLE");
    const response = await fetch(origin + "/rest/v1/rpc/nuvio_indications_v1", {
        method: "POST",
        headers: { apikey: key, Authorization: "Bearer " + key, "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        redirect: "error",
        signal: AbortSignal.timeout(6000),
    });
    const result = await response.json();
    if (!response.ok) {
        const code = result?.message;
        if (typeof code === "string" && /^D12_[A-Z_]+$/.test(code)) {
            throw new RequestError(response.status === 400 ? 400 : 403, code);
        }
        throw new RequestError(502, "D12_STORAGE_UNAVAILABLE");
    }
    return result;
}

type Indication = {
    recipient_id: string;
    media_id: string;
    media_type: string;
    created_at: string;
    episode_keys: string[];
    watched: boolean;
};
type Watched = {
    content_id: string;
    content_type: string;
    season: number | null;
    episode: number | null;
    watched_at: number;
};

async function addWatchedStatus(
    items: Indication[], profiles: Profile[], actor: Profile, headers: Record<string, string>,
): Promise<Indication[]> {
    await Promise.all(profiles.filter((profile) => profile.id === actor.id || !profile.pin_enabled).map(async (profile) => {
        const rows = items.filter((item) => item.recipient_id === profile.id);
        if (rows.length === 0) return;
        const quotedIds = [...new Set(rows.map((item) => JSON.stringify(item.media_id)))].join(",");
        const query = new URLSearchParams({
            content_id: "in.(" + quotedIds + ")",
            select: "content_id,content_type,season,episode,watched_at",
        });
        try {
            const history = await upstream<Watched[]>("/rest/v1/rpc/sync_pull_watched_items?" + query, headers, {
                p_profile_id: profile.profile_index, p_page: 1, p_page_size: 10000,
            });
            for (const row of rows) {
                const watched = history.filter((item) => item.content_id === row.media_id && item.watched_at >= Date.parse(row.created_at));
                row.watched = row.media_type === "movie"
                    ? watched.some((item) => item.content_type === "movie" && item.season === null && item.episode === null)
                    : row.episode_keys.length > 0 && row.episode_keys.every((key) => watched.some((item) =>
                        ["series", "tv", "show"].includes(item.content_type) && key === item.season + ":" + item.episode));
            }
        } catch {
            // Indications can still be read when Nuvio's watch history is unavailable.
        }
    }));
    return items;
}

async function handle(request: Request): Promise<unknown> {
    if (request.method !== "POST") throw new RequestError(405, "D12_INVALID_METHOD");
    const bearer = request.headers.get("Authorization") ?? "";
    const publicKey = request.headers.get("X-Nuvio-Publishable-Key") ?? "";
    if (!/^Bearer [\w.-]{1,4096}$/.test(bearer) || !/^[\w.-]{20,4096}$/.test(publicKey)) {
        throw new RequestError(401, "D12_SESSION_REQUIRED");
    }

    // Bound the incoming stream before forwarding data to either service.
    let body: unknown;
    try {
        const reader = request.body?.getReader();
        if (!reader) throw new Error("Missing body");
        const decoder = new TextDecoder();
        let bytes = 0;
        let raw = "";
        for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            bytes += value.byteLength;
            if (bytes > 32768) {
                await reader.cancel();
                throw new Error("Request too large");
            }
            raw += decoder.decode(value, { stream: true });
        }
        body = JSON.parse(raw + decoder.decode());
    } catch {
        throw new RequestError(400, "D12_INVALID_REQUEST");
    }

    if (!isObject(body) || typeof body.operation !== "string" || !OPERATIONS.has(body.operation) ||
        (body.operation !== "profiles" && (!isUuid(body.profile_id) || !isObject(body.payload))) ||
        (body.proof !== undefined && !isUuid(body.proof))) {
        throw new RequestError(400, "D12_INVALID_REQUEST");
    }
    const headers = { apikey: publicKey, Authorization: bearer, "Content-Type": "application/json" };
    const user = await upstream<{ id?: unknown; is_anonymous?: boolean }>("/auth/v1/user", headers);
    const claims = tokenClaims(bearer);
    if (!isUuid(user.id) || user.id !== claims.sub || !isUuid(claims.session_id) ||
        claims.role !== "authenticated" || user.is_anonymous === true || claims.is_anonymous === true) {
        throw new RequestError(401, "D12_SESSION_REQUIRED");
    }
    const owner = await upstream<unknown>("/rest/v1/rpc/get_sync_owner", headers, {});
    if (!isUuid(owner)) throw new RequestError(401, "D12_SESSION_REQUIRED");

    let profiles = await profilesFor(owner, headers);
    if (body.operation === "profiles") {
        return profiles.map(({ id, profile_index, name, avatar_color_hex, pin_enabled }) =>
            ({ id, profile_index, name, avatar_color_hex, pin_enabled }));
    }
    const actor = profiles.find((profile) => profile.id === body.profile_id);
    if (!actor) throw new RequestError(403, "D12_PROFILE_UNAVAILABLE");
    const payload = body.payload as Record<string, unknown>;
    if (body.operation === "unlock") {
        if (!actor.pin_enabled) return { unlocked: true };
        const pin = payload.pin;
        if (typeof pin !== "string" || !/^[0-9]{4}$/.test(pin)) return { unlocked: false };
        const result = await upstream<Array<{ unlocked: boolean; retry_after_seconds: number }>>(
            "/rest/v1/rpc/verify_profile_pin", headers, { p_profile_id: actor.profile_index, p_pin: pin },
        );
        if (result?.[0]?.unlocked !== true) {
            return { unlocked: false, retry_after_seconds: result?.[0]?.retry_after_seconds ?? 0 };
        }
        profiles = await profilesFor(owner, headers);
    }

    const result = await database({
        owner,
        caller_session: claims.session_id,
        viewer_profile_id: actor.id,
        verified_profiles: profiles.map(({ id, pin_enabled, updated_at }) => ({ id, pin_enabled, updated_at })),
        operation: body.operation,
        payload: body.operation === "unlock" ? {} : payload,
        proof: body.proof ?? null,
    });
    return body.operation === "list" && Array.isArray(result)
        ? await addWatchedStatus(result, profiles, actor, headers)
        : result;
}

export async function serve(request: Request): Promise<Response> {
    try {
        return Response.json(await handle(request));
    } catch (error) {
        if (error instanceof RequestError) return Response.json({ code: error.code }, { status: error.status });
        return Response.json({ code: "D12_SERVICE_UNAVAILABLE" }, { status: 502 });
    }
}

if (import.meta.main) Deno.serve(serve);
