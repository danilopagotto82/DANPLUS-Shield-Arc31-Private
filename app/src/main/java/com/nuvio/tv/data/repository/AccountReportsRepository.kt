package com.nuvio.tv.data.repository

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.build.IssueReports
import com.nuvio.tv.core.network.ReportInstallId
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.ServerConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import io.github.jan.supabase.auth.Auth
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class ReportAccountScope(val actor: String, val owner: String)

@Serializable
data class AccountReportReply(val text: String, val at: String)

@Serializable
data class AccountReport(
    val id: String,
    val kind: String,
    val status: String,
    @SerialName("received_at") val receivedAt: String,
    val replies: List<AccountReportReply> = emptyList()
)

@Singleton
class AccountReportsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val authManager: AuthManager,
    private val auth: Auth,
    private val server: ServerConfiguration
) {
    private val install = ReportInstallId(context)
    private val endpoint = IssueReports.danplusBaseUrl?.substringBeforeLast("/danplus-reports/")
        ?.plus("/danplus-report-account")
    private val enabled = endpoint != null && !server.isCustom &&
        server.backendUrl.trimEnd('/') == "https://api.nuvio.tv" &&
        BuildConfig.DANPLUS_D12_SUPABASE_KEY.startsWith("sb_publishable_")

    internal val accountChanges = authManager.authState.map {
        if (enabled) (it as? AuthState.FullAccount)?.userId else null
    }.distinctUntilChanged()
    internal val currentAccount: String?
        get() = authManager.currentUserId?.takeIf { enabled && auth.hasLiveAccount(it) && ACCOUNT_ID.matches(it) }

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).build()

    internal suspend fun submissionScope(): ReportAccountScope? {
        val actor = currentAccount ?: return null
        val owner = authManager.getEffectiveUserId(fallbackToOwnIdOnFailure = false)
            ?.takeIf(ACCOUNT_ID::matches) ?: return null
        return if (currentAccount == actor) ReportAccountScope(actor, owner) else null
    }

    internal suspend fun upload(account: ReportAccountScope, route: ReportRoute, payload: ByteArray): ReportUpload? =
        withContext(Dispatchers.IO) {
            try {
                val report = json.parseToJsonElement(payload.decodeToString()) as? JsonObject
                    ?: return@withContext ReportUpload.Refused(400)
                val body = buildJsonObject {
                    put("operation", "submit")
                    put("route", route.key)
                    put("install", install.get())
                    put("expected_owner", account.owner)
                    put("report", report)
                }
                request(account.actor, body) { code, response ->
                    when {
                        code == 404 -> null // Only a missing endpoint falls back to the existing device-only relay.
                        code == 401 || code == 429 -> ReportUpload.Unreachable("HTTP " + code)
                        code in 200..299 -> {
                            val receipt = json.decodeFromString<Receipt>(response)
                            if (!ACCOUNT_ID.matches(receipt.reportId)) ReportUpload.Unreachable("Invalid receipt")
                            else ReportUpload.Accepted(receipt.reportId, null, accountBound = true)
                        }
                        else -> reportUploadResult(code, false, null, null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReportUpload.Unreachable("Account report unavailable")
            }
        }

    internal suspend fun list(account: String): List<AccountReport> = withContext(Dispatchers.IO) {
        request(account, buildJsonObject { put("operation", "list") }) { code, response ->
            check(code == 200)
            json.decodeFromString<List<AccountReport>>(response)
        }
    }

    private fun Auth.hasLiveAccount(account: String) = currentSessionOrNull()?.user?.id == account

    private fun <T> request(account: String, payload: JsonObject, result: (Int, String) -> T): T {
        check(currentAccount == account)
        val session = checkNotNull(auth.currentSessionOrNull())
        check(session.user?.id == account)
        val request = Request.Builder().url(checkNotNull(endpoint))
            .header("apikey", BuildConfig.DANPLUS_D12_SUPABASE_KEY)
            .header("X-Nuvio-Publishable-Key", server.publishableKey)
            .header("Authorization", "Bearer " + session.accessToken)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { response ->
            check(currentAccount == account)
            result(response.code, response.body?.string().orEmpty())
        }
    }

    @Serializable
    private data class Receipt(val reportId: String)

    private companion object {
        val ACCOUNT_ID = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}
