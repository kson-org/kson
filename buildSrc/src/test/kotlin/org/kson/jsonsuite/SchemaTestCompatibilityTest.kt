package org.kson.jsonsuite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Checks [SchemaTestCompatibility] against the examples in the JSON-Schema-Test-Suite's `validation/README.md`
 */
class SchemaTestCompatibilityTest {
    private val releases = listOf(3, 4, 6, 7, 2019, 2020)

    private fun compatibleReleases(compatibility: String) =
        releases.filter { SchemaTestCompatibility.parse(compatibility).admits(it) }

    @Test
    fun anAbsentCompatibilityAppliesToEveryRelease() {
        assertEquals(releases, releases.filter { SchemaTestCompatibility.ALL.admits(it) })
    }

    @Test
    fun aBareVersionIsAMinimum() {
        assertEquals(listOf(7, 2019, 2020), compatibleReleases("7"))
    }

    @Test
    fun lessThanOrEqualIsAMaximum() {
        assertEquals(listOf(3, 4, 6, 7, 2019), compatibleReleases("<=2019"))
    }

    @Test
    fun equalsIsASingleRelease() {
        assertEquals(listOf(2020), compatibleReleases("=2020"))
    }

    @Test
    fun aMinimumAndMaximumIsARange() {
        assertEquals(listOf(6, 7, 2019), compatibleReleases("6,<=2019"))
    }

    @Test
    fun constraintsCombineAsADisjunction() {
        assertEquals(listOf(3, 7, 2019, 2020), compatibleReleases("=3,7,<=2020"))
        assertEquals(listOf(3, 7, 2019, 2020), compatibleReleases("<=3,7"))
    }

    @Test
    fun anUnreleasedVersionExcludesEveryRelease() {
        assertEquals(emptyList(), compatibleReleases("9999"))
        assertEquals(listOf(4, 6), compatibleReleases("4,<=6,9999"))
    }

    @Test
    fun aMalformedConstraintIsRejected() {
        assertFailsWith<IllegalArgumentException> { SchemaTestCompatibility.parse(">=7") }
    }
}
