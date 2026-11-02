package io.github.alandi123.noblexlauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUrlPolicyTest {
    @Test
    fun redirectsOnlyToGithubAssetHosts() {
        assertTrue(ReleaseUrlPolicy.isAllowedRedirect("https://objects.githubusercontent.com/github-production-release-asset/abc"))
        assertTrue(ReleaseUrlPolicy.isAllowedRedirect("https://release-assets.githubusercontent.com/x?y=z"))
        assertFalse(ReleaseUrlPolicy.isAllowedRedirect("http://objects.githubusercontent.com/x"))
        assertFalse(ReleaseUrlPolicy.isAllowedRedirect("https://githubusercontent.com.evil.example/x"))
        assertFalse(ReleaseUrlPolicy.isAllowedRedirect("https://user:pw@github.com/x"))
        assertFalse(ReleaseUrlPolicy.isAllowedRedirect("https://github.com:8443/x"))
        assertFalse(ReleaseUrlPolicy.isAllowedRedirect("file:///sdcard/x.apk"))
        assertFalse(ReleaseUrlPolicy.isAllowedRedirect("not a url"))
    }

    @Test
    fun officialAssetMustMatchRepositoryTagAndFileExactly() {
        val ok = "https://github.com/AlanDi123/noblex-launcher/releases/download/v1.0.0/update-manifest.json"
        assertTrue(ReleaseUrlPolicy.isOfficialAsset(ok, "v1.0.0", "update-manifest.json"))
        assertFalse(ReleaseUrlPolicy.isOfficialAsset(ok, "v1.0.1", "update-manifest.json"))
        assertFalse(ReleaseUrlPolicy.isOfficialAsset("$ok#frag", "v1.0.0", "update-manifest.json"))
        assertFalse(ReleaseUrlPolicy.isOfficialAsset(ok.replace("noblex-launcher", "noblex-launcher2"), "v1.0.0", "update-manifest.json"))
    }
}
