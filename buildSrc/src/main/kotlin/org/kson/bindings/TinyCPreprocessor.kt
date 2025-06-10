package org.kson.bindings

import java.io.File
import java.io.FileWriter
import java.util.Stack

class TinyCPreprocessor {
    fun preprocess(sourcePath: String, targetPath: String) {
        val targetFile = FileWriter(targetPath, false)
        val scopes = Stack<Boolean>()
        File(sourcePath).forEachLine {
            // Handle directives
            if (it.startsWith("#define")) {
                // We don't handle defines
                return@forEachLine
            } else if (it.startsWith("#ifndef")) {
                // Nothing is ever defined, so this will always be true
                scopes.push(true)
                return@forEachLine
            } else if (it.startsWith("#ifdef")) {
                // Nothing is ever defined, so this will always be false
                scopes.push(false)
                return@forEachLine
            } else if (it.startsWith("#else")) {
                // Flip the scope's include/exclude flag
                if (scopes.isNotEmpty()) {
                    scopes.push(!scopes.pop())
                }
                return@forEachLine
            } else if (it.startsWith("#endif")) {
                // Exit scope
                if (scopes.isNotEmpty()) {
                    scopes.pop()
                }
                return@forEachLine
            }

            // Remove unsupported `__attribute__` usage
            if (it.contains("typedef float __attribute__")) {
                return@forEachLine
            }

            if (scopes.isEmpty() || scopes.peek()) {
                targetFile.append(it)
                targetFile.append("\n")
            }
        }
        targetFile.flush()
    }
}