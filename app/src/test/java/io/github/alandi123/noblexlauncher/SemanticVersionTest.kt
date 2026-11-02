package io.github.alandi123.noblexlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    private fun v(text: String) = SemanticVersion.parse(text)!!

    @Test
    fun parsesOnlyStrictVersions() {
        listOf("0.1.0", "1.2.3", "99.99.99", "1.0.0-rc.1", "1.0.0-beta.12", "1.0.0-alpha.1").forEach {
            assertNotNull(it, SemanticVersion.parse(it))
        }
        listOf("", "1", "1.2", "01.2.3", "1.2.3.4", "v1.2.3", "1.2.3-rc", "1.2.3-rc.0", "1.2.3-snapshot.1",
            "1.2.3+build", "100.0.0", "1.2.3-rc.99", " 1.2.3").forEach {
            assertNull(it, SemanticVersion.parse(it))
        }
    }

    @Test
    fun versionCodeFollowsSemanticOrdering() {
        val ordered = listOf(
            "0.1.0-alpha.1", "0.1.0-alpha.2", "0.1.0-beta.1", "0.1.0-rc.1", "0.1.0-rc.2", "0.1.0",
            "0.1.1-rc.1", "0.1.1", "0.2.0", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0",
        ).map(::v)
        ordered.zipWithNext().forEach { (lower, higher) ->
            assertTrue("$lower < $higher", lower < higher)
            assertTrue(lower.toVersionCode() < higher.toVersionCode())
        }
    }

    @Test
    fun knownVersionCodes() {
        assertEquals(1_020_399L, v("1.2.3").toVersionCode())
        assertEquals(10_099L, v("0.1.0").toVersionCode())
        assertEquals(10_067L, v("0.1.0-rc.1").toVersionCode())
        assertTrue(v("0.1.0-rc.1").isPreRelease)
    }
}
