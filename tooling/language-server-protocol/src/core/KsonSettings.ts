import {LSPAny} from "vscode-languageserver";
import {FormattingStyle} from "kson";

/**
 * Configuration settings for the Kson language server.
 *
 * These are the already-unwrapped settings — the server has stripped the
 * configuration namespace (e.g. "kson") before calling
 * {@link ksonSettingsWithDefaults}.
 *
 * Indentation is intentionally absent here: it is driven per-request by the
 * editor's `FormattingOptions` (the "Spaces/Tabs" toggle), not by config.
 */
export interface KsonSettings {
    formattingStyle: FormattingStyle;
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
        formattingStyle: formatStyle,
        codeLensEnabled
    };
}
