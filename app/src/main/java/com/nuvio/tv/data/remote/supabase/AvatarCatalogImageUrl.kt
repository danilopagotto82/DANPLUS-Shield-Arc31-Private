package com.nuvio.tv.data.remote.supabase

/**
 * The official build may omit AVATAR_PUBLIC_BASE_URL while still providing SUPABASE_URL.
 * Resolve public catalog artwork against the same Storage bucket used by custom servers.
 * Keep absolute URLs unchanged; never synthesize a relative URL from an invalid backend.
 */
internal fun avatarCatalogImageUrl(
    storagePath: String,
    configuredBaseUrl: String?,
    backendUrl: String
): String {
    val path = storagePath.trim().trimStart('/')
    if (path.startsWith("https://") || path.startsWith("http://")) return path

    val configured = configuredBaseUrl?.trim()?.trimEnd('/').orEmpty()
    val backend = backendUrl.trim().trimEnd('/')
    val baseUrl = configured.ifBlank {
        backend.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let { "$it/storage/v1/object/public/avatars" }
            .orEmpty()
    }
    return if (baseUrl.isNotEmpty()) "$baseUrl/$path" else path
}
