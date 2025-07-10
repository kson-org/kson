package org.kson.jetbrains.psi

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.parser.behavior.embedblock.EmbedDelim

class KsonCoreIndentHandlerTest : BasePlatformTestCase() {
    private lateinit var generator: KsonElementGenerator

    override fun setUp() {
        super.setUp()
        generator = KsonElementGenerator(project)
    }

    private fun doTrimTest(
        input: String,
        expectedRanges: List<TextRange>
    ) {
        val embedBlock = generator.createEmbedBlock(EmbedDelim.Percent, input)
        val embedContent = embedBlock.embedContent!!
        val result = KsonTrimIndentHandler().getUntrimmedRanges(embedContent)
        assertEquals(expectedRanges.size, result.size)
        expectedRanges.zip(result).forEachIndexed { index, (expected, actual) ->
            assertEquals("Range at index $index does not match", expected, actual)
        }
    }
    
    fun testTrimIndentBasicIndentation() {
        doTrimTest(
            """
            |    line1
            |    line2
            |    line3
            """.trimMargin(),
            listOf(
                TextRange(4, 10),   // "line1\n"
                TextRange(14, 20),  // "line2\n"
                TextRange(24, 29)   // "line3"
            )
        )
    }

    fun testTrimIndentWithBlankLines() {
        doTrimTest(
            """
            |    1
            |    
            |    
            |    5
            """.trimMargin(),
            listOf(
                TextRange(4, 6),    // "1\n"
                TextRange(10, 11),  // "\n"
                TextRange(15, 16),  // "\n"
                TextRange(20, 21)   // "5"
            )
        )
    }

    fun testTrimIndentAllBlankLines() {
        doTrimTest(
            """
            |    
            |    
            |    
            |  
            """.trimMargin(),
            listOf(
                TextRange(2, 5),    // "  \n"
                TextRange(7, 10),  // "  \n"
                TextRange(12, 15),  // "  \n"
                TextRange(17, 17)   // ""
            )
        )
    }

    fun testTrimIndentWithEndingBlankLine() {
        doTrimTest(
            """
            |    1
            |      
            """.trimMargin(),
            listOf(
                TextRange(4, 6),    // "1\n"
                TextRange(10, 12)   // "  "
            )
        )
    }

    fun testTrimIndentWithEmptyContent() {
        doTrimTest(
            """
            |
            """.trimMargin(),
            listOf(
                TextRange(0, 0),
            )
        )
    }

    fun testTrimIndentWithMixedIndentation() {
        doTrimTest(
            """
            |    line1
            |      line2
            |    line3
            """.trimMargin(),
            listOf(
                TextRange(4, 10),   // "line1\n"
                TextRange(14, 22),  // "  line2\n"
                TextRange(26, 31)   // "line3"
            )
        )
    }

    fun testTrimIndentWithStartingBlankLine() {
        doTrimTest(
            """
            |    
            |    line1
            |    line2
            """.trimMargin(),
            listOf(
                TextRange(4, 5),    // "\n"
                TextRange(9, 15),   // "line1\n"
                TextRange(19, 24)   // "line2"
            )
        )
    }

    fun testTrimIndentWithSingleLine() {
        doTrimTest(
            "    single line",
            listOf(TextRange(4, 15))
        )
    }
}
