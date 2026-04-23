import { DEFAULT_CONFIG_NAMESPACE } from 'kson-language-server';

/**
 * Configuration for bundled schemas mapped by file extension.
 */
export interface BundledSchemaMapping {
    /** File extension this schema applies to (without leading dot) */
    fileExtension: string;
    /** Relative path to the bundled schema file (from extension root) */
    schemaPath: string;
}

export interface LanguageConfiguration {
    languageIds: string[];
    fileExtensions: string[];
    /** Bundled schema mappings extracted from package.json */
    bundledSchemas: BundledSchemaMapping[];
    /**
     * Prefix for VSCode commands and configuration keys (e.g. "kson").
     * Read from {@link CONFIG_NAMESPACE_MANIFEST_FIELD}, defaulting to "kson".
     */
    configNamespace: string;
}

/**
 * Root-level package.json field naming the namespace for VSCode commands and
 * configuration keys. A build toolchain that produces a non-default extension
 * (e.g. a derived one using a different `languageId`) sets this so the derived
 * extension doesn't collide with the base kson extension on install.
 *
 * A derived extension's manifest must keep three things in sync: (1) set
 * `ksonConfigNamespace` to the new namespace; (2) rewrite static contribution
 * keys — `contributes.commands[].command` ids and `contributes.configuration.properties`
 * keys — to live under that prefix instead of `kson.*`. Runtime call sites all
 * route through {@link getConfigNamespace}, so no source changes are required.
 */
const CONFIG_NAMESPACE_MANIFEST_FIELD = 'ksonConfigNamespace';

let cachedConfig: LanguageConfiguration | null = null;

/**
 * Get language configuration. Must be initialized first via initializeLanguageConfig.
 */
export function getLanguageConfiguration(): LanguageConfiguration {
    if (!cachedConfig) {
        throw new Error('Language configuration not initialized. Call initializeLanguageConfig first.');
    }
    return cachedConfig;
}

/**
 * Check if a language ID is a KSON language.
 */
export function isKsonLanguage(languageId: string): boolean {
    return getLanguageConfiguration().languageIds.includes(languageId);
}

/**
 * Namespace prefix for this extension's commands and configuration keys at
 * runtime. Contribution keys in package.json (commands, configuration
 * properties) are static and must be rewritten separately when forking.
 */
export function getConfigNamespace(): string {
    return getLanguageConfiguration().configNamespace;
}

/**
 * Initialize language configuration from extension's package.json.
 * Call this early in the activate function.
 */
export function initializeLanguageConfig(packageJson: any): void {
    const languages = packageJson?.contributes?.languages || [];

    // Extract bundled schema mappings using file extension from lang.extensions[0]
    const bundledSchemas: BundledSchemaMapping[] = languages
        .filter((lang: any) => lang.extensions?.[0] && lang.bundledSchema)
        .map((lang: any) => ({
            fileExtension: lang.extensions[0].replace(/^\./, ''),
            schemaPath: lang.bundledSchema
        }));

    cachedConfig = {
        languageIds: languages.map((lang: any) => lang.id).filter(Boolean),
        fileExtensions: languages
            .flatMap((lang: any) => lang.extensions || [])
            .filter(Boolean)
            .map((ext: string) => ext.replace(/^\./, '')),
        bundledSchemas,
        configNamespace:
            packageJson?.[CONFIG_NAMESPACE_MANIFEST_FIELD]
            || DEFAULT_CONFIG_NAMESPACE
    };
}

export function resetLanguageConfiguration(): void {
    cachedConfig = null;
}
