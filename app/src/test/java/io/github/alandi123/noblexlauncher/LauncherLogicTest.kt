package io.github.alandi123.noblexlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherLogicTest {
    @Test
    fun categoryNamesAreTrimmedAndValidated() {
        assertEquals("Mis juegos", LauncherLogic.normalizeCategory("  Mis   juegos ", listOf("Streaming")))
        assertEquals(24, LauncherLogic.normalizeCategory("x".repeat(40), emptyList())!!.length)
        assertNull(LauncherLogic.normalizeCategory("   ", emptyList()))
        assertNull(LauncherLogic.normalizeCategory("streaming", listOf("Streaming")))
        listOf("Todas", "favoritos", "RECIENTES", "Entradas").forEach {
            assertNull(it, LauncherLogic.normalizeCategory(it, emptyList()))
        }
    }

    @Test
    fun swapUsesAvailableOrderWhenNothingWasSavedAndDropsUninstalledApps() {
        val available = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), LauncherLogic.swap(emptyList(), available, "a", "b"))
        assertEquals(listOf("a", "c", "b"), LauncherLogic.swap(listOf("gone", "a", "b"), available, "c", "b"))
    }

    @Test
    fun defaultCategoriesPreferKnownAppsOverTheSystemFlag() {
        assertEquals("Streaming", LauncherLogic.defaultCategory("com.netflix.ninja", system = true))
        assertEquals("Música", LauncherLogic.defaultCategory("com.spotify.tv.android", system = false))
        assertEquals("Juegos", LauncherLogic.defaultCategory("com.example.games.fun", system = false))
        assertEquals("Herramientas", LauncherLogic.defaultCategory("com.homwee.help", system = true))
        assertEquals("Sistema", LauncherLogic.defaultCategory("com.vendor.unknown", system = true))
        assertEquals("Entretenimiento", LauncherLogic.defaultCategory("com.user.installed", system = false))
    }

    @Test
    fun recentsAreMostRecentFirstWithoutDuplicatesAndBounded() {
        var recents = emptyList<String>()
        listOf("a", "b", "c", "a", "d").forEach { recents = LauncherLogic.recordRecent(recents, it, 3) }
        assertEquals(listOf("d", "a", "c"), recents)
    }
}
