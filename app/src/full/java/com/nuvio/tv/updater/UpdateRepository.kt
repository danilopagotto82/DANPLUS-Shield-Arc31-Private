package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.GitHubReleaseApi
import com.nuvio.tv.updater.model.AppUpdate
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

internal class NoEligibleUpdateException : IllegalStateException("No compatible fork update found")

internal data class UpdateOrigin(val owner: String, val repo: String) {
    fun accepts(asset: com.nuvio.tv.data.remote.dto.GitHubAssetDto): Boolean {
        val url = try { URI(asset.browserDownloadUrl) } catch (_: Exception) { return false }
        val path = url.rawPath ?: return false
        val parts = path.split("/")
        return url.scheme == "https" && url.host == "github.com" && url.rawAuthority == "github.com" && url.rawUserInfo == null &&
            url.port == -1 && url.rawQuery == null && url.rawFragment == null &&
            '%' !in path && parts.size == 7 && parts[0].isEmpty() &&
            parts[1] == owner && parts[2] == repo && parts[3] == "releases" &&
            parts[4] == "download" && parts[5].isNotEmpty() && parts[5] != "." && parts[5] != ".." &&
            parts[6] == asset.name && parts[6].isNotEmpty() &&
            (asset.size == null || asset.size > 0)
    }
}

@Singleton
class UpdateRepository @Inject constructor(private val gitHubReleaseApi: GitHubReleaseApi) {
    // Keep the legacy repository first for installed 2.0.0 clients and offline devices.
    // The new DanPlus repository is an additional origin, never a replacement for the legacy URL.
    private var origins = listOf(
        UpdateOrigin(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO),
        UpdateOrigin("danilopagotto82", "DanPlus")
    )

    internal constructor(api: GitHubReleaseApi, origins: List<UpdateOrigin>) : this(api) {
        require(origins.isNotEmpty())
        this.origins = origins.toList()
    }

    suspend fun getLatestUpdate(
        supportedAbis: List<String> =
            AbiSelector.installedFirst(android.os.Build.SUPPORTED_ABIS?.toList().orEmpty())
    ): Result<AppUpdate> = try {
        var failure: Exception? = null
        var checkedAtLeastOneOrigin = false
        var selected: Pair<com.nuvio.tv.data.remote.dto.GitHubReleaseDto, com.nuvio.tv.data.remote.dto.GitHubAssetDto>? = null
        for ((originIndex, origin) in origins.withIndex()) {
            val releases = mutableListOf<com.nuvio.tv.data.remote.dto.GitHubReleaseDto>()
            var missingFutureOrigin = false
            try {
                var page = 1
                do {
                    val response = gitHubReleaseApi.getReleases(owner = origin.owner, repo = origin.repo, page = page++)
                    if (response.code() == 404) {
                        // A future repository can be absent during OTA migration.
                        // A verified legacy feed still proves the installed version is current.
                        if (originIndex > 0 && checkedAtLeastOneOrigin) {
                            missingFutureOrigin = true
                            break
                        }
                        throw IOException("GitHub repository not found")
                    }
                    check(response.isSuccessful) { "GitHub API error: ${response.code()}" }
                    val batch = response.body() ?: throw IOException("Empty GitHub release response")
                    releases.addAll(batch)
                } while (response.headers()["Link"].orEmpty().contains("rel=\"next\""))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                failure = error
                continue
            }
            if (missingFutureOrigin) continue
            checkedAtLeastOneOrigin = true
            selected = ReleaseSelector.eligibleReleases(releases)
                .filter { VersionUtils.isRemoteNewer(it.tagName, BuildConfig.VERSION_NAME,
                    ReleaseSelector.versionCode(it), BuildConfig.VERSION_CODE.toLong()) }
                .firstNotNullOfOrNull { release ->
                    AbiSelector.chooseBestApkAsset(
                        release.assets, supportedAbis,
                        debugBuild = BuildConfig.IS_DEBUG_BUILD,
                        fireChannel = BuildConfig.APPLICATION_ID == "com.danexus.tv.fire.debug"
                    )?.takeIf(origin::accepts)?.let { release to it }
                }
            if (selected != null) break
        }
        val (dto, asset) = selected ?: throw (failure ?: NoEligibleUpdateException())
        Result.success(AppUpdate(
            tag = dto.tagName!!, title = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
            notes = dto.body.orEmpty().replace(Regex("""<!-- nuvio-fork-version-code: [0-9]+ -->"""), "").trim(),
            releaseUrl = dto.htmlUrl, assetName = asset.name, assetUrl = asset.browserDownloadUrl,
            assetSizeBytes = asset.size, versionCode = ReleaseSelector.versionCode(dto)
        ))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
