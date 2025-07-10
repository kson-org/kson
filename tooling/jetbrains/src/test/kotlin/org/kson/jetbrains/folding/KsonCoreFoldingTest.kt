package org.kson.jetbrains.folding

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KsonCoreFoldingTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/resources/testData/folding"

    override fun setUp() {
        super.setUp()
        myFixture.testDataPath = testDataPath
    }

    fun testDashListFolding() {
        doTest()
    }

    fun testBracketListFolding() {
        doTest()
    }

    fun testAngleListFolding() {
        doTest()
    }

    fun testEmbedBlockFolding() {
        doTest()
    }

    fun testSingleLineShouldNotFold() {
        doTest()
    }

    fun testObjectFolding() {
        doTest()
    }

    private fun doTest() {
        val testName = getTestName(true)
        myFixture.testFolding("$testDataPath/$testName.kson")
    }
} 
