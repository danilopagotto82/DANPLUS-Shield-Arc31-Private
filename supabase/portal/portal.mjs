const form = document.querySelector("#sign-in");
const confirmation = document.querySelector("#confirmation");
const status = document.querySelector("#status");
const approve = document.querySelector("#approve");
const cancel = document.querySelector("#cancel");
const legacy = location.pathname === "/tv-login";
const originalCode = new URL(location.href).searchParams.get("code");
if (originalCode) {
    form.elements.code.value = originalCode;
    history.replaceState(null, "", location.pathname);
}

let config;
let session;
let code;

async function post(path, payload, accessToken) {
    const response = await fetch(config.url + path, {
        method: "POST",
        redirect: "error",
        headers: {
            apikey: config.key,
            "Content-Type": "application/json",
            ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
        body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error("Request failed");
    return response.status === 204 ? null : response.json();
}

async function finish(message) {
    const accessToken = session?.access_token;
    session = undefined;
    code = undefined;
    confirmation.hidden = true;
    form.reset();
    if (originalCode) form.elements.code.value = originalCode;
    status.textContent = message;
    if (accessToken) {
        await post("/auth/v1/logout?scope=local", {}, accessToken).catch(() => {});
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = form.querySelector("button");
    button.disabled = true;
    code = form.elements.code.value.trim();
    try {
        if (!code) throw new Error("Missing code");
        if (!config) {
            const response = await fetch("/runtime-config");
            if (!response.ok) throw new Error("Configuration unavailable");
            config = await response.json();
        }
        const { access_token } = await post("/auth/v1/token?grant_type=password", {
            email: form.elements.email.value.trim(),
            password: form.elements.password.value,
        });
        session = access_token ? { access_token } : undefined;
        if (!session?.access_token) throw new Error("Missing session");
        form.elements.password.value = "";
        if (!legacy) {
            const [device] = await post("/rest/v1/rpc/get_device_login_session",
                { p_user_code: code }, session.access_token);
            if (device?.success !== true) throw new Error("Code unavailable");
            document.querySelector("#device").textContent = device.device_name ?? "";
        }
        document.querySelector("#code").textContent = code;
        form.hidden = true;
        confirmation.hidden = false;
        status.textContent = "";
        approve.focus();
    } catch {
        await finish("Não foi possível entrar ou localizar esse código. Confira os dados e tente novamente.");
        form.hidden = false;
    } finally {
        button.disabled = false;
    }
});

approve.addEventListener("click", async () => {
    if (!session?.access_token || !code || approve.disabled) return;
    approve.disabled = true;
    cancel.disabled = true;
    try {
        const path = legacy ? "approve_tv_login_session" : "approve_device_login_session";
        const input = legacy ? { p_code: code } : { p_user_code: code };
        const [result] = await post("/rest/v1/rpc/" + path, input, session.access_token);
        if (result?.success !== true) throw new Error("Approval failed");
        await finish("TV aprovada. Volte à tela da TV para concluir.");
    } catch {
        await finish("O código não pôde ser aprovado. Atualize o QR na TV e tente novamente.");
        form.hidden = false;
    } finally {
        approve.disabled = false;
        cancel.disabled = false;
    }
});

cancel.addEventListener("click", async () => {
    await finish("Vinculação cancelada.");
    form.hidden = false;
});
