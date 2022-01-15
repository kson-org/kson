package org.kson.jetbrains.file

import com.intellij.openapi.fileTypes.LanguageFileType
import org.kson.jetbrains.KsonBundle
import org.kson.jetbrains.KsonIcons
import org.kson.jetbrains.KsonLanguage
import javax.swing.Icon

object KsonFileType : LanguageFileType(KsonLanguage) {

    override fun getName(): String {
        return "Kson"
    }

    override fun getDescription(): String {
        return KsonBundle.message("kson.file.fileTypeDescription")
    }

    override fun getDefaultExtension(): String {
        return "kson"
    }

    override fun getIcon(): Icon {
        return KsonIcons.FILE
    }
}