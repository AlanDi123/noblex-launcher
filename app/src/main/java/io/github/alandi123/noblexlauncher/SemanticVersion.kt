package io.github.alandi123.noblexlauncher

/**
 * Strict subset of Semantic Versioning used by NOBLEX LAUNCHER releases:
 * MAJOR.MINOR.PATCH with an optional pre-release of the form alpha.N, beta.N or rc.N.
 * Each component is limited so that [toVersionCode] is strictly monotonic with semver ordering.
 */
class SemanticVersion private constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    private val preReleaseSlot: Int,
) : Comparable<SemanticVersion> {
    val isPreRelease: Boolean get() = preReleaseSlot != STABLE_SLOT

    fun toVersionCode(): Long =
        ((major * 10_000L) + (minor * 100L) + patch) * 100L + preReleaseSlot

    override fun compareTo(other: SemanticVersion): Int = toVersionCode().compareTo(other.toVersionCode())

    override fun equals(other: Any?): Boolean = other is SemanticVersion && toVersionCode() == other.toVersionCode()

    override fun hashCode(): Int = toVersionCode().hashCode()

    companion object {
        private const val STABLE_SLOT = 99
        private const val SLOTS_PER_KIND = 33
        private val KINDS = listOf("alpha", "beta", "rc")
        private val PATTERN = Regex("""(0|[1-9]\d{0,1})\.(0|[1-9]\d{0,1})\.(0|[1-9]\d{0,1})(?:-(alpha|beta|rc)\.([1-9]\d?))?""")

        fun parse(text: String): SemanticVersion? {
            val match = PATTERN.matchEntire(text) ?: return null
            val (major, minor, patch, kind, number) = match.destructured
            val slot = if (kind.isEmpty()) {
                STABLE_SLOT
            } else {
                val n = number.toInt()
                if (n > SLOTS_PER_KIND - 1) return null
                KINDS.indexOf(kind) * SLOTS_PER_KIND + n
            }
            return SemanticVersion(major.toInt(), minor.toInt(), patch.toInt(), slot)
        }
    }
}
