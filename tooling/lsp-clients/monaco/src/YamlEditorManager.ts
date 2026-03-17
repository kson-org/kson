import { MonacoVscodeApiWrapper } from 'monaco-languageclient/vscodeApiWrapper';
import { EditorApp, type EditorAppConfig } from 'monaco-languageclient/editorApp';
import { createYamlConfig } from './config/yamlConfig.js';

export interface YamlEditor {
    editorApp: EditorApp;
    start(container: HTMLElement): Promise<void>;
    dispose(): Promise<void>;
}

/**
 * Creates a YAML editor with syntax highlighting support.
 *
 * Initializes the VSCode API and returns an editor ready to be mounted
 * via `start(container)`.
 */
export async function createYamlEditor(overrides?: {
    editorAppConfig?: Partial<EditorAppConfig>;
}): Promise<YamlEditor> {
    const config = createYamlConfig(overrides);

    const apiWrapper = new MonacoVscodeApiWrapper(config.vscodeApiConfig);
    await apiWrapper.start();

    const editorApp = new EditorApp(config.editorAppConfig);

    return {
        editorApp,
        async start(container: HTMLElement) {
            await editorApp.start(container);
        },
        async dispose() {
            await editorApp.dispose();
            apiWrapper.dispose();
        }
    };
}
