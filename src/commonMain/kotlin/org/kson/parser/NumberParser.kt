package org.kson.parser

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * Note that our number parsing exactly follows JSON's grammar (see https://www.json.org):
 *
 * number -> integer fraction exponent
 * integer -> digit
 *          | oneToNine digits
 *          | "-" digit
 *          | "-" oneToNine digits
 * digits -> digit
 *         | digit digits
 * digit -> "0" | oneToNine
 * oneToNine -> "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
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
    private var hasDecimalPoint = false
    private var hasExponent = false

    sealed class ParsedNumber {
        abstract val asString: String

        /**
         * Get this [ParsedNumber] as a [Double]
         */
        val asDouble: Double by lazy {
            when (this) {
                is Decimal -> value
                is Integer -> value.toDouble()
            }
        }

        class Integer(rawString: String) : ParsedNumber() {
            override val asString = rawString
            val value = convertToLong(asString)
        }

        class Decimal(rawString: String) : ParsedNumber() {
            // normalize exponent marker `E` to `e`
            override val asString = rawString.replace('E', 'e')
            val value: Double by lazy {
                asString.toDouble()
            }
        }
    }

    data class NumberParseResult(
        /**
         * The parsed number, or `null` if the string number candidate was invalid (in which case [error] will be set)
         */
        val number: ParsedNumber?,
        /**
         * If we fail to parse to a number, [number] will be `null` and this [error] property will contain the
         * error [Message] describing why the number parsing failed
         */
        val error: Message?
    )

    fun parse(): NumberParseResult {
        return if (number()) {
            val result = if (!hasDecimalPoint && !hasExponent) {
                try {
                    ParsedNumber.Integer(numberCandidate)
                } catch (_: IntegerOverflowException) {
                    // The exception type already carries the semantic — we convert it to a parser-level message
                    return NumberParseResult(null,
                        MessageType.INTEGER_OVERFLOW.create(numberCandidate))
                }
            } else {
                ParsedNumber.Decimal(numberCandidate)
            }
            NumberParseResult(result, null)
        } else {
            if (error == null) {
                /**
                 * A null `error` here indicates a bug in this [NumberParser]
                 */
                throw ShouldNotHappenException("must always set `error` message for a failed parse")
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
     * integer -> digit
     *          | oneToNine digits
     *          | "-" digit
     *          | "-" oneToNine digits
     */
    private fun integer(): Boolean {
        val minusSignFound = scanner.peek() == '-'
        if (minusSignFound) {
            scanner.advanceScanner()
        }

        // We need to explicitly check for leading zeros before letting digits() do its work
        if (scanner.peek() == '0') {
            scanner.advanceScanner()
            if (isDigit()) {
                error = MessageType.LEADING_ZEROS.create()
                return false
            }
            return true
        }

        if (digits()) {
            return true
        }

        error = if (minusSignFound) {
            MessageType.ILLEGAL_MINUS_SIGN.create()
        } else {
            MessageType.INVALID_DIGITS.create(scanner.peek().toString())
        }
        return false
    }

    /**
     * digits -> digit
     *         | digit digits
     */
    private fun digits(): Boolean {
        var digitsFound = false
        while (isDigit()) {
            scanner.advanceScanner()
            digitsFound = true
        }

        return digitsFound
    }

    /**
     * digit -> "0" | oneToNine
     */
    private fun isDigit(): Boolean {
        return scanner.peek() == '0' || isOneToNine()
    }

    /**
     * oneToNine -> "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
     */
    private fun isOneToNine(): Boolean {
        return oneToNine.contains(scanner.peek())
    }

    private val oneToNine = setOf(
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
            hasDecimalPoint = true
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
            hasExponent = true
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

private fun convertToLong(numberString: String): Long {
    try {
        return numberString.toLong()
    } catch (_: NumberFormatException) {
        /**
         * Our number grammar should ensure this is an integer, though it won't catch massive numbers
         * that are our of range.  If this [NumberFormatException] is anything other than an out-of-range
         * error, that indicates a bug in this [NumberParser]
         */
        throw IntegerOverflowException("Invalid long: $numberString")
    }
}

/**
 * TODO We should be able to fall back to a BigInteger in these cases and not error anymore
 */
private class IntegerOverflowException(message: String) : NumberFormatException(message)
