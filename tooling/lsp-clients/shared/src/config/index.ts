import tmLanguageJson from '../../extension/config/kson.tmLanguage.json';
import languageConfigJson from '../../extension/config/language-configuration.json';

export const tmLanguage = tmLanguageJson;
export const languageConfiguration = languageConfigJson;

// Export as strings for different consumption methods
export const tmLanguageString = JSON.stringify(tmLanguageJson, null, 2);
export const languageConfigurationString = JSON.stringify(languageConfigJson, null, 2);

// Export individual config properties for type safety
export const KSON_LANGUAGE_ID = 'kson';
export const KSON_EXTENSIONS = ['.kson'];
export const KSON_ALIASES = ['Kson', 'kson', 'KSON'];
export const KSON_SCOPE_NAME = 'source.kson';