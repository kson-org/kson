import { MonacoEditorLanguageClientWrapper, WrapperConfig } from 'monaco-editor-wrapper';
import { createYamlConfig } from './config/yamlConfig.js';

/**
 * Creates a YAML editor with syntax highlighting support.
 * Returns the wrapper which can be managed using its native API.
 */
export async function createYamlEditor(config: Partial<WrapperConfig> = {}): Promise<MonacoEditorLanguageClientWrapper> {
    const yamlConfig = createYamlConfig(config);
    
    const wrapper = new MonacoEditorLanguageClientWrapper();
    await wrapper.init(yamlConfig);
    
    return wrapper;
}