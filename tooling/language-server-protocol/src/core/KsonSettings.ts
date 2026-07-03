import {LSPAny} from "vscode-languageserver";
import {FormattingStyle} from "kson";
import {formattingStyleFromId} from "./formattingStyle.js";

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
    // Map the configured style string to the FormattingStyle enum, defaulting to
    // PLAIN when absent/unrecognized. The VS Code config enum only surfaces
    // "plain"/"delimited", but this shares the command path's mapper, so a
    // hand-edited settings.json value of "compact"/"classic" is also honored.
    const formatStyle: FormattingStyle = formattingStyleFromId(settings?.format?.formattingStyle);

    // CodeLens enabled by default
    const codeLensEnabled = settings?.codeLens?.enable !== false;

    return {
        formattingStyle: formatStyle,
        codeLensEnabled
    };
}
