import { BrowserMessageReader, BrowserMessageWriter } from 'vscode-languageclient/browser.js';
import { MonacoEditorLanguageClientWrapper } from 'monaco-editor-wrapper';
import { setupKsonClientExtended } from './config/config.js';
import workerUrl from './worker/ksonServer?worker&url';

export const runKsonWrapper = async () => {
    try {
        let wrapper: MonacoEditorLanguageClientWrapper;

        const loadKsonWorker = () => {
            console.log(`Kson worker URL: ${workerUrl}`);
            return new Worker(workerUrl, {
                type: 'module',
                name: 'Kson LS',
            });
        };

        const checkStarted = () => {
            if (wrapper?.isStarted() ?? false) {
                alert('Editor was already started!\nPlease reload the page to test the alternative editor.');
                return true;
            }
            return false;
        };

        const startKsonClient = async () => {
            if (checkStarted()) return;

            const worker = loadKsonWorker();
            const reader = new BrowserMessageReader(worker);
            const writer = new BrowserMessageWriter(worker);
            reader.listen((message) => {
                console.log('Received message from worker:', message);
            });

            const config = await setupKsonClientExtended({
                worker,
                messageTransports: { reader, writer }
            });
            wrapper = new MonacoEditorLanguageClientWrapper();
            await wrapper.initAndStart(config);

            await delayExecution(1000);
        };

        const disposeEditor = async () => {
            if (!wrapper) return;
            wrapper.reportStatus();
            await wrapper.dispose();
            wrapper = undefined;
        };

        document.querySelector('#button-start')?.addEventListener('click', startKsonClient);
        document.querySelector('#button-dispose')?.addEventListener('click', disposeEditor);
    } catch (e) {
        console.error(e);
    }
};

export const delayExecution = (ms: number) => {
    return new Promise((resolve) => setTimeout(resolve, ms));
};
