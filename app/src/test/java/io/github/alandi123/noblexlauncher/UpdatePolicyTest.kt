package io.github.alandi123.noblexlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class UpdatePolicyTest {
    private fun release(version: String) = LauncherRelease(
        versionName = version,
        versionCode = SemanticVersion.parse(version)!!.toVersionCode(),
        minSdk = 28,
        apkUrl = "",
        sha256 = "",
        sizeBytes = 1,
        releaseDate = "",
        releaseNotes = "",
        mandatory = false,
        supportedAbis = listOf("universal"),
    )

    @Test
    fun offersOnlyStrictlyNewerVersionCodes() {
        val installed = SemanticVersion.parse("1.0.0")!!.toVersionCode()
        assertEquals(UpdateDecision.AVAILABLE, UpdatePolicy.decide(release("1.0.1"), "1.0.0", installed))
        assertEquals(UpdateDecision.UP_TO_DATE, UpdatePolicy.decide(release("1.0.0"), "1.0.0", installed))
        assertEquals(UpdateDecision.UP_TO_DATE, UpdatePolicy.decide(release("0.9.0"), "1.0.0", installed))
    }

    @Test
    fun prereleaseDoesNotReplaceStableOfSameVersion() {
        val installed = SemanticVersion.parse("1.0.0")!!.toVersionCode()
        assertEquals(UpdateDecision.UP_TO_DATE, UpdatePolicy.decide(release("1.0.0-rc.3"), "1.0.0", installed))
    }

    @Test
    fun rejectsReleaseWhoseCodeIsHigherButVersionIsNot() {
        val forged = release("1.0.0").copy(versionCode = 99_999_999L)
        assertThrows(IOException::class.java) { UpdatePolicy.decide(forged, "1.0.0", 1_000_099L) }
    }
}
