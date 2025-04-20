package org.kson.jetbrains.inject

import com.intellij.lang.xml.XMLLanguage
import com.intellij.testFramework.fixtures.InjectionAssertionData
import org.kson.parser.delimiters.EmbedDelim

/**
 * Tests for KSON language injection functionality.
 */
class KsonInjectionTest : AbstractKsonInjectionTest() {
    fun testAutomaticLanguageInjection() {
        val language = XMLLanguage.INSTANCE
        val injectedContent = "should be injected xml content"
        val fileContent =
            """
            |$$${language.id}
            |$injectedContent$$
            """.trimMargin()
        assertInjectionPresentTest(
            fileContent,
            InjectionAssertionData(injectedContent, language.id)
        )
    }

    fun testLanguageInjectionActionAvailable() {
        assertLanguageInjectionActionPresent(
            """
            key: %%custom
            {<caret>
                "key": "should have injection within `EMBED_CONTENT`"
            }
            %%
        """, true
        )

        assertLanguageInjectionActionPresent(
            """
            key: %%<caret>custom
            {
                "key": "should not have injection outside `EMBED_CONTENT`"
            }
            %%
        """, false
        )

        assertLanguageInjectionActionPresent(
            """
            key:<caret> %%custom
            {
               "key": "should not have injection outside of `EMBED_CONTENT`"
            }
            %%
        """, false
        )

        assertLanguageInjectionActionPresent(
            """
            key: %%html
            {<caret>
                "key": "should not have injection for already injected language"
            }
            %%
        """, false
        )
    }

    fun testFragmentEditorUnescapesOriginalText() {
        for (delim in listOf(EmbedDelim.Dollar, EmbedDelim.Percent)) {
            val fileContent =
                """
                |${delim.delimiter}${XMLLanguage.INSTANCE.id}
                |${delim.char}\${delim.char}<caret>
                |${delim.char}\\${delim.char}
                |${delim.char}\\\${delim.char}
                |${delim.delimiter}
                """.trimMargin()

            assertFileContentUnescapedInFragmentEditor(
                text = fileContent,
                expectedTextInFragmentEditor =
                    """
                    |${delim.char}${delim.char}
                    |${delim.char}\${delim.char}
                    |${delim.char}\\${delim.char}
                    |
                    """.trimMargin()
            )
        }
    }

    fun testFragmentEditorUpdatesOnTyping() {
        for (delim in listOf(EmbedDelim.Dollar, EmbedDelim.Percent)) {
            val fileContent =
                """
                |${delim.delimiter}${XMLLanguage.INSTANCE.id}
                |<caret><xml>${delim.delimiter}
            """.trimMargin()

            assertTypingInFragmentEditorEscapes(
                text = fileContent,
                typedInFragmentEditor =
                    """
                    |${delim.char}${delim.char}
                    |${delim.char}\${delim.char}
                    |${delim.char}\\${delim.char}
                    |
                    """.trimMargin(),
                expectedTextInFile =
                    """
                    |${delim.char}\${delim.char}
                    |${delim.char}\\${delim.char}
                    |${delim.char}\\\${delim.char}
                    |<xml>
                    """.trimMargin()
            )
        }
    }
}
