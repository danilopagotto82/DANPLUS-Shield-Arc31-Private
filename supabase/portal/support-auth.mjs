export function reportEmailVerification(value, email, origin) {
    const input = value.trim();
    if (/^[0-9]{6,10}$/.test(input)) return { email, token: input, type: "email" };
    let url;
    try { url = new URL(input); } catch { return null; }
    if (url.origin !== origin || url.username || url.password || url.pathname !== "/auth/v1/verify") return null;
    const types = url.searchParams.getAll("type");
    if (types.length !== 1 || !["magiclink", "email"].includes(types[0])) return null;
    const hashes = [...url.searchParams.getAll("token"), ...url.searchParams.getAll("token_hash")];
    if (hashes.length !== 1 || !/^[A-Za-z0-9_-]{20,256}$/.test(hashes[0])) return null;
    return { token_hash: hashes[0], type: "email" };
}

export function reportEmailMatches(result, email) {
    return typeof result?.user?.email === "string" &&
        result.user.email.trim().toLowerCase() === email.trim().toLowerCase();
}
