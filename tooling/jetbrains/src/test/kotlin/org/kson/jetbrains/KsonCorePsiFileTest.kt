package org.kson.jetbrains

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile

class KsonCorePsiFileTest : BasePlatformTestCase() {

    fun testKsonFile() {
        val psiFile = myFixture.configureByText(KsonFileType, "key: val")
        val ksonFile = assertInstanceOf(psiFile, KsonPsiFile::class.java)
        assertFalse(PsiErrorElementUtil.hasErrors(project, ksonFile.virtualFile))

        val psiFileWithError = myFixture.configureByText(KsonFileType, "[\"unclosed list\", ")
        val ksonFileWithError = assertInstanceOf(psiFileWithError, KsonPsiFile::class.java)
        assertTrue(PsiErrorElementUtil.hasErrors(project, ksonFileWithError.virtualFile))
    }
}
