package io.github.alandi123.noblexlauncher

import java.net.URI
import java.net.URISyntaxException

/** Decides which network locations the updater is allowed to talk to. */
object ReleaseUrlPolicy {
    const val REPOSITORY = "AlanDi123/noblex-launcher"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"

    private val REDIRECT_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun apkFileName(version: String) = "noblex-launcher-$version.apk"

    /** True only for the exact asset URL of the official repository for [tag]. */
    fun isOfficialAsset(address: String, tag: String, fileName: String): Boolean {
        val uri = parseHttps(address) ?: return false
        return uri.host == "github.com" &&
            uri.rawQuery == null &&
            uri.rawPath == "/$REPOSITORY/releases/download/$tag/$fileName"
    }

    /** Redirect hops GitHub uses to serve release assets. */
    fun isAllowedRedirect(address: String): Boolean {
        val uri = parseHttps(address) ?: return false
        return uri.host in REDIRECT_HOSTS
    }

    private fun parseHttps(address: String): URI? =
        try {
            URI(address).takeIf {
                it.scheme == "https" && it.rawUserInfo == null && it.port == -1 &&
                    it.rawFragment == null && it.host != null
            }
        } catch (_: URISyntaxException) {
            null
        }
}
