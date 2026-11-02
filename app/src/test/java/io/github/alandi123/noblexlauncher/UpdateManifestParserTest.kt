package io.github.alandi123.noblexlauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class UpdateManifestParserTest {
    @Test
    fun acceptsManifestForMatchingReleaseAndDevice() {
        val release = UpdateManifestParser.parse(
            manifest(),
            "v1.2.3",
            "io.github.alandi123.noblexlauncher",
            30,
            listOf("armeabi-v7a"),
        )

        assertEquals(123L, release.versionCode)
        assertEquals("1.2.3", release.versionName)
    }

    @Test
    fun rejectsMismatchedIdentityOrReleaseTag() {
        assertThrows(IOException::class.java) {
            UpdateManifestParser.parse(
                manifest(),
                "v1.2.3",
                "other.application",
                30,
                listOf("armeabi-v7a"),
            )
        }
        assertThrows(IOException::class.java) {
            UpdateManifestParser.parse(
                manifest(),
                "v1.2.4",
                "io.github.alandi123.noblexlauncher",
                30,
                listOf("armeabi-v7a"),
            )
        }
    }

    @Test
    fun rejectsIncompatibleSdkAbiAndChecksum() {
        assertThrows(IOException::class.java) {
            UpdateManifestParser.parse(
                manifest(minSdk = 31),
                "v1.2.3",
                "io.github.alandi123.noblexlauncher",
                30,
                listOf("armeabi-v7a"),
            )
        }
        assertThrows(IOException::class.java) {
            UpdateManifestParser.parse(
                manifest(supportedAbis = listOf("x86_64")),
                "v1.2.3",
                "io.github.alandi123.noblexlauncher",
                30,
                listOf("armeabi-v7a"),
            )
        }
        assertThrows(IOException::class.java) {
            UpdateManifestParser.parse(
                manifest(sha256 = "bad"),
                "v1.2.3",
                "io.github.alandi123.noblexlauncher",
                30,
                listOf("armeabi-v7a"),
            )
        }
    }

    private fun manifest(
        minSdk: Int = 28,
        supportedAbis: List<String> = listOf("universal"),
        sha256: String = "a".repeat(64),
    ) = JSONObject()
        .put("applicationId", "io.github.alandi123.noblexlauncher")
        .put("version", "1.2.3")
        .put("versionCode", 123)
        .put("minSdk", minSdk)
        .put("supportedAbis", org.json.JSONArray(supportedAbis))
        .put("apkUrl", "https://github.com/AlanDi123/noblex-launcher/releases/download/v1.2.3/app-release.apk")
        .put("sha256", sha256)
}
