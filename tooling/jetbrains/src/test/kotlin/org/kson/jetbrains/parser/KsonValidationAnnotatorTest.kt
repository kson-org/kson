package org.kson.jetbrains.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.parser.messages.MessageType

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

    fun testWarningsAreMarkedAsWarnings() {
        // Test that warnings like ignored end-dots are properly marked
        val source = """
            -
            -
        """.trimIndent()
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        
        val highlights = myFixture.doHighlighting()
        
        assertTrue("Should have at least one highlight for the warning", highlights.isNotEmpty())
        
        // Check that we have a warning-level highlight
        val listDashWarning = highlights.filter {
            it.severity.myVal == com.intellij.lang.annotation.HighlightSeverity.WARNING.myVal 
        }.find {
            val listDashMessage = MessageType.DANGLING_LIST_DASH.create().toString()
            it.description == listDashMessage
        }

        assertNotNull("Should have a warning about ignored end-dot", listDashWarning)
        
        // Ensure there are no errors for this valid (but warned) syntax
        val errorHighlights = highlights.filter { 
            it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal 
        }
        assertTrue("Should not have any errors for valid syntax with warnings", errorHighlights.isEmpty())
    }
    
    fun testMultipleWarningsAndErrors() {
        // Test a case with both warnings and errors
        val source = """
            - {key:}
            - 
        """.trimIndent()
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        
        val highlights = myFixture.doHighlighting()
        
        // Should have both warnings and errors
        val warningHighlights = highlights.filter { 
            it.severity.myVal == com.intellij.lang.annotation.HighlightSeverity.WARNING.myVal 
        }
        val errorHighlights = highlights.filter { 
            it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal 
        }
        
        assertTrue("Should have at least one warning", warningHighlights.isNotEmpty())
        assertTrue("Should have at least one error", errorHighlights.isNotEmpty())
    }
} 
