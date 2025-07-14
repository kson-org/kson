import {LSPAny} from "vscode-languageserver";
import {FormatOptions} from "kson";

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
    // Create FormatOptions based on the provided settings
    let formatOptions: FormatOptions;
    
    if (settings?.kson?.format) {
        const format = settings.kson.format;
        if (format.insertSpaces === false) {
            formatOptions = FormatOptions.Tabs;
        } else {
            // Default to spaces with the specified or default tab size
            const tabSize = format.tabSize ?? 2;
            formatOptions = new FormatOptions.Spaces(tabSize);
        }
    } else {
        // Use the default from the Kotlin library
        formatOptions = FormatOptions.Companion.DEFAULT;
    }
    
    return {
        kson: {
            formatOptions
        }
    };
}