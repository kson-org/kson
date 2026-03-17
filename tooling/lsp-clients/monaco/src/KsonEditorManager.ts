import { MonacoVscodeApiWrapper } from 'monaco-languageclient/vscodeApiWrapper';
import { LanguageClientWrapper } from 'monaco-languageclient/lcwrapper';
import { EditorApp, type EditorAppConfig } from 'monaco-languageclient/editorApp';
import { createKsonLanguageConfig } from './config/ksonConfig.js';
import workerUrl from './worker/ksonServer?worker&url';

export interface KsonEditor {
    editorApp: EditorApp;
    languageClient: LanguageClientWrapper;
    start(container: HTMLElement): Promise<void>;
    dispose(): Promise<void>;
}

/**
 * Creates a KSON editor with full language server support.
 *
 * Initializes the VSCode API, starts the language client, and returns
 * an editor ready to be mounted via `start(container)`.
 */
export async function createKsonEditor(overrides?: {
    editorAppConfig?: Partial<EditorAppConfig>;
}): Promise<KsonEditor> {
    const worker = new Worker(workerUrl, {
        type: 'module',
        name: `Kson LS (${Math.random().toString(36).substring(2, 15)})`,
    });

    const config = createKsonLanguageConfig({ worker });

    const mergedEditorConfig: EditorAppConfig = {
        ...config.editorAppConfig,
        ...overrides?.editorAppConfig,
    };

    const apiWrapper = new MonacoVscodeApiWrapper(config.vscodeApiConfig);
    await apiWrapper.start();

    const lcWrapper = new LanguageClientWrapper(config.languageClientConfig);
    await lcWrapper.start();

    const editorApp = new EditorApp(mergedEditorConfig);

    return {
        editorApp,
        languageClient: lcWrapper,
        async start(container: HTMLElement) {
            await editorApp.start(container);
        },
        async dispose() {
            await editorApp.dispose();
            await lcWrapper.dispose();
            apiWrapper.dispose();
            worker.terminate();
        }
    };
}
