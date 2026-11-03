package org.kson

import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PythonSdistTest {

    @Test
    fun dashesInTheProjectNameBecomeUnderscores() {
        assertEquals("kson_lang-0.4.0.dev0.tar.gz", PythonSdist.fileName("kson-lang", "0.4.0.dev0"))
    }

    @Test
    fun separatorRunsCollapseAndTheProjectNameIsLowercased() {
        assertEquals("kson_lang-1.2.3.tar.gz", PythonSdist.fileName("Kson-._Lang", "1.2.3"))
    }

    @Test
    fun readsNameAndVersionFromTheProjectTable() {
        val pyproject = pyproject(
            """
            [tool.other]
            name = "other"
            version = "9.9.9"

            [project]
            authors = [
              { name="KSON", email="kson@kson.org" },
            ]
            # [[kson-version-num]]
            name = "kson-lang"
            version = "0.4.0.dev0"

            [project.urls]
            name = "urls"
            """
        )

        assertEquals("kson_lang-0.4.0.dev0.tar.gz", PythonSdist.fileName(pyproject))
    }

    @Test
    fun failsWhenTheProjectTableHasNoStaticVersion() {
        val pyproject = pyproject(
            """
            [project]
            name = "kson-lang"
            dynamic = ["version"]

            [tool.other]
            version = "9.9.9"
            """
        )

        val failure = assertFailsWith<IllegalStateException> { PythonSdist.fileName(pyproject) }
        assertEquals("$pyproject has no static version in its [project] table", failure.message)
    }

    private fun pyproject(contents: String): File =
        File(createTempDirectory("PythonSdistTest").toFile(), "pyproject.toml").apply {
            writeText(contents.trimIndent())
        }
}
