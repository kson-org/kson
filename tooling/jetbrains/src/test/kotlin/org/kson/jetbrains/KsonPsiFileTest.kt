package org.kson.jetbrains

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.jetbrains.parser.KsonValidationAnnotator

class KsonPsiFileTest : BasePlatformTestCase() {

    fun testKsonFile() {
        val psiFile = myFixture.configureByText(KsonFileType, "key: val")
        val ksonFile = assertInstanceOf(psiFile, KsonPsiFile::class.java)
        assertFalse(PsiErrorElementUtil.hasErrors(project, ksonFile.virtualFile))

        val psiFileWithError = myFixture.configureByText(KsonFileType, "[\"unclosed list\", ")
        val ksonFileWithError = assertInstanceOf(psiFileWithError, KsonPsiFile::class.java)
        /**
         * Validate that we don't start duplicating errors that [KsonValidationAnnotator]
         * is responsible for logging
         */
        assertFalse(
            "should not have errors --- they are provided by an annotator",
            PsiErrorElementUtil.hasErrors(project, ksonFileWithError.virtualFile)
        )
    }
}
