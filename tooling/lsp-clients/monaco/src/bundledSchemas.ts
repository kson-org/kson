/**
 * Creates Monaco models for bundled schema/metaschema content so go-to-definition
 * can navigate to them.  The LSP server addresses these via `bundled://...` URIs.
 *
 * The `getModel` guard ensures repeated calls (e.g. from a second `attachKsonLsp`
 * sharing the same registry) don't duplicate models.  Disposal only releases
 * models this call created.
 */

import * as monaco from 'monaco-editor';
import { type KsonLspBridge, type KsonLspBridgeOptions } from './bridge/index.js';
import { KSON_LANGUAGE_ID } from './language/ksonLanguage.js';

export interface BundledSchemaModels {
    /** Models created by this call (does NOT include pre-existing ones). */
    readonly created: readonly monaco.editor.ITextModel[];
    /** Dispose only the models this call created. */
    dispose(): void;
}

/** True for `bundled://schema/...` and `bundled://metaschema/...` URIs. */
export function isBundledSchemaUri(uri: monaco.Uri): boolean {
    return uri.scheme === 'bundled';
}

/**
 * Create models for `lspOptions.bundledSchemas` and `lspOptions.bundledMetaSchemas`,
 * skipping any URI a model already exists for.  Newly-created models are opened
 * read-only in the LSP so they get semantic tokens.
 */
export function createBundledSchemaModels(
    lspOptions: KsonLspBridgeOptions['initializationOptions'] | undefined,
    bridge: KsonLspBridge,
): BundledSchemaModels {
    const created: monaco.editor.ITextModel[] = [];

    for (const schema of lspOptions?.bundledSchemas ?? []) {
        const uri = monaco.Uri.parse(`bundled://schema/${schema.fileExtension}.schema.kson`);
        if (!monaco.editor.getModel(uri)) {
            created.push(monaco.editor.createModel(schema.schemaContent, KSON_LANGUAGE_ID, uri));
        }
    }
    for (const meta of lspOptions?.bundledMetaSchemas ?? []) {
        const uri = monaco.Uri.parse(`bundled://metaschema/${meta.name}.schema.kson`);
        if (!monaco.editor.getModel(uri)) {
            created.push(monaco.editor.createModel(meta.schemaContent, KSON_LANGUAGE_ID, uri));
        }
    }

    for (const m of created) {
        bridge.openReadOnlyDocument(m.uri.toString(), m.getValue());
    }

    return {
        created,
        dispose(): void {
            for (const m of created) m.dispose();
        },
    };
}
