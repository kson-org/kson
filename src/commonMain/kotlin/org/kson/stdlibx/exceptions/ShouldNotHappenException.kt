package org.kson.stdlibx.exceptions

/**
 * An exception for code paths that should never be hit
 *
 * @param whyNot the reason why we believe that this code path should never be hit
 */
class ShouldNotHappenException(whyNot: String) : RuntimeException("This should not happen:\n$whyNot")