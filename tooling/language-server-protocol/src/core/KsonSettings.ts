import {LSPAny} from "vscode-languageserver";
import {FormatOptions, FormattingStyle, IndentType} from "kson";

/**
 * Configuration settings for the Kson language server.
 *
 * These are the already-unwrapped settings — the server has stripped the
 * configuration namespace (e.g. "kson") before calling
 * {@link ksonSettingsWithDefaults}.
 */
export interface KsonSettings {
    formatOptions: FormatOptions;
    codeLensEnabled: boolean;
}

/**
 * Create new [KsonSettings] from LSP settings, applying defaults where needed.
 *
 * Input is the object returned by `connection.workspace.getConfiguration(ns)`,
 * so the shape is `{ format?: {...}, codeLens?: {...} }` without any outer
 * namespace key.
 */
export function ksonSettingsWithDefaults(settings?: LSPAny): Required<KsonSettings> {
    // Create IndentType based on the provided settings
    let indentType: IndentType;
    if (settings?.format) {
        const format = settings.format;
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
    if (settings?.format?.formattingStyle) {
        // Map lowercase string to uppercase enum value exhaustively
        const style = settings.format.formattingStyle.toLowerCase();
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

    // CodeLens enabled by default
    const codeLensEnabled = settings?.codeLens?.enable !== false;

    return {
        formatOptions: new FormatOptions(indentType, formatStyle),
        codeLensEnabled
    };
}
