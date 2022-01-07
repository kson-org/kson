package org.kson.jetbrains

import org.kson.Kson

/**
 * todo jetbrains delete this (this code is just sanity checking the parent project dependency came in as desired
 */
fun main() {
    val parseResult = Kson.parse("test: it")
    println(parseResult.ast?.toKsonSource())
}