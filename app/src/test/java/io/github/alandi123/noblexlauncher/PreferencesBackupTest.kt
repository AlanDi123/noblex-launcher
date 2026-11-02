package io.github.alandi123.noblexlauncher

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PreferencesBackupTest {
    private val allowed = LauncherPreferences.Companion::isBackedUpKey

    private val sample = mapOf<String, Any>(
        "favorites" to setOf("com.netflix.ninja", "com.spotify.tv.android"),
        "hidden" to setOf("com.homwee.help"),
        "order" to "a\nb\nc",
        "custom_categories" to "Mis series",
        "category_order" to "Mis series\nStreaming",
        "category.com.netflix.ninja" to "Mis series",
        "wallpaper_dim" to 25,
    )

    @Test
    fun roundTripPreservesEveryEntryAndTheWallpaper() {
        val jpeg = byteArrayOf(1, 2, 3, 4, 5)
        val text = PreferencesBackup.encode(sample, "io.github.alandi123.noblexlauncher.debug", "0.2.0", 1L, jpeg)
        val decoded = PreferencesBackup.decode(text, allowed)
        assertEquals(sample, decoded.entries)
        assertArrayEquals(jpeg, decoded.wallpaper)
        assertEquals("io.github.alandi123.noblexlauncher.debug", decoded.sourcePackage)
    }

    @Test
    fun noWallpaperMeansNullAndOversizedWallpaperIsNotEmbedded() {
        val none = PreferencesBackup.decode(PreferencesBackup.encode(sample, "p", "v", 1L, null), allowed)
        assertNull(none.wallpaper)
        val big = ByteArray(PreferencesBackup.MAX_WALLPAPER_BYTES + 1)
        assertNull(PreferencesBackup.decode(PreferencesBackup.encode(sample, "p", "v", 1L, big), allowed).wallpaper)
    }

    @Test
    fun keysOutsideTheWhitelistAreDropped() {
        val text = PreferencesBackup.encode(sample + ("last_release" to "{}") + ("wallpaper_uri" to "file:///x"), "p", "v", 1L, null)
        val decoded = PreferencesBackup.decode(text, allowed)
        assertFalse("last_release" in decoded.entries)
        assertFalse("wallpaper_uri" in decoded.entries)
        assertEquals(sample, decoded.entries)
    }

    @Test
    fun invalidFilesAreRejectedWithAReadableError() {
        listOf(
            "not json",
            "{}",
            """{"format":"other","version":1}""",
            """{"format":"noblex-launcher-backup","version":99}""",
            """{"format":"noblex-launcher-backup","version":1,"strings":{"order":5}}""",
        ).forEach { text ->
            try {
                PreferencesBackup.decode(text, allowed)
                fail("accepted: $text")
            } catch (expected: IOException) {
                assertTrue(expected.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun backupKeyWhitelist() {
        assertTrue(LauncherPreferences.isBackedUpKey("favorites"))
        assertTrue(LauncherPreferences.isBackedUpKey("category.any.package"))
        assertFalse(LauncherPreferences.isBackedUpKey("last_release"))
        assertFalse(LauncherPreferences.isBackedUpKey("wallpaper_uri"))
    }
}
