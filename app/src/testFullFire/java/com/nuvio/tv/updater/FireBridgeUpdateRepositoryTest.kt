package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.GitHubReleaseApi
import com.nuvio.tv.data.remote.dto.GitHubAssetDto
import com.nuvio.tv.data.remote.dto.GitHubReleaseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FireBridgeUpdateRepositoryTest {
    private val api = mockk<GitHubReleaseApi>()
    private val repo = UpdateRepository(api)
    private val futureRepo = "DanPlus"
    private val owner = "danilopagotto82"

    private fun release(name: String): GitHubReleaseDto {
        val url = "https://github.com/$owner/$futureRepo/releases/download/2.0.2/$name"
        return GitHubReleaseDto(
            tagName = "2.0.2",
            prerelease = true,
            body = "<!-- nuvio-fork-version-code: ${BuildConfig.VERSION_CODE + 1L} -->",
            assets = listOf(GitHubAssetDto(name, url, 42))
        )
    }

    @Test fun `fire production bridge accepts new name after legacy feed has no update`() = runTest {
        assertEquals("com.danexus.tv.fire.debug", BuildConfig.APPLICATION_ID)
        val assetName = "DanPlus-FireTV-2.0.2-armeabi-v7a.apk"
        coEvery { api.getReleases(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO, 100, 1) } returns Response.success(emptyList())
        coEvery { api.getReleases(owner, futureRepo, 100, 1) } returns Response.success(listOf(release(assetName)))
        val update = repo.getLatestUpdate(listOf("armeabi-v7a", "armeabi")).getOrThrow()
        assertEquals(assetName, update.assetName)
        assertEquals((BuildConfig.VERSION_CODE + 1L), update.versionCode)
        coVerify(exactly = 1) { api.getReleases(owner, futureRepo, 100, 1) }
    }

    @Test fun `fire bridge does not conceal forbidden response from legacy origin`() = runTest {
        coEvery { api.getReleases(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO, 100, 1) } returns Response.error(403, "forbidden".toResponseBody())
        assertTrue(repo.getLatestUpdate(listOf("armeabi-v7a")).isFailure)
        coVerify(exactly = 0) { api.getReleases(owner, futureRepo, 100, 1) }
    }
}
