import { createServer } from "node:http";
import { readFile } from "node:fs/promises";

const backend = new URL(process.env.DANPLUS_SUPABASE_URL ?? "");
const key = process.env.DANPLUS_SUPABASE_PUBLIC_KEY?.trim() ?? "";
const legacyAnon = (() => {
    try {
        const parts = key.split(".");
        return parts.length === 3 && JSON.parse(Buffer.from(parts[1], "base64url")).role === "anon";
    } catch {
        return false;
    }
})();
if (backend.protocol !== "https:" || backend.username || backend.password ||
    backend.pathname !== "/" || backend.search || backend.hash ||
    (!/^sb_publishable_[a-zA-Z0-9_-]+$/.test(key) && !legacyAnon)) {
    throw new Error("Configure an HTTPS Supabase origin and a public key");
}

const files = new Map([
    ["/link", ["text/html; charset=utf-8", await readFile(new URL("./index.html", import.meta.url))]],
    ["/portal.mjs", ["text/javascript; charset=utf-8", await readFile(new URL("./portal.mjs", import.meta.url))]],
    ["/runtime-config", ["application/json; charset=utf-8", JSON.stringify({ url: backend.origin, key })]],
]);
files.set("/tv-login", files.get("/link"));

const server = createServer((request, response) => {
    const path = request.url?.split("?", 1)[0];
    const entry = request.method === "GET" ? files.get(path) : undefined;
    response.writeHead(entry ? 200 : 404, {
        "Content-Type": entry?.[0] ?? "text/plain; charset=utf-8",
        "Cache-Control": "no-store",
        "Referrer-Policy": "no-referrer",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy": `default-src 'none'; script-src 'self'; connect-src 'self' ${backend.origin}; base-uri 'none'; frame-ancestors 'none'; form-action 'none'`,
    });
    response.end(entry?.[1] ?? "Página indisponível.");
});

server.listen(Number(process.env.DANPLUS_PORTAL_PORT ?? 8080), "127.0.0.1");
