package org.kson.parser

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType

/**
 * Note that our number parsing closely follows JSON's grammar (see https://www.json.org), with one key difference:
 *    we allow leading zeros (which are ignored, value-wise) in numbers.
 *
 * number -> integer fraction exponent
 * integer -> digits
 *          | "-" digits
 * digits -> digit
 *         | digit digits
 * digit -> "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
 * fraction -> ""
 *           | "." digits
 * exponent -> ""
 *           | "E" sign digits
 *           | "e" sign digits
 * sign -> "" | "+" | "-"
 */
class NumberParser(private val numberCandidate: String) {
    private val scanner: Scanner = Scanner(numberCandidate)
    private var error: Message? = null

    data class NumberParseResult(
        /**
         * The parsed [Double], or null if the string number candidate was invalid (in which case [error] will be set)
         */
        val number: Double?,
        /**
         * If we fail to parse to a number, [number] will be `null` and this [error] property will contain the
         * error [Message] describing why the number parsing failed
         */
        val error: Message?
    )

    fun parse(): NumberParseResult {
        return if (number()) {
            NumberParseResult(
                /**
                 * Our number grammar ensures that all parseable strings can be safely converted to [Double].
                 * If this throws a [NumberFormatException], that indicates a bug in this [NumberParser]
                 */
                numberCandidate.toDouble(),
                null
            )
        } else {
            if (error == null) {
                /**
                 * A null `error` here indicates a bug in this [NumberParser]
                 */
                throw RuntimeException("must always set `error` message for a failed parse")
            }
            NumberParseResult(null, error)
        }
    }

    /**
     * A [Char] by [Char] [source] [Scanner]
     */
    private class Scanner(private val source: String) {
        private var currentSourceIndex = 0

        /**
         * Returns the next [Char] in this [Scanner],
         *   or `null` if there is no next character
         */
        fun peek(): Char? {
            if (eof()) {
                return null
            }
            return source[currentSourceIndex]
        }

        fun advanceScanner() {
            currentSourceIndex++
        }

        fun eof(): Boolean {
            return currentSourceIndex >= source.length
        }
    }

    /**
     * number -> integer fraction exponent
     */
    private fun number(): Boolean {
        if (integer() && fraction() && exponent()) {
            if (!scanner.eof()) {
                // we have unparsed trailing content: must be invalid characters
                error = MessageType.INVALID_DIGITS.create(scanner.peek().toString())
                return false
            }
            return true
        }

        return false
    }

    /**
     * integer -> digits
     *          | "-" digits
     */
    private fun integer(): Boolean {
        if (digits()) {
            return true
        }

        if (scanner.peek() == '-') {
            scanner.advanceScanner()
            return if (digits()) {
                true
            } else {
                error = MessageType.ILLEGAL_MINUS_SIGN.create()
                false
            }
        }

        error = MessageType.INVALID_DIGITS.create(scanner.peek().toString())
        return false
    }

    /**
     * digits -> digit
     *         | digit digits
     */
    private fun digits(): Boolean {
        var digitsFound = false
        while (digit()) {
            scanner.advanceScanner()
            digitsFound = true
        }

        return digitsFound
    }

    /**
     * digit -> "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
     */
    private fun digit(): Boolean {
        return zeroToNine.contains(scanner.peek())
    }

    private val zeroToNine = setOf(
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9'
    )

    /**
     * fraction -> ""
     *           | "." digits
     */
    private fun fraction(): Boolean {
        if (scanner.peek() == '.') {
            scanner.advanceScanner()
            return if (digits()) {
                true
            } else {
                error = MessageType.DANGLING_DECIMAL.create()
                false
            }
        }

        // fraction can be empty
        return true
    }

    /**
     * exponent -> ""
     *           | "E" sign digits
     *           | "e" sign digits
     */
    private fun exponent(): Boolean {
        val exponentChar = scanner.peek()
        if (exponentChar == 'E' || exponentChar == 'e') {
            scanner.advanceScanner()
            // parse optional sign
            sign()
            if (scanner.eof()) {
                error = MessageType.DANGLING_EXP_INDICATOR.create(exponentChar.toString())
                return false
            } else if (!digits()) {
                error = MessageType.INVALID_DIGITS.create(scanner.peek().toString())
                return false
            }
        }

        // exponent can be "" (i.e. empty), so this function always succeeds in "parsing"
        return true
    }

    /**
     * sign -> "" | "+" | "-"
     */
    private fun sign(): Boolean {
        if (scanner.peek() == '+' || scanner.peek() == '-') {
            scanner.advanceScanner()
        }

        return true
    }
}