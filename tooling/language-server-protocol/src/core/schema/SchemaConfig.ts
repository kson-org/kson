/**
 * Configuration for mapping KSON files to their JSON schemas.
 */

/**
 * A single schema mapping that associates file patterns with a schema.
 */
export interface SchemaMapping {
    /**
     * Glob patterns to match file paths.
     * Examples: ["config/*.kson"] or glob patterns with wildcards
     */
    fileMatch: string[];

    /**
     * Workspace-relative path to the schema file.
     * Example: "schemas/config.schema.json"
     */
    schema: string;
}

/**
 * The root configuration object for .kson-schema.json
 */
export interface SchemaConfig {
    /**
     * Array of schema mappings.
     */
    schemas: SchemaMapping[];
}

/**
 * Type guard to check if an object is a valid SchemaConfig
 */
export function isValidSchemaConfig(obj: unknown): obj is SchemaConfig {
    if (typeof obj !== 'object' || obj === null) {
        return false;
    }

    const config = obj as Record<string, unknown>;

    if (!Array.isArray(config.schemas)) {
        return false;
    }

    return config.schemas.every((mapping: unknown) => {
        if (typeof mapping !== 'object' || mapping === null) {
            return false;
        }

        const m = mapping as Record<string, unknown>;

        return (
            Array.isArray(m.fileMatch) &&
            m.fileMatch.every((pattern: unknown) => typeof pattern === 'string') &&
            typeof m.schema === 'string'
        );
    });
}

export const SCHEMA_CONFIG_FILENAME = '.kson-schema.kson';
