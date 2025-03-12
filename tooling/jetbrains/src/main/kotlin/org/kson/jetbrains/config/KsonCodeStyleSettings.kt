package org.kson.jetbrains.config

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class KsonCodeStyleSettings(codeStyleSettings: CodeStyleSettings)
    : CustomCodeStyleSettings("KsonCodeStyleSettings", codeStyleSettings) {

    companion object {
        const val INDENT_SIZE = 2
        const val TAB_SIZE = 2
    }
}
