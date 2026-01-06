package org.kson.value

import org.kson.parser.Location

/**
 * Interface for [KsonValue]s whose values may be sub-parsed by external parsers
 */
interface SubParseable {
    /**
     * Convert an offset range within this [SubParseable] [KsonValue] into a [org.kson.parser.Location] in the original
     * KSON source
     */
    fun subOffsetLocation(subStartOffset: Int, subEndOffset: Int): Location

    /**
     * Convert a line/column coordinate range within this [SubParseable] [KsonValue] into a [Location] in the original
     * KSON source
     */
    fun subCoordinatesLocation(subStartLine: Int, subStartColumn: Int,
                               subEndLine: Int, subEndColumn: Int,): Location
}
