package com.nuvio.tv.core.danexus

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.ServerConfiguration
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class RecommendationProfile(
    val id: String,
    @SerialName("profile_index") val index: Int,
    val name: String,
    @SerialName("avatar_color_hex") val avatarColorHex: String,
    @SerialName("pin_enabled") val pinEnabled: Boolean
)

@Singleton
class RecommendationAccount @Inject constructor(
    private val authManager: AuthManager,
    private val auth: Auth,
    private val server: ServerConfiguration
) {
    internal val configured = !server.isCustom && server.backendUrl.trimEnd('/') == "https://api.nuvio.tv" &&
        BuildConfig.DANPLUS_D12_SUPABASE_URL.matches(Regex("https://[a-z0-9-]+\\.supabase\\.co/?")) &&
        BuildConfig.DANPLUS_D12_SUPABASE_KEY.startsWith("sb_publishable_")

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    internal val json = Json { ignoreUnknownKeys = true }
    internal val owner: String?
        get() = if (configured) authManager.currentUserId else null
    internal val email: String?
        get() = if (configured) (authManager.authState.value as? AuthState.FullAccount)?.email else null
    internal val changes = authManager.authState.map { if (configured) (it as? AuthState.FullAccount)?.userId else null }
        .distinctUntilChanged()

    internal suspend fun resolveOwner(user: String): String {
        check(owner == user)
        return checkNotNull(authManager.getEffectiveUserId(fallbackToOwnIdOnFailure = false))
    }

    internal suspend fun request(
        user: String,
        operation: String,
        profileId: String? = null,
        payload: JsonObject = buildJsonObject {},
        proof: String? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        check(configured && owner == user)
        val session = checkNotNull(auth.currentSessionOrNull())
        check(session.user?.id == user)
        val body = buildJsonObject {
            put("operation", operation)
            profileId?.let { put("profile_id", it) }
            put("payload", payload)
            proof?.let { put("proof", it) }
        }
        val request = Request.Builder()
            .url(BuildConfig.DANPLUS_D12_SUPABASE_URL.trimEnd('/') + "/functions/v1/nuvio-indications")
            .header("apikey", BuildConfig.DANPLUS_D12_SUPABASE_KEY)
            .header("X-Nuvio-Publishable-Key", server.publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            check(owner == user)
            val data = json.parseToJsonElement(checkNotNull(response.body).string())
            if (!response.isSuccessful) {
                val code = (data as? JsonObject)?.get("code")?.jsonPrimitive?.content
                error(code ?: "D12_HTTP_${response.code}")
            }
            data
        }
    }

    internal inline fun <reified T> decode(data: JsonElement): T =
        json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), data)

    internal suspend fun profiles(user: String): List<RecommendationProfile> =
        decode(request(user, "profiles"))
}
