package io.github.alandi123.noblexlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 10L * 24 * 60 * 60 * 1000

    private fun file(cache: UpdateCache, name: String, ageMs: Long) =
        File(cache.directory, name).apply {
            writeText("x")
            setLastModified(now - ageMs)
        }

    @Test
    fun removesStaleFilesAndKeepsAFreshNewerApk() {
        val cache = UpdateCache(folder.newFolder("updates"))
        val keep = file(cache, "noblex-launcher-200.apk", 1_000)
        val old = file(cache, "noblex-launcher-100.apk", 1_000)
        val same = file(cache, "noblex-launcher-150.apk", 1_000)
        val oldPart = file(cache, "noblex-launcher-300.apk.part", 2 * 60 * 60 * 1000)
        val freshPart = file(cache, "noblex-launcher-301.apk.part", 1_000)
        val unknown = file(cache, "something-else.bin", 1_000)
        val ancient = file(cache, "noblex-launcher-400.apk", 8L * 24 * 60 * 60 * 1000)

        val removed = cache.cleanup(installedVersionCode = 150, now = now)

        assertTrue(keep.exists())
        assertTrue(freshPart.exists())
        listOf(old, same, unknown, ancient, oldPart).forEach { assertFalse(it.name, it.exists()) }
        assertEquals(5, removed)
    }

    @Test
    fun cleanupOnMissingDirectoryIsHarmless() {
        assertEquals(0, UpdateCache(File(folder.root, "missing")).cleanup(1))
    }
}
