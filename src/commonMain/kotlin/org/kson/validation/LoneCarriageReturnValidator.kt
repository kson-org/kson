package org.kson.validation

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.behavior.Line
import org.kson.parser.messages.MessageType.LONE_CARRIAGE_RETURN

/**
 * Warns if there is a \r without accompanying \n. This is likely a mistake, and potentially rendered inconsistently
 * in some terminals/editors.
 *
 * JSON allows this, so it's merely a warning (and still a valid KSON document).
 */
class LoneCarriageReturnValidator {
    fun validate(source: String, messageSink: MessageSink) {
        var lineStartOffset = 0
        Line.splitToLines(source).forEachIndexed { lineNumber, line ->
            // any \r that's not part of Line.ending is likely a mistake
            line.text.forEachIndexed { column, char ->
                if (char == '\r') {
                    val offset = lineStartOffset + column
                    messageSink.error(
                        Location.create(lineNumber, column, lineNumber, column + 1, offset, offset + 1),
                        LONE_CARRIAGE_RETURN.create()
                    )
                }
            }
            lineStartOffset += line.text.length + line.ending.length
        }
    }
}
