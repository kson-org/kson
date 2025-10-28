/**
 * Stub for minimatch module in browser builds.
 *
 * The vscode-languageclient library imports minimatch for features like:
 * - DiagnosticFeature (diagnostic pull mode)
 * - NotebookDocumentSyncFeature
 * - FileOperationFeature (create/rename/delete file operations)
 *
 * Since we don't use these features in the browser client, we provide
 * a minimal stub to prevent the full minimatch library from being bundled.
 */

export class Minimatch {
    constructor(pattern: string, options?: any) {
        console.warn('minimatch stub called - this feature is not supported in browser mode');
    }

    match(path: string): boolean {
        return false;
    }
}

// Default export for different import styles
export default {
    Minimatch
};