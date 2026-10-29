package org.kson

import java.io.File

/** Names the sdist `uv build --sdist` writes up front, so its task can declare it as an output. */
object PythonSdist {
    /**
     * The name setuptools gives the sdist of [projectName] at [version] (PEP 625).
     * [version] must already be PEP 440 canonical, as setuptools would otherwise rewrite it.
     */
    fun fileName(projectName: String, version: String): String =
        "${projectName.replace(Regex("[-_.]+"), "_").lowercase()}-$version.tar.gz"

    /** Reads the single-line `name` and `version` strings of the `[project]` table; buildSrc has no TOML parser. */
    fun fileName(pyprojectToml: File): String {
        val project = pyprojectToml.readLines().dropWhile { it != "[project]" }.drop(1).takeWhile { !it.startsWith("[") }

        fun stringValue(key: String): String {
            val entry = Regex("""^$key\s*=\s*"([^"]*)"""")
            return project.firstNotNullOfOrNull { entry.find(it) }?.groupValues?.get(1)
                ?: error("$pyprojectToml has no static $key in its [project] table")
        }

        return fileName(stringValue("name"), stringValue("version"))
    }
}
