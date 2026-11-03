package io.github.alandi123.noblexlauncher

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class UpdateVerifierTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val release = LauncherRelease(
        versionName = "1.2.3", versionCode = 1_020_399L, minSdk = 28,
        apkUrl = "https://github.com/AlanDi123/noblex-launcher/releases/download/v1.2.3/noblex-launcher-1.2.3.apk",
        sha256 = "a".repeat(64), sizeBytes = 10, releaseDate = "", releaseNotes = "", mandatory = false,
        supportedAbis = listOf("universal"),
    )

    @Test
    fun checksumMatchesKnownVectorAndRejectsWrongHash() {
        val file = folder.newFile().apply { writeText("abc") }
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, UpdateVerifier.sha256(file))
        UpdateVerifier.requireChecksum(file, expected.uppercase())
        assertThrows(IOException::class.java) { UpdateVerifier.requireChecksum(file, "0".repeat(64)) }
    }

    @Test
    fun rejectsSizeMismatchesAndTruncatedDownloads() {
        UpdateVerifier.requireDeclaredSize(-1, 10)
        UpdateVerifier.requireDeclaredSize(10, 10)
        assertThrows(IOException::class.java) { UpdateVerifier.requireDeclaredSize(11, 10) }
        assertThrows(IOException::class.java) { UpdateVerifier.requireDownloadedSize(11, 10) }
        assertThrows(IOException::class.java) { UpdateVerifier.requireComplete(9, 10) }
        UpdateVerifier.requireComplete(10, 10)
    }

    @Test
    fun rejectsDifferentOrMissingSigningCertificate() {
        val good = UpdateVerifier.digest(byteArrayOf(1, 2, 3))
        val other = UpdateVerifier.digest(byteArrayOf(9, 9, 9))
        UpdateVerifier.requireSameSigner(setOf(good), setOf(other, good))
        assertThrows(IOException::class.java) { UpdateVerifier.requireSameSigner(setOf(good), setOf(other)) }
        assertThrows(IOException::class.java) { UpdateVerifier.requireSameSigner(emptySet(), emptySet()) }
    }

    @Test
    fun rejectsWrongPackageOrVersionInsideApk() {
        val id = "io.github.alandi123.noblexlauncher"
        UpdateVerifier.requireIdentity(id, 1_020_399L, "1.2.3", id, release)
        assertThrows(IOException::class.java) { UpdateVerifier.requireIdentity("evil.app", 1_020_399L, "1.2.3", id, release) }
        assertThrows(IOException::class.java) { UpdateVerifier.requireIdentity(id, 1_020_398L, "1.2.3", id, release) }
        assertThrows(IOException::class.java) { UpdateVerifier.requireIdentity(id, 1_020_399L, "1.2.4", id, release) }
        assertThrows(IOException::class.java) { UpdateVerifier.requireIdentity(null, 1_020_399L, null, id, release) }
    }

    @Test
    fun policyNeverOffersOlderOrEqualVersions() {
        assertEquals(UpdateDecision.UP_TO_DATE, UpdatePolicy.decide(release, "1.2.3", 1_020_399L))
        assertEquals(UpdateDecision.UP_TO_DATE, UpdatePolicy.decide(release, "2.0.0", 2_000_000L))
        assertEquals(UpdateDecision.AVAILABLE, UpdatePolicy.decide(release, "1.2.2", 1_020_299L))
    }

    @Test
    fun malformedOrIncompleteMetadataFailsInsteadOfBeingAccepted() {
        assertThrows(JSONException::class.java) { JSONObject("{not json") }
        val incomplete = JSONObject().put("applicationId", "io.github.alandi123.noblexlauncher")
        assertThrows(JSONException::class.java) {
            UpdateManifestParser.parse(incomplete, "v1.2.3", "io.github.alandi123.noblexlauncher", 30, listOf("armeabi-v7a"))
        }
    }
}
