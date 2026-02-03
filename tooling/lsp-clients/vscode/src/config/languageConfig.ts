export interface LanguageConfiguration {
    languageIds: string[];
    fileExtensions: string[];
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
    cachedConfig = {
        languageIds: languages.map((lang: any) => lang.id).filter(Boolean),
        fileExtensions: languages
            .flatMap((lang: any) => lang.extensions || [])
            .filter(Boolean)
            .map((ext: string) => ext.replace(/^\./, ''))
    };
}

export function resetLanguageConfiguration(): void {
    cachedConfig = null;
}
