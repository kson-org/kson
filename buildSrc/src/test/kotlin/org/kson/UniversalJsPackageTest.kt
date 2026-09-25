package org.kson

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UniversalJsPackageTest {

    private val packageJson = Json.parseToJsonElement(UniversalJsPackage.packageJson("1.2.3")).jsonObject

    /** The `exports` conditions, in the order npm and TypeScript consider them. */
    private val exportConditions: Map<String, JsonObject>
        get() = packageJson["exports"]!!.jsonObject["."]!!.jsonObject
            .mapValues { (_, condition) -> condition.jsonObject }

    /** The `./`-prefixed entry points named anywhere in the manifest. */
    private val entryPoints: List<String>
        get() = listOf("main", "browser", "types")
            .map { packageJson[it]!!.jsonPrimitive.content } +
            exportConditions.values.flatMap { condition ->
                condition.values.map { it.jsonPrimitive.content }
            }

    /** A package directory laid out like a built one: a module and its declarations per environment. */
    private fun createPackageDir(): File {
        val packageDir = createTempDirectory("UniversalJsPackageTest").toFile()
        for (environment in listOf("browser", "node")) {
            val environmentDir = File(packageDir, environment).apply { mkdirs() }
            File(environmentDir, "kson-kson-lib.mjs").writeText("// module")
            File(environmentDir, "kson-kson-lib.d.mts").writeText("// declarations")
        }
        return packageDir
    }

    @Test
    fun versionIsRenderedAsAJsonString() {
        assertEquals("1.2.3", packageJson["version"]!!.jsonPrimitive.content)
        assertTrue(
            packageJson["version"]!!.jsonPrimitive.isString,
            "npm requires a string version"
        )
    }

    @Test
    fun entryPointsNameTheFilesKotlinJsEmits() {
        // spelled out in full: asserting a property of these paths rather than the paths
        // themselves lets a `types` that points at a `.mjs` satisfy the assertion vacuously
        assertEquals(
            Json.parseToJsonElement(
                """
                {
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
                """.trimIndent()
            ),
            packageJson["exports"]!!.jsonObject["."]!!
        )
        assertEquals("./node/kson-kson-lib.mjs", packageJson["main"]!!.jsonPrimitive.content)
        assertEquals("./browser/kson-kson-lib.mjs", packageJson["browser"]!!.jsonPrimitive.content)
        assertEquals("./node/kson-kson-lib.d.mts", packageJson["types"]!!.jsonPrimitive.content)
    }

    @Test
    fun everyExportConditionListsTypesFirst() {
        for ((name, condition) in exportConditions) {
            // Node and TypeScript take the first matching key, so `types` after `default` is unreachable
            assertEquals("types", condition.keys.first(), "`$name` must resolve `types` first")
        }
    }

    @Test
    fun exportsCoverBrowserNodeAndBundlerResolution() {
        // `bundler` resolution matches neither `browser` nor `node`, so `default` must be present
        assertEquals(listOf("browser", "node", "default"), exportConditions.keys.toList())
    }

    @Test
    fun everyEntryPointIsPacked() {
        val packedPaths = packageJson["files"]!!.jsonArray.map { it.jsonPrimitive.content }
        for (entryPoint in entryPoints) {
            val packed = packedPaths.any { entryPoint.removePrefix("./").startsWith(it) }
            assertTrue(packed, "`files` $packedPaths does not pack the entry point $entryPoint")
        }
    }

    @Test
    fun writesTheManifestForACompletePackage() {
        val packageDir = createPackageDir()

        UniversalJsPackage.writePackageJson(packageDir, "1.2.3")

        assertEquals(
            UniversalJsPackage.packageJson("1.2.3"),
            File(packageDir, "package.json").readText()
        )
    }

    @Test
    fun failsWhenAnEntryPointIsMissingFromThePackage() {
        val packageDir = createPackageDir()
        // a manifest advertising declarations that are not present in the package
        packageDir.walkTopDown().filter { it.name.endsWith(".d.mts") }.forEach { it.delete() }

        val exception = assertFailsWith<GradleException> {
            UniversalJsPackage.writePackageJson(packageDir, "1.2.3")
        }

        assertContains(exception.message!!, "browser/kson-kson-lib.d.mts")
        assertContains(exception.message!!, "node/kson-kson-lib.d.mts")
    }
}
