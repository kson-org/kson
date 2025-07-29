package org.kson.jetbrains.highlighter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KsonSemanticHighlightAnnotatorTest : BasePlatformTestCase() {
    
    override fun getTestDataPath(): String {
        return "src/test/resources/testData/annotator"
    }

    fun testObjectKeys() {
        myFixture.testHighlighting(false, true, false, "objectKeys.kson")
    }

    fun testStringValues() {
        myFixture.testHighlighting(false, true, false, "stringValues.kson")
    }
}
