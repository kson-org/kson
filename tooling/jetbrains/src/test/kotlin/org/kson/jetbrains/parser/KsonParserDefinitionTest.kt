package org.kson.jetbrains.parser

import com.intellij.testFramework.LightPlatformTestCase

/**
 * Tests for [KsonParserDefinition] that verify invariants not covered by the PSI parsing tests.
 */
class KsonParserDefinitionTest : LightPlatformTestCase() {

    /**
     * [IFileElementType][com.intellij.psi.tree.IFileElementType] registers itself in a JVM-wide
     * static array ([IElementType.ourRegistry][com.intellij.psi.tree.IElementType]) indexed by a
     * `short`. Creating a new instance on every call to `getFileNodeType()` would eventually
     * overflow that registry, causing `ArrayIndexOutOfBoundsException` and preventing PSI file
     * creation.
     *
     * This test ensures `getFileNodeType()` always returns the same singleton instance.
     */
    fun testFileNodeTypeReturnsSameInstance() {
        val definition = KsonParserDefinition()
        val first = definition.fileNodeType
        val second = definition.fileNodeType
        assertSame("getFileNodeType() must return the same instance on every call", first, second)
    }
}
