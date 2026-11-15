
import { reportEmailMatches, reportEmailVerification } from "/support-auth.mjs";

const states = {
    received: "Recebido", read: "Lido", cause_found: "Causa encontrada",
    fixed: "Corrigido", not_danplus: "Não causado pelo DanPlus", closed: "Fechado",
};
const kinds = { bug: "Problema", idea: "Sugestão", crash: "Travamento", playback: "Reprodução", auth: "Conta" };
const get = selector => document.querySelector(selector);
const form = get("#sign-in");
const verification = get("#email-verification");
const emailLogin = get("#email-login");
const filters = get("#filters");
const update = get("#update");
const notice = get("#notice");
const queue = get("#queue");
const next = get("#next");
const detail = get("#detail");
let token, settings, cursor, current, mutation, verificationEmail;
let revision = 0;
let queueRequest = 0;
let detailRequest = 0;

for (const select of document.querySelectorAll("[data-status]")) {
    for (const [value, label] of Object.entries(states)) select.add(new Option(label, value));
}

const formatTime = value => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("pt-BR");
};

async function request(path, body, accessToken) {
    const response = await fetch(settings.url + path, {
        method: "POST", redirect: "error", signal: AbortSignal.timeout(15000),
        headers: {
            apikey: settings.key,
            "Content-Type": "application/json",
            ...(accessToken ? { Authorization: "Bearer " + accessToken } : {}),
        },
        body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error("HTTP " + response.status);
    return response.status === 204 ? null : response.json();
}

const rpc = (name, body = {}) => request("/rest/v1/rpc/" + name, body, token);

function clearSession() {
    const previous = token;
    token = undefined;
    revision++;
    queueRequest++;
    detailRequest++;
    current = cursor = mutation = undefined;
    queue.replaceChildren();
    get("#events").replaceChildren();
    for (const id of ["detail-title", "protocol", "summary", "meta", "diagnostic"]) get("#" + id).textContent = "";
    update.reset();
    form.reset();
    verification.reset();
    verificationEmail = undefined;
    verification.hidden = true;
    form.hidden = false;
    get("#workspace").hidden = detail.hidden = get("#sign-out").hidden = true;
    if (previous) request("/auth/v1/logout?scope=local", {}, previous).catch(() => {});
}

async function loadQueue(append = false) {
    const own = ++queueRequest;
    const page = await rpc("danplus_support_queue", {
        p_protocol: filters.elements.protocol.value.trim() || null,
        p_kind: filters.elements.kind.value || null,
        p_status: filters.elements.status.value || null,
        p_before_time: append ? cursor?.received_at ?? null : null,
        p_before_id: append ? cursor?.id ?? null : null,
    });
    if (own !== queueRequest) return;
    if (!append) queue.replaceChildren();
    for (const item of page) {
        const row = document.createElement("li");
        const button = document.createElement("button");
        button.type = "button";
        button.dataset.id = item.id;
        if (item.id === current?.id) button.setAttribute("aria-current", "true");
        button.textContent = item.summary || kinds[item.kind] || item.kind;
        const metadata = document.createElement("small");
        metadata.textContent = [kinds[item.kind] ?? item.kind, states[item.status], formatTime(item.received_at)].filter(Boolean).join(" · ");
        button.append(metadata);
        row.append(button);
        queue.append(row);
    }
    cursor = page.at(-1);
    next.hidden = page.length < 50;
    get("#empty").hidden = queue.children.length > 0;
}

function showThread(thread) {
    current = thread;
    detail.hidden = false;
    for (const button of queue.querySelectorAll("button")) {
        if (button.dataset.id === thread.id) button.setAttribute("aria-current", "true");
        else button.removeAttribute("aria-current");
    }
    get("#detail-title").textContent = kinds[thread.kind] ?? thread.kind;
    get("#protocol").textContent = thread.id;
    get("#summary").textContent = thread.summary ?? "";
    get("#meta").textContent = [formatTime(thread.received_at), thread.app_version, thread.device_model].filter(Boolean).join(" · ");
    update.reset();
    update.elements.status.value = thread.status;
    get("#diagnostic").textContent = thread.diagnostic == null ? "" : JSON.stringify(thread.diagnostic, null, 2);
    detail.querySelector("details").open = false;
    update.elements.reply.disabled = !thread.can_reply;
    get("#reply-label").hidden = !thread.can_reply;
    get("#legacy").hidden = thread.can_reply;
    const events = get("#events");
    events.replaceChildren();
    for (const entry of thread.events) {
        const row = document.createElement("li");
        row.textContent = [formatTime(entry.created_at), entry.status && states[entry.status]].filter(Boolean).join(" · ");
        for (const [label, value] of [["Resposta", entry.reply], ["Nota interna", entry.note]]) {
            if (!value) continue;
            const text = document.createElement("p");
            text.textContent = label + ": " + value;
            row.append(text);
        }
        events.append(row);
    }
}

function showError(error) {
    if (error.message === "HTTP 401" || error.message === "HTTP 403") {
        clearSession();
        notice.textContent = "Sessão expirada ou conta sem acesso administrativo.";
    } else {
        notice.textContent = "Não foi possível concluir agora. Tente novamente.";
    }
}

async function loadSettings() {
    if (settings) return;
    const response = await fetch("/support-config", { cache: "no-store" });
    if (!response.ok) throw new Error("Configuração indisponível");
    settings = await response.json();
}

async function openPanel(session, email, own) {
    if (own !== revision) return;
    if (!session.access_token || !reportEmailMatches(session, email)) {
        throw new Error("ACCOUNT_MISMATCH");
    }
    token = session.access_token;
    form.elements.password.value = "";
    await loadQueue();
    if (own !== revision) return;
    verification.reset();
    verificationEmail = undefined;
    verification.hidden = form.hidden = true;
    get("#workspace").hidden = get("#sign-out").hidden = false;
    notice.textContent = "";
}

function loginError(error, method) {
    const wasSignedIn = Boolean(token);
    clearSession();
    if (wasSignedIn && error.message === "HTTP 403") {
        notice.textContent = "A conta entrou, mas não tem acesso administrativo.";
    } else if (!wasSignedIn && (error.message === "HTTP 400" || error.message === "HTTP 401" ||
                                 error.message === "ACCOUNT_MISMATCH")) {
        notice.textContent = method === "password"
            ? "E-mail ou senha não aceitos. Use Entrar por e-mail se não lembrar a senha."
            : "Código ou link inválido, expirado ou já usado. Peça um novo e-mail.";
    } else {
        notice.textContent = "Não foi possível conectar ao painel agora. Tente novamente.";
    }
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    const own = ++revision;
    const button = form.querySelector('button:not([type="button"])');
    button.disabled = emailLogin.disabled = true;
    const email = form.elements.email.value.trim();
    try {
        await loadSettings();
        const session = await request("/auth/v1/token?grant_type=password", {
            email, password: form.elements.password.value,
        });
        await openPanel(session, email, own);
    } catch (error) {
        if (own === revision) {
            loginError(error, "password");
            form.elements.email.value = email;
        }
    } finally { button.disabled = emailLogin.disabled = false; }
});

emailLogin.addEventListener("click", async () => {
    if (!form.elements.email.reportValidity()) return;
    const own = ++revision;
    const button = form.querySelector('button:not([type="button"])');
    button.disabled = emailLogin.disabled = true;
    const email = form.elements.email.value.trim();
    try {
        await loadSettings();
        await request("/auth/v1/otp", { email, create_user: false });
        if (own !== revision) return;
        verificationEmail = email;
        form.elements.password.value = "";
        form.hidden = true;
        verification.hidden = false;
        verification.elements.verification.focus();
        notice.textContent = "Confira sua caixa de entrada e o spam.";
    } catch (error) {
        if (own === revision) notice.textContent = error.message === "HTTP 429"
            ? "Aguarde antes de pedir outro e-mail."
            : "Não foi possível enviar o e-mail de acesso. Confira o endereço e tente novamente.";
    } finally { button.disabled = emailLogin.disabled = false; }
});

get("#email-back").addEventListener("click", () => {
    if (verification.querySelector("button").disabled) return;
    revision++;
    verificationEmail = undefined;
    verification.reset();
    verification.hidden = true;
    form.hidden = false;
    notice.textContent = "";
    form.elements.email.focus();
});

verification.addEventListener("submit", async event => {
    event.preventDefault();
    const email = verificationEmail;
    if (!email || !settings) return;
    const body = reportEmailVerification(verification.elements.verification.value, email, settings.url);
    if (!body) {
        notice.textContent = "Copie o código ou o link original enviado ao seu e-mail.";
        return;
    }
    const own = ++revision;
    const button = verification.querySelector("button");
    button.disabled = true;
    try {
        const session = await request("/auth/v1/verify", body);
        await openPanel(session, email, own);
    } catch (error) {
        if (own === revision) {
            loginError(error, "email");
            form.elements.email.value = email;
        }
    } finally { button.disabled = false; }
});

get("#sign-out").addEventListener("click", () => {
    clearSession();
    notice.textContent = "Sessão encerrada.";
});

filters.addEventListener("submit", async event => {
    event.preventDefault();
    const own = revision;
    try { await loadQueue(); } catch (error) { if (own === revision) showError(error); }
});

next.addEventListener("click", async () => {
    const own = revision;
    next.disabled = true;
    try { await loadQueue(true); } catch (error) { if (own === revision) showError(error); }
    finally { next.disabled = false; }
});

queue.addEventListener("click", async event => {
    const button = event.target.closest("button[data-id]");
    if (!button || update.querySelector("button").disabled) return;
    const own = revision;
    const selection = ++detailRequest;
    try {
        const thread = await rpc("danplus_support_thread", { p_id: button.dataset.id });
        if (own !== revision || selection !== detailRequest) return;
        mutation = undefined;
        showThread(thread);
        get("#detail-title").focus();
        notice.textContent = "";
    } catch (error) { if (own === revision && selection === detailRequest) showError(error); }
});

update.addEventListener("submit", async event => {
    event.preventDefault();
    if (!current) return;
    const own = revision;
    const fields = {
        p_id: current.id,
        p_status: update.elements.status.value === current.status ? null : update.elements.status.value,
        p_reply: current.can_reply ? update.elements.reply.value.trim() || null : null,
        p_note: update.elements.note.value.trim() || null,
    };
    if (!fields.p_status && !fields.p_reply && !fields.p_note) return;
    const key = JSON.stringify(fields);
    if (mutation?.key !== key) mutation = { key, id: crypto.randomUUID() };
    const button = update.querySelector("button");
    button.disabled = true;
    try {
        const thread = await rpc("danplus_support_update", { ...fields, p_request: mutation.id });
        if (own !== revision) return;
        mutation = undefined;
        showThread(thread);
        notice.textContent = "Alteração salva.";
        try { await loadQueue(); } catch { if (own === revision) notice.textContent = "Alteração salva. Atualize a lista pelo filtro."; }
    } catch (error) { if (own === revision) showError(error); }
    finally { button.disabled = false; }
});
