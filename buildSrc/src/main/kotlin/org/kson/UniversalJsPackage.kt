package org.kson

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.gradle.api.GradleException
import java.io.File

/**
 * The `package.json` for the npm package published as `@kson_org/kson`, which ships a browser build
 * and a Node.js build side by side.
 *
 * Kotlin/JS emits each module as `kson-kson-lib.mjs` with sibling declarations named
 * `kson-kson-lib.d.mts`, one pair per environment, so there is no single set of declarations at the
 * package root. That shapes the `exports` map in two ways:
 *
 * - each condition names its own `types`, listed first, since Node and TypeScript take the first
 *   matching key within a condition.
 * - a `default` condition is required for TypeScript's `bundler` module resolution, which resolves
 *   using `["types", "import"]` and so matches neither `browser` nor `node`.
 */
object UniversalJsPackage {
    /**
     * Generates the `package.json` contents for the package at [version].
     */
    fun packageJson(version: String): String = """
        {
          "name": "@kson_org/kson",
          "version": "$version",
          "description": "KSON - Extended JSON format with comments and more",
          "author": {
            "name": "KSON Team",
            "email": "kson@kson.org"
          },
          "repository": {
            "type": "git",
            "url": "https://github.com/kson-org/kson"
          },
          "license": "Apache-2.0",
          "keywords": ["json", "kson", "yaml", "configuration"],
          "exports": {
            ".": {
              "browser": {
                "types": "./browser/kson-kson-lib.d.mts",
                "default": "./browser/kson-kson-lib.mjs"
              },
              "node": {
                "types": "./node/kson-kson-lib.d.mts",
                "default": "./node/kson-kson-lib.mjs"
              },
              "default": {
                "types": "./node/kson-kson-lib.d.mts",
                "default": "./node/kson-kson-lib.mjs"
              }
            }
          },
          "main": "./node/kson-kson-lib.mjs",
          "browser": "./browser/kson-kson-lib.mjs",
          "types": "./node/kson-kson-lib.d.mts",
          "files": [
            "browser/",
            "node/",
            "README.md"
          ]
        }
    """.trimIndent()

    /**
     * Writes [packageJson] for [version] into [packageDir], failing the build if any entry point it
     * names is missing.
     *
     * npm packs a manifest whose entry points do not exist without complaining, so an unchecked
     * typo would surface only as a resolution failure in a consuming project.
     */
    fun writePackageJson(packageDir: File, version: String) {
        val packageJson = packageJson(version)

        val missing = referencedFiles(Json.parseToJsonElement(packageJson))
            .distinct()
            .filterNot { packageDir.resolve(it.removePrefix("./")).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "package.json names entry points that are missing from ${packageDir.absolutePath}: " +
                    missing.joinToString()
            )
        }

        packageDir.resolve("package.json").writeText(packageJson)
    }

    /**
     * Every relative path [element] points at, recognized by the `./` prefix npm requires on
     * `exports` entry points. Bare directory names under `files` are pack globs rather than entry
     * points, so they are not matched.
     */
    private fun referencedFiles(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.values.flatMap(::referencedFiles)
        is JsonArray -> element.flatMap(::referencedFiles)
        is JsonPrimitive -> listOfNotNull(element.contentOrNull?.takeIf { it.startsWith("./") })
    }
}
