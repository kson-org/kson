package org.kson.jetbrains.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.parser.behavior.embedblock.EmbedDelim

class KsonElementGeneratorTest : BasePlatformTestCase() {
    private lateinit var generator: KsonElementGenerator

    override fun setUp() {
        super.setUp()
        generator = KsonElementGenerator(project)
    }

    fun testEmbedBlockGeneration() {
        val content = """
            |  content with a newline current indent of 2
            |   
        """.trimMargin()
        val generatedBlock = generator.createEmbedBlock(EmbedDelim.Percent, content, tag = "custom")

        val expectedBlock = """
            |%%custom
            |  content with a newline current indent of 2
            |   %%
        """.trimMargin()
        assertEquals(generatedBlock.text, expectedBlock)
    }

    fun testEmbedBlockGenerationWithIndent() {
        val content = """
            |  content with a newline and added indent of 2
            |  
        """.trimMargin()
        val generatedBlock = generator.createEmbedBlock(EmbedDelim.Percent, content, tag = "custom", indentText = "  ")

        val expectedBlock = """
            |%%custom
            |    content with a newline and added indent of 2
            |    %%
        """.trimMargin()
        assertEquals(generatedBlock.text, expectedBlock)
    }


}