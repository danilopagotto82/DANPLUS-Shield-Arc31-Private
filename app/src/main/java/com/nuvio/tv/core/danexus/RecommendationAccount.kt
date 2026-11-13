package com.nuvio.tv.core.danexus

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class RecommendationAccount @Inject constructor(@ApplicationContext context: Context) {
    internal val configured = BuildConfig.DANPLUS_D12_SUPABASE_URL.matches(
        Regex("https://[a-z0-9-]+\\.supabase\\.co/?")
    ) && BuildConfig.DANPLUS_D12_SUPABASE_KEY.startsWith("sb_publishable_")

    private val settings = ProtectedProfilePreferences(context, "danplus_indications_session", "danplus.indications.session.v1")
    private val client by lazy {
        check(configured)
        createSupabaseClient(BuildConfig.DANPLUS_D12_SUPABASE_URL, BuildConfig.DANPLUS_D12_SUPABASE_KEY) {
            httpEngine = OkHttp.create {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                }
            }
            install(Auth) {
                enableLifecycleCallbacks = false
                sessionManager = object : SessionManager {
                    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
                        settings.write(0, Json.encodeToString(UserSession.serializer(), session))
                    }

                    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
                        settings.read(0)?.let { Json.decodeFromString(UserSession.serializer(), it) }
                    }

                    override suspend fun deleteSession() = withContext(Dispatchers.IO) { settings.write(0, null) }
                }
            }
            install(Postgrest)
        }
    }
    private val auth: Auth get() = client.auth
    internal val postgrest: Postgrest get() = client.postgrest
    internal val owner: String? get() = if (configured) auth.currentUserOrNull()?.id else null
    internal val email: String? get() = if (configured) auth.currentUserOrNull()?.email else null
    internal val sessionStatus = if (configured) auth.sessionStatus else MutableStateFlow(SessionStatus.NotAuthenticated())
    internal val changes = sessionStatus.map { owner }.distinctUntilChanged()

    internal suspend fun signIn(address: String, secret: String, create: Boolean): Boolean {
        if (create) {
            auth.signUpWith(Email) { email = address; password = secret }
        } else {
            auth.signInWith(Email) { email = address; password = secret }
        }
        return owner != null
    }

    internal suspend fun signOut() = auth.signOut()

    internal suspend fun profiles(owner: String): List<RecommendationProfile> {
        check(this.owner == owner)
        return postgrest.rpc("sync_pull_profiles").decodeList()
    }

    internal suspend fun createProfile(owner: String, name: String, color: String, pin: String?): String {
        check(this.owner == owner)
        return postgrest.rpc("d12_create_profile", buildJsonObject {
            put("p_name", name.take(60))
            put("p_avatar_color", color)
            pin?.let { put("p_pin", it) }
        }).decodeAs()
    }
}
