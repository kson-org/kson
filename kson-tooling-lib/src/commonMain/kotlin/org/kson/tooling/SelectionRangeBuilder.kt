package org.kson.tooling

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.value.EmbedBlock
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonNull
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import kotlin.collections.iterator

/**
 * Builds a list of enclosing [Range]s for a given cursor position in KSON source.
 *
 * Walks the [KsonValue] AST to collect all ranges that contain the cursor,
 * from innermost to outermost. Used for smart expand/shrink selection.
 */
internal object SelectionRangeBuilder {

    fun build(ksonValue: KsonValue, line: Int, column: Int): List<Range> {
        val ancestors = mutableListOf<Range>()
        collectAncestors(ksonValue, Coordinates(line, column), ancestors)
        return deduplicate(ancestors)
    }

    /**
     * Recursively collect all AST node ranges that contain the given position,
     * from innermost to outermost.
     */
    private fun collectAncestors(value: KsonValue, cursor: Coordinates, ancestors: MutableList<Range>): Boolean {
        if (!Location.containsCoordinates(value.location, cursor)) {
            return false
        }

        val range = value.toRange()

        when (value) {
            is KsonObject -> {
                for ((_, prop) in value.propertyMap) {
                    val keyLoc = prop.propName.location
                    val valueLoc = prop.propValue.location

                    val propertyRange = Range(
                        keyLoc.start.line, keyLoc.start.column,
                        valueLoc.end.line, valueLoc.end.column
                    )

                    if (containsPosition(propertyRange, cursor)) {
                        val descendedIntoValue = collectAncestors(prop.propValue, cursor, ancestors)

                        if (!descendedIntoValue) {
                            // Cursor is on the key itself
                            val keyRange = prop.propName.toRange()
                            if (containsPosition(keyRange, cursor)) {
                                ancestors.add(keyRange)
                            }
                        }

                        ancestors.add(propertyRange)
                        ancestors.add(range)
                        return true
                    }
                }
            }

            is KsonList -> {
                for (element in value.elements) {
                    if (collectAncestors(element, cursor, ancestors)) {
                        ancestors.add(range)
                        return true
                    }
                }
            }

            is EmbedBlock,
            is KsonString,
            is KsonNumber,
            is KsonBoolean,
            is KsonNull -> {}

        }

        // Leaf node or cursor is on container delimiters (e.g. { or })
        ancestors.add(range)
        return true
    }

    private fun deduplicate(ranges: List<Range>): List<Range> {
        if (ranges.isEmpty()) return ranges
        val result = mutableListOf(ranges[0])
        for (i in 1 until ranges.size) {
            if (ranges[i] != result.last()) {
                result.add(ranges[i])
            }
        }
        return result
    }

    private fun containsPosition(range: Range, cursor: Coordinates): Boolean {
        if (cursor.line < range.startLine || cursor.line > range.endLine) return false
        if (cursor.line == range.startLine && cursor.column < range.startColumn) return false
        if (cursor.line == range.endLine && cursor.column > range.endColumn) return false
        return true
    }

}
