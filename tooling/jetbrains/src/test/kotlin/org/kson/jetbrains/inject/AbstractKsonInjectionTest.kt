package org.kson.jetbrains.inject

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionAssertionData
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.intellij.lang.annotations.Language
import org.kson.jetbrains.file.KsonFileType

/**
 * Base test class for KSON language injection tests.
 * This class provides methods for testing language injection features in KSON files.
 */
abstract class AbstractKsonInjectionTest : BasePlatformTestCase() {

    /**
     * Tests that language injection is present and working correctly.
     *
     * @param text The KSON text to test with
     * @param injectionAssertion The expected text in the injected file and the corresponding language ID
     */
    protected fun assertInjectionPresentTest(
        @Language("kson") text: String,
        injectionAssertion: InjectionAssertionData,
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())

        InjectionTestFixture(myFixture).assertInjected(injectionAssertion)
    }

    protected fun assertFileContentUnescapedInFragmentEditor(
        @Language("kson") text: String,
        expectedTextInFragmentEditor: String
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())
        val fragmentEditor = InjectionTestFixture(myFixture).openInFragmentEditor()
        assertEquals(
            "should find the properly escaped text in fragment editor",
            expectedTextInFragmentEditor,
            fragmentEditor.file.text,
        )
    }

    protected fun assertTypingInFragmentEditorMatches(
        @Language("kson") text: String,
        typedInFragmentEditor: String,
        expectedTextInFile: String
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())
        val fragmentEditor = InjectionTestFixture(myFixture).openInFragmentEditor()

        fragmentEditor.type(typedInFragmentEditor)

        assertEquals(
            "typed text in fragment editor does not match",
            expectedTextInFile,
            InjectionTestFixture(myFixture).topLevelFile.text
        )
    }

    /**
     * Assert that manual language injection is available when no injection is present.
     */
    protected fun assertLanguageInjectionActionPresent(@Language("kson") text: String, injectionAvailable: Boolean) {
        myFixture.configureByText(KsonFileType, text.trimIndent())

        val intentions = myFixture.availableIntentions
        val injectIntention = intentions.find {
            it.text.contains("Inject language", ignoreCase = true) || it.familyName.contains(
                "Inject language",
                ignoreCase = true
            )
        }
        if (injectionAvailable) {
            assertNotNull("should have 'Inject language' intention available", injectIntention)
        } else {
            assertNull("should not have 'Inject language' intention available", injectIntention)
        }
    }

    /**
     * Tests that language completion is available and contains expected languages.
     *
     * @param text The KSON text to test with
     * @param inputTag: Tag that will be typed in the editor
     * @param expectedLanguageId A language ID that must be present in completion results
     */
    protected fun assertLanguageListCompletionAvailable(
        @Language("kson") text: String,
        inputTag: String,
        expectedLanguageId: String
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())

        myFixture.type(inputTag)
        myFixture.complete(CompletionType.BASIC)
        val lookupElements = myFixture.lookupElements

        assertNotNull("Should have completion variants available", lookupElements)

        val completionTexts = lookupElements!!.map { it.lookupString }
        assertTrue(
            "should contain '$expectedLanguageId', but not found in completion list: $completionTexts",
            completionTexts.contains(expectedLanguageId)
        )
    }
} 