package org.kson.jetbrains.file

import com.intellij.openapi.fileTypes.LanguageFileType
import org.kson.jetbrains.KsonBundle
import org.kson.jetbrains.KsonIcons
import org.kson.jetbrains.KsonLanguage
import javax.swing.Icon

object KsonFileType : LanguageFileType(KsonLanguage) {
    override fun getName(): String = KsonLanguage.NAME

    override fun getDescription(): String = KsonBundle.message("kson.file.fileTypeDescription")

    override fun getDefaultExtension(): String = "kson"

    override fun getIcon(): Icon = KsonIcons.FILE
}
