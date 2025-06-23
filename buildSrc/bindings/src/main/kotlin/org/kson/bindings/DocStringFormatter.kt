package org.kson.bindings

class RustDocStringFormatter {
    companion object {
        fun format(indent: String, docString: String?): String {
            if (docString == null) {
                return ""
            }

            val builder = StringBuilder()
            val lines = docString.lines()
            lines.forEachIndexed { i, line ->
                if ((i == 0 || i == lines.size - 1) && line.trim().isEmpty()) {
                    return@forEachIndexed
                }

                builder.append(indent)
                builder.append("///")
                builder.append(line)
                builder.append("\n")
            }

            return builder.toString()
        }
    }
}

class PythonDocStringFormatter {
    companion object {
        fun format(indent: String, docString: String?): String {
            if (docString == null) {
                return ""
            }

            val tripleQuote = "\"\"\""

            val builder = StringBuilder()

            var lineCount = 0
            val lines = docString.lines()
            var firstNonEmptyLine = true
            lines.forEachIndexed { i, line ->
                if ((i == 0 || i == lines.size - 1) && line.isBlank()) {
                    return@forEachIndexed
                }

                builder.append(indent)

                if (firstNonEmptyLine) {
                    builder.append(tripleQuote)
                    firstNonEmptyLine = false
                }

                builder.append(trimFirstSpace(line).trimEnd())
                builder.append("\n")
                lineCount++
            }

            if (builder.isNotEmpty()) {
                if (lineCount == 1) {
                    builder.deleteAt(builder.length - 1)
                } else {
                    builder.append(indent)
                }

                builder.append(tripleQuote)
                builder.append("\n")
            }

            return builder.toString()
        }

        fun trimFirstSpace(line: String): String {
            return if (line.firstOrNull() == ' ') {
                line.substring(1)
            } else {
                line
            }
        }
    }
}
