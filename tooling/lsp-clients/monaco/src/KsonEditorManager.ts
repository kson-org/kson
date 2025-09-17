import { MonacoEditorLanguageClientWrapper, WrapperConfig } from 'monaco-editor-wrapper';
import { createKsonLanguageConfig } from './config/ksonConfig.js';
import workerUrl from './worker/ksonServer?worker&url';


/**
 * Creates a Kson-specific WrapperConfig with sensible defaults.
 * You can override any properties by spreading your own config.
 */
async function createKsonConfig(overrides: Partial<WrapperConfig> = {}): Promise<WrapperConfig> {
    // Create a dedicated worker for this editor
    const worker = new Worker(workerUrl, {
        type: 'module',
        name: `Kson LS (${Math.random().toString(36).substring(2, 15)})`,
    });

    const baseConfig = createKsonLanguageConfig({
        worker
    });
    
    // Deep merge base config with overrides
    const finalConfig: WrapperConfig = {
        ...baseConfig,
        ...overrides,
        vscodeApiConfig: {
            ...baseConfig.vscodeApiConfig,
            ...overrides.vscodeApiConfig
        },
        languageClientConfigs: {
            ...baseConfig.languageClientConfigs,
            ...overrides.languageClientConfigs
        },
        editorAppConfig: {
            ...baseConfig.editorAppConfig,
            ...overrides.editorAppConfig,
        }
    };
    
    // Attach worker reference for cleanup
    (finalConfig as any).__worker = worker;
    
    return finalConfig;
}

/**
 * Creates a Kson editor wrapper using the standard monaco-editor-wrapper.
 * Returns the wrapper which can be managed using its native API.
 */
export async function createKsonEditor(config: Partial<WrapperConfig> = {}): Promise<MonacoEditorLanguageClientWrapper> {
    const ksonConfig = await createKsonConfig(config);
    
    const wrapper = new MonacoEditorLanguageClientWrapper();
    await wrapper.init(ksonConfig);
    
    return wrapper;
}