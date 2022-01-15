package org.kson.jetbrains

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import org.junit.Test
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile

class KsonPsiFileTest : BasePlatformTestCase() {

    @Test
    fun testKsonFile() {
        val psiFile = myFixture.configureByText(KsonFileType, "key: val")
        val ksonFile = assertInstanceOf(psiFile, KsonPsiFile::class.java)

        // jetbrains todo test has errors once errors are implemented
        assertFalse(PsiErrorElementUtil.hasErrors(project, ksonFile.virtualFile))
    }
}
