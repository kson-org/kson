package org.kson.jetbrains.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.file.KsonFileType

class KsonPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, KsonLanguage) {
    override fun getFileType(): FileType {
        return KsonFileType
    }
}