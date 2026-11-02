package io.github.alandi123.noblexlauncher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class UpdateManifestParserTest {
    private val appId = "io.github.alandi123.noblexlauncher"

    private fun parse(
        manifest: JSONObject = manifest(),
        tag: String = "v1.2.3",
        id: String = appId,
        sdk: Int = 30,
        abis: List<String> = listOf("armeabi-v7a"),
    ) = UpdateManifestParser.parse(manifest, tag, id, sdk, abis)

    @Test
    fun acceptsManifestForMatchingReleaseAndDevice() {
        val release = parse()
        assertEquals(1_020_399L, release.versionCode)
        assertEquals("1.2.3", release.versionName)
        assertEquals(1_000_000L, release.sizeBytes)
    }

    @Test
    fun rejectsMismatchedIdentityOrReleaseTag() {
        assertThrows(IOException::class.java) { parse(id = "other.application") }
        assertThrows(IOException::class.java) { parse(tag = "v1.2.4") }
    }

    @Test
    fun rejectsIncompatibleSdkAbiAndChecksum() {
        assertThrows(IOException::class.java) { parse(manifest(minSdk = 31)) }
        assertThrows(IOException::class.java) { parse(manifest(supportedAbis = listOf("x86_64"))) }
        assertThrows(IOException::class.java) { parse(manifest(sha256 = "bad")) }
    }

    @Test
    fun rejectsInvalidSemanticVersionOrInconsistentVersionCode() {
        assertThrows(IOException::class.java) { parse(manifest(version = "1.2"), tag = "v1.2") }
        assertThrows(IOException::class.java) { parse(manifest(version = "01.2.3"), tag = "v01.2.3") }
        assertThrows(IOException::class.java) { parse(manifest(versionCode = 999_999_999L)) }
    }

    @Test
    fun rejectsApkUrlOutsideTheOfficialRelease() {
        val base = "https://github.com/AlanDi123/noblex-launcher/releases/download/v1.2.3"
        listOf(
            "https://evil.example/AlanDi123/noblex-launcher/releases/download/v1.2.3/noblex-launcher-1.2.3.apk",
            "http://github.com/AlanDi123/noblex-launcher/releases/download/v1.2.3/noblex-launcher-1.2.3.apk",
            "$base/other.apk",
            "https://github.com/someone/noblex-launcher/releases/download/v1.2.3/noblex-launcher-1.2.3.apk",
            "https://github.com@evil.example/AlanDi123/noblex-launcher/releases/download/v1.2.3/noblex-launcher-1.2.3.apk",
            "$base/noblex-launcher-1.2.3.apk?x=1",
        ).forEach { url ->
            assertThrows(url, IOException::class.java) { parse(manifest(apkUrl = url)) }
        }
    }

    @Test
    fun rejectsMissingOrAbsurdSize() {
        assertThrows(IOException::class.java) { parse(manifest(sizeBytes = 0)) }
        assertThrows(IOException::class.java) { parse(manifest(sizeBytes = UpdatePolicy.MAX_APK_BYTES + 1)) }
        assertThrows(org.json.JSONException::class.java) { parse(manifest().apply { remove("sizeBytes") }) }
    }

    @Test
    fun acceptsPrereleaseWithDerivedVersionCode() {
        val version = SemanticVersion.parse("1.3.0-rc.2")!!
        val release = parse(
            manifest(version = "1.3.0-rc.2", versionCode = version.toVersionCode(), apkUrl = url("v1.3.0-rc.2", "1.3.0-rc.2")),
            tag = "v1.3.0-rc.2",
        )
        assertEquals("1.3.0-rc.2", release.versionName)
    }

    private fun url(tag: String = "v1.2.3", version: String = "1.2.3") =
        "https://github.com/AlanDi123/noblex-launcher/releases/download/$tag/noblex-launcher-$version.apk"

    private fun manifest(
        minSdk: Int = 28,
        supportedAbis: List<String> = listOf("universal"),
        sha256: String = "a".repeat(64),
        version: String = "1.2.3",
        versionCode: Long = 1_020_399L,
        apkUrl: String = url(),
        sizeBytes: Long = 1_000_000L,
    ) = JSONObject()
        .put("applicationId", appId)
        .put("version", version)
        .put("versionCode", versionCode)
        .put("minSdk", minSdk)
        .put("supportedAbis", JSONArray(supportedAbis))
        .put("apkUrl", apkUrl)
        .put("sha256", sha256)
        .put("sizeBytes", sizeBytes)
}
