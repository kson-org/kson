import {BaseLanguageClient} from "vscode-languageclient";

export let languageClient: BaseLanguageClient;

/**
 * Deactivation function for language client.
 */
export async function deactivate(): Promise<void> {
    if (languageClient) {
        await languageClient.stop();
        languageClient = undefined;
    }
}