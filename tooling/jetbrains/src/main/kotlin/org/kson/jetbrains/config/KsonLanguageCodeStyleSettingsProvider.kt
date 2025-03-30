package org.kson.jetbrains.config

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import org.kson.jetbrains.KsonLanguage

class KsonLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language {
        return KsonLanguage
    }

    override fun getConfigurableDisplayName(): String {
        return KsonLanguage.NAME
    }

    override fun getCodeSample(settingsType: SettingsType): String {
        return """
            "Kson constructs are": {
              lists:  [ 1,2,3 ]
              strings: [ "foo", "bar", "\u0062\u0061\u0072" ]
              numbers: [ 42, 6.62606975e-34 ]
              dashed_lists:
                - 1
                - 2
                - 3
              booleans: [ true, false ]
              nulls: null
              objects: {
                alphaNumericKey: 10
                "string key": 20
              }
              embed_blocks: 
                %%:this is an embed block tag
                This is embedded content:
                  It's whatever you want!  Note the minimum indent is stripped
                   and inner indents are preserved
                %%
              
              # embed blocks are also useful for embedded code 
              embedded_code:
                %%kotlin
                fun main() {
                    println("Hello, World!")
                }
                %%
            }
        """.trimIndent()
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor {
        return IndentOptionsEditor()
    }

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions
    ) {
        indentOptions.INDENT_SIZE = KsonCodeStyleSettings.INDENT_SIZE
        indentOptions.TAB_SIZE = KsonCodeStyleSettings.TAB_SIZE
    }

    override fun createConfigurable(
        baseSettings: CodeStyleSettings,
        modelSettings: CodeStyleSettings
    ): CodeStyleConfigurable {
        return KsonLanguageCodeStyleConfigurable(baseSettings, modelSettings)
    }

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
        return KsonCodeStyleSettings(settings)
    }
}

private class KsonCodeStyleMainPanel(currentSettings: CodeStyleSettings?, settings: CodeStyleSettings) :
    TabbedLanguageCodeStylePanel(KsonLanguage, currentSettings, settings) {
    override fun initTabs(settings: CodeStyleSettings) {
        addIndentOptionsTab(settings)
    }
}

private class KsonLanguageCodeStyleConfigurable(
    baseSettings: CodeStyleSettings,
    modelSettings: CodeStyleSettings
): CodeStyleAbstractConfigurable(baseSettings, modelSettings, KsonLanguage.NAME) {
    override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
        return KsonCodeStyleMainPanel(currentSettings, settings)
    }
}
