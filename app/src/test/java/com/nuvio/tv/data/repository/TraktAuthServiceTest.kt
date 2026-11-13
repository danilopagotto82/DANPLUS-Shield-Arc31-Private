package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.data.local.AuthSessionNoticeDataStore
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRefreshTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRevokeRequestDto
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TraktAuthServiceTest {
    @Test
    fun `device authorization refresh and revoke send the public client id only`() {
        val moshi = Moshi.Builder().build()

        val poll = moshi.adapter(TraktDeviceTokenRequestDto::class.java)
            .toJson(TraktDeviceTokenRequestDto("device-code", "public-client"))
        val refresh = moshi.adapter(TraktRefreshTokenRequestDto::class.java)
            .toJson(TraktRefreshTokenRequestDto("refresh-token", "public-client", "urn:ietf:wg:oauth:2.0:oob"))
        val revoke = moshi.adapter(TraktRevokeRequestDto::class.java)
            .toJson(TraktRevokeRequestDto("access-token", "public-client"))

        val json = moshi.adapter(Map::class.java)
        assertEquals(mapOf("code" to "device-code", "client_id" to "public-client"), json.fromJson(poll))
        assertEquals(
            mapOf(
                "refresh_token" to "refresh-token",
                "client_id" to "public-client",
                "redirect_uri" to "urn:ietf:wg:oauth:2.0:oob",
                "grant_type" to "refresh_token"
            ),
            json.fromJson(refresh)
        )
        assertEquals(mapOf("token" to "access-token", "client_id" to "public-client"), json.fromJson(revoke))
    }

    @Test
    fun `refresh token 400 clears credentials and prevents another refresh`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        val authSessionNoticeDataStore = mockk<AuthSessionNoticeDataStore>()
        var authState = authenticatedState()

        coEvery { traktAuthDataStore.getCurrentState() } answers { authState }
        coEvery { traktAuthDataStore.clearAuth() } answers { authState = TraktAuthState() }
        coEvery { authSessionNoticeDataStore.markTraktReconnectRequired() } returns Unit
        coEvery { traktApi.refreshToken(any()) } returns Response.error(400, "invalid_grant".toResponseBody())

        val service = spyk(TraktAuthService(
            context = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = authSessionNoticeDataStore
        ))
        every { service.hasRequiredCredentials() } returns true

        assertFalse(service.refreshTokenIfNeeded(force = true))
        assertEquals(2_000L, currentTime)
        assertFalse(service.refreshTokenIfNeeded(force = true))

        // A rejected refresh is retried once, two seconds later, before the credentials are cleared.
        coVerify(exactly = 2) { traktApi.refreshToken(any()) }
        coVerify(exactly = 1) { authSessionNoticeDataStore.markTraktReconnectRequired() }
        coVerify(exactly = 1) { traktAuthDataStore.clearAuth() }
    }

    @Test
    fun `a request without a Trakt account is skipped and the reason is kept once`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        coEvery { traktAuthDataStore.getCurrentState() } returns TraktAuthState()
        val service = TraktAuthService(
            context = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = mockk(relaxed = true)
        )

        repeat(3) {
            assertNull(service.executeAuthorizedRequest { header -> traktApi.getLastActivities(header) })
        }

        assertEquals(setOf("not signed in"), service.reportedFailureKinds())
        coVerify(exactly = 0) { traktApi.getLastActivities(any()) }
    }

    private fun authenticatedState(): TraktAuthState {
        return TraktAuthState(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "bearer",
            createdAt = 1L,
            expiresIn = 3600
        )
    }
}
