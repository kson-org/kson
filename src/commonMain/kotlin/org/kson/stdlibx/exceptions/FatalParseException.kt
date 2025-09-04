package org.kson.stdlibx.exceptions

/**
 * An exception for code paths that are fatal if hit during parsing.
 *
 * These exceptions are caught and result in a null-AST
 *
 * @param message the reason why we believe that it is unexpected to hit this code path
 */
class FatalParseException(message: String) : RuntimeException(message)