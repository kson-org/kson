package org.kson.parser

import org.kson.parser.NumberParser.ParsedNumber
import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * Note that our number parsing closely follows JSON's grammar (see https://www.json.org), with one key difference:
 *  we allow leading zeros (which are ignored, value-wise) in numbers, though we do trim these leading zeros
 *  as part of our parse so that the string representation we retain is fully Json compatible (and more broadly
 *  compatible in general)—see [ParsedNumber.Integer.trimLeadingZeros] and [[ParsedNumber.Decimal.trimLeadingZeros]]
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
            override val asString = trimLeadingZeros(rawString)
            val value = convertToLong(rawString.trimStart('0').ifEmpty { "0" })

            fun trimLeadingZeros(input: String): String {
                // Handle negative numbers separately to preserve the minus sign
                if (input.startsWith("-")) {
                    val trimmed = input.substring(1).trimStart('0')
                    return "-" + (trimmed.ifEmpty { "0" })
                }
                
                // For positive numbers, just trim leading zeros
                val trimmed = input.trimStart('0')
                return if (trimmed.isEmpty()) "0" else trimmed
            }
        }

        class Decimal(rawString: String) : ParsedNumber() {
            override val asString = trimLeadingZeros(rawString)
            val value: Double by lazy {
                asString.toDouble()
            }

            /**
             * Note this handles leading zeros both on the integer part and
             * the exponent part
             */
            fun trimLeadingZeros(input: String): String {
                val isNegative = input.startsWith("-")
                val unsigned = if (isNegative) input.substring(1) else input

                val parts = unsigned.split('E', 'e')
                val mainPart = parts[0]
                val exponentPart = if (parts.size > 1) "e${parts[1]}" else ""

                val mainTrimmedPart = if (mainPart.contains('.')) {
                    val (intPart, fracPart) = mainPart.split('.')
                    val trimmedIntPart = intPart.trimStart('0').ifEmpty { "0" }
                    if (fracPart.isEmpty()) trimmedIntPart else "$trimmedIntPart.$fracPart"
                } else {
                    mainPart.trimStart('0').ifEmpty { "0" }
                }

                return buildString {
                    if (isNegative) append("-")
                    append(mainTrimmedPart)
                    if (exponentPart.isNotEmpty()) append(exponentPart)
                }
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
                } catch (e: IntegerOverflowException) {
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
    } catch (e: NumberFormatException) {
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
