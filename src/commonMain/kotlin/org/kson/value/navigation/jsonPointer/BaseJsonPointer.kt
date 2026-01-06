package org.kson.value.navigation.jsonPointer

import org.kson.value.navigation.jsonPointer.PointerParser.*

/**
 * Abstract base class for JSON Pointer implementations.
 *
 * Provides common functionality for both JsonPointer (RFC 6901) and JsonPointerGlob (with wildcards/patterns).
 *
 * @property pointerString The pointer string representation
 */
abstract class BaseJsonPointer(parser: PointerParser) {
    /**
     * The pointer string representation
     */
    val pointerString: String = parser.pointerString

    /**
     * The parsed reference tokens as ParsedToken objects.
     * Can be Literal, Wildcard, or GlobPattern tokens.
     * Empty list for root pointer ("").
     */
    val rawTokens: List<Tokens>

    /**
     * The parsed reference tokens as strings.
     * This is a convenience property that converts tokens to their string representation:
     * - Literal -> the literal value
     * - Wildcard -> "*"
     * - RecursiveDescent -> "**"
     * - GlobPattern -> the pattern string
     * Empty list for root pointer ("").
     */
    val tokens: List<String>
        get() = rawTokens.map {
            when (it) {
                is Tokens.Literal -> it.value
                is Tokens.Wildcard -> "*"
                is Tokens.RecursiveDescent -> "**"
                is Tokens.GlobPattern -> it.pattern
            }
        }

    init {
        when (val result = parser.parse()) {
            is ParseResult.Success -> {
                rawTokens = result.tokens
            }
            is ParseResult.Error -> {
                // Determine the pointer type name for error message
                val pointerTypeName = when (parser) {
                    is JsonPointerParser -> "JSON Pointer"
                    is JsonPointerGlobParser -> "JsonPointerGlob"
                    else -> "pointer"
                }
                throw IllegalArgumentException(
                    "Invalid $pointerTypeName '$pointerString': ${result.message}"
                )
            }
        }
    }

    override fun toString(): String = pointerString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BaseJsonPointer
        return pointerString == other.pointerString
    }

    override fun hashCode(): Int {
        return pointerString.hashCode()
    }
}
