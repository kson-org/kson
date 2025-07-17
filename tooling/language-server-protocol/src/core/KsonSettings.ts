import {LSPAny} from "vscode-languageserver";
import {FormatOptions, FormattingStyle, IndentType} from "kson";

/**
 * Configuration settings for the Kson language server
 */
export interface KsonSettings {
    kson: {
        formatOptions: FormatOptions;
    };
}

/**
 * Create new [KsonSettings] from LSP settings, applying defaults where needed.
 */
export function ksonSettingsWithDefaults(settings?: LSPAny): Required<KsonSettings> {
    // Create IndentType based on the provided settings
    let indentType: IndentType;
    if (settings?.kson?.format) {
        const format = settings.kson.format;
        if (format.insertSpaces === false) {
            indentType = IndentType.Tabs;
        } else {
            // Default to spaces with the specified or default tab size
            const tabSize = format.tabSize ?? 2;
            indentType = new IndentType.Spaces(tabSize);
        }
    } else {
        // Use the default from the Kotlin library
        indentType = new IndentType.Spaces(2);
    }

    // Create FormattingStyle based on the provided settings
    let formatStyle: FormattingStyle
    if (settings?.kson?.format?.formattingStyle) {
        // Map lowercase string to uppercase enum value exhaustively
        const style = settings.kson.format.formattingStyle.toLowerCase();
        switch (style) {
            case 'plain':
                formatStyle = FormattingStyle.PLAIN;
                break;
            case 'delimited':
                formatStyle = FormattingStyle.DELIMITED;
                break;
            default:
                // Default to PLAIN for any unrecognized value
                formatStyle = FormattingStyle.PLAIN;
                break;
        }
    } else {
        formatStyle = FormattingStyle.PLAIN;
    }

    return {
        kson: {
            formatOptions: new FormatOptions(indentType, formatStyle)
        }
    };
}