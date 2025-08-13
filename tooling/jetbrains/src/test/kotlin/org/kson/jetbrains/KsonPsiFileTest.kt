package org.kson.jetbrains

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile

class KsonPsiFileTest : BasePlatformTestCase() {

    fun testKsonFileWithoutErrors() {
        val psiFile = myFixture.configureByText(KsonFileType, "key: val")
        val ksonFile = assertInstanceOf(psiFile, KsonPsiFile::class.java)
        assertFalse(PsiErrorElementUtil.hasErrors(project, ksonFile.virtualFile))
    }

    fun testKsonFileWithParseError() {
        val psiFileWithError = myFixture.configureByText(KsonFileType, "[\"unclosed list\", ")
        val ksonFileWithError = assertInstanceOf(psiFileWithError, KsonPsiFile::class.java)
        assertTrue(
            "should have errors --- since unclosed list error is created during parsing",
            PsiErrorElementUtil.hasErrors(project, ksonFileWithError.virtualFile)
        )
    }

    fun testKsonFileWithPostProcessingIndentError() {
        val psiFile = myFixture.configureByText(KsonFileType, """
            key: value
              bad_indent: value
        """.trimIndent())
        val ksonFileWithError = assertInstanceOf(psiFile, KsonPsiFile::class.java)
        assertFalse(
            "should not have errors --- they are provided by an annotator",
            PsiErrorElementUtil.hasErrors(project, ksonFileWithError.virtualFile)
        )

        // But the annotator should highlight the bad indentation as an error
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Annotator should detect bad indentation error",
            highlights.isNotEmpty()
        )
        assertTrue(
            "Error message should mention indentation",
            highlights.any { it.description?.contains("indent", ignoreCase = true) == true }
        )
    }
}
