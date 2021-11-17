package org.kson

actual fun readResourceAsString(path: String): String {
    val inputStream = ClassLoader.getSystemResourceAsStream(path)
    if (inputStream != null) {
        return String(inputStream.readAllBytes())
    } else {
        throw IllegalArgumentException("unable to read resource at $path")
    }
}