import * as vscode from 'vscode';
import { BundledSchemaConfig, BundledMetaSchemaConfig } from '../../config/bundledSchemaLoader';

/**
 * TextDocumentContentProvider for bundled:// URIs.
 *
 * ## Why this exists
 *
 * The LSP server creates schema documents with `bundled://schema/{fileExtension}` URIs
 * for schemas that are bundled with the extension, and `bundled://metaschema/{name}` URIs
 * for metaschemas. When "Go to Definition" returns a location pointing to these URIs,
 * VS Code needs to be able to open them.
 *
 * Without this provider, VS Code would fail with:
 * "Unable to resolve resource bundled://schema/xxx"
 *
 * This provider maps bundled URIs to the pre-loaded schema content, allowing navigation
 * to work correctly.
 *
 * URI formats:
 * - bundled://schema/{fileExtension}.schema.kson
 * - bundled://metaschema/{name}.schema.kson
 *
 * @see BundledSchemaConfig in bundledSchemaLoader.ts for architecture discussion
 * @see test/suite/bundled-schema.test.ts for integration tests
 */
export class BundledSchemaContentProvider implements vscode.TextDocumentContentProvider {
    private contentByKey: Map<string, string>;

    constructor(bundledSchemas: BundledSchemaConfig[], bundledMetaSchemas: BundledMetaSchemaConfig[] = []) {
        this.contentByKey = new Map();

        // Register extension-based schemas: key = "schema/{ext}.schema.kson"
        for (const schema of bundledSchemas) {
            const key = `schema/${schema.fileExtension}.schema.kson`;
            this.contentByKey.set(key, schema.schemaContent);
        }

        // Register metaschemas: key = "metaschema/{name}.schema.kson"
        for (const metaSchema of bundledMetaSchemas) {
            const key = `metaschema/${metaSchema.name}.schema.kson`;
            this.contentByKey.set(key, metaSchema.schemaContent);
        }
    }

    provideTextDocumentContent(uri: vscode.Uri): string | undefined {
        // URI format: bundled://{authority}/{path}
        // uri.authority = "schema" or "metaschema"
        // uri.path = "/{name}.schema.kson"
        const key = `${uri.authority}${uri.path}`;
        // Remove leading slash from path: "schema//ext.schema.kson" → "schema/ext.schema.kson"
        const normalizedKey = key.replace(/^([^/]+)\/\//, '$1/');
        return this.contentByKey.get(normalizedKey);
    }
}

/**
 * Register the bundled schema content provider.
 *
 * @param bundledSchemas The loaded bundled schemas
 * @param bundledMetaSchemas The loaded bundled metaschemas
 * @returns The registered disposable
 */
export function registerBundledSchemaContentProvider(
    bundledSchemas: BundledSchemaConfig[],
    bundledMetaSchemas: BundledMetaSchemaConfig[] = []
): vscode.Disposable {
    const provider = new BundledSchemaContentProvider(bundledSchemas, bundledMetaSchemas);
    return vscode.workspace.registerTextDocumentContentProvider('bundled', provider);
}
