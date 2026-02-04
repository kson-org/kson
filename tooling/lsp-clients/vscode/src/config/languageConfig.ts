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
}

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
        bundledSchemas
    };
}

export function resetLanguageConfiguration(): void {
    cachedConfig = null;
}
