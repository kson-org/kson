package org.kson.value.navigation.jsonPointer

/**
 * Scanner for character-by-character processing of pointer strings.
 * Used by both [JsonPointerParser] and [JsonPointerGlobParser].
 */
class PointerScanner(private val source: String) {
    var currentIndex = 0
        private set

    /**
     * Get the current character without advancing
     * @return Current character or null if at end
     */
    fun peek(): Char? {
        return if (eof()) null else source[currentIndex]
    }

    /**
     * Advance to the next character
     */
    fun advance() {
        if (!eof()) {
            currentIndex++
        }
    }

    /**
     * Check if at end of string.
     *
     * @return true if no more characters to read, false otherwise
     */
    fun eof(): Boolean {
        return currentIndex >= source.length
    }
}
