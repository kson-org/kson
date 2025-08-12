package org.kson.jetbrains.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile

/**
 * Tests for [KsonValidationAnnotator] that verify it correctly flows validation errors
 * from KsonCore to the IDE annotation system.
 */
class KsonValidationAnnotatorTest : BasePlatformTestCase() {

    fun testValidKsonHasNoErrors() {
        val source = "key: \"value\""
        val file = myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        myFixture.checkHighlighting(false, false, false)

        // If we get here without exceptions, the annotator ran successfully
        assertNotNull("File should be created", file)
    }

    fun testInvalidKsonHasErrors() {
        // Use a simple syntax error that KsonCore will definitely catch
        val source = "\"unclosed string"
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile

        // This should find syntax errors via the KsonValidationAnnotator
        val highlights = myFixture.doHighlighting()

        assertTrue("Invalid KSON should have at least one error highlight", highlights.isNotEmpty())
        assertTrue(
            "Should have error-level highlights",
            highlights.any { it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal })
    }

    fun testBlankFileHasNoErrors() {
        // Blank files should not error in IDE context (unlike CLI parsing)
        val source = ""
        val file = myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        myFixture.checkHighlighting(false, false, false)

        // If we get here without exceptions, the annotator handled blank files correctly
        assertNotNull("File should be created even when blank", file)
    }

    fun testAnnotatorDirectly() {
        // Test the annotator directly to ensure it handles null gracefully
        val annotator = KsonValidationAnnotator()

        // Test null file text
        val result = annotator.doAnnotate(null)
        assertEquals("Null file text should return empty list", emptyList<Any>(), result)

        // Test valid KSON
        val validResult = annotator.doAnnotate(ValidationInfo("key: \"value\""))
        assertNotNull("Valid KSON should return a result", validResult)

        // Test invalid KSON  
        val invalidResult = annotator.doAnnotate(ValidationInfo("\"unclosed string"))
        assertNotNull("Invalid KSON should return messages", invalidResult)
        assertTrue("Invalid KSON should have error messages", invalidResult.isNotEmpty())
    }

    fun testCoreParseMessagesAreFiltered() {
        val source = "3.0.9"
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile

        // This should not show core parse messages as errors
        val highlights = myFixture.doHighlighting()
        assertEquals(
            "Should only have one error and not duplicate error in parsing and annotating.",
            highlights.size, 1
        )
    }
} 
