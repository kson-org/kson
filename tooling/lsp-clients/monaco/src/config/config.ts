/* --------------------------------------------------------------------------------------------
 * Copyright (c) 2024 TypeFox and others.
 * Licensed under the MIT License. See LICENSE in the package root for license information.
 * ------------------------------------------------------------------------------------------ */

import getKeybindingsServiceOverride from '@codingame/monaco-vscode-keybindings-service-override';
import getThemeServiceOverride from '@codingame/monaco-vscode-theme-service-override';
import getTextmateServiceOverride from '@codingame/monaco-vscode-textmate-service-override';
import { LogLevel } from '@codingame/monaco-vscode-api';
import { MessageTransports } from 'vscode-languageclient';
import type { WrapperConfig } from 'monaco-editor-wrapper';
import { configureDefaultWorkerFactory } from 'monaco-editor-wrapper/workers/workerLoaders';
import '@codingame/monaco-vscode-theme-defaults-default-extension';
import exampleText from '../../resources/kson/example.kson?raw';

import { languageConfigurationString, tmLanguageString, KSON_LANGUAGE_ID, KSON_EXTENSIONS, KSON_ALIASES, KSON_SCOPE_NAME } from '@kson/lsp-shared';

export const setupKsonClientExtended = async (params: {
    worker: Worker
    messageTransports?: MessageTransports,
}): Promise<WrapperConfig> => {

    const extensionFilesOrContents = new Map<string, string | URL>();
    // vite build is easier with string content
    extensionFilesOrContents.set('/kson-configuration.json', languageConfigurationString);
    extensionFilesOrContents.set('/kson-grammar.json', tmLanguageString);
    return {
        $type: 'extended',
        htmlContainer: document.getElementById('monaco-editor-root')!,
        logLevel: LogLevel.Debug,
        vscodeApiConfig: {
            serviceOverrides: {
                ...getKeybindingsServiceOverride(),
                ...getThemeServiceOverride(),
                ...getTextmateServiceOverride()
            },
            userConfiguration: {
                json: JSON.stringify({
                    'editor.guides.bracketPairsHorizontal': 'active',
                    'editor.wordBasedSuggestions': 'off',
                    'editor.experimental.asyncTokenization': true,
                    'editor.semanticHighlighting.enabled': true,
                    'vitest.disableWorkspaceWarning': true
                })
            }
        },
        extensions: [{
            config: {
                name: 'kson-monaco',
                publisher: 'kson.org',
                version: '0.0.1',
                engines: {
                    vscode: '*'
                },
                contributes: {
                    languages: [{
                        id: KSON_LANGUAGE_ID,
                        extensions: KSON_EXTENSIONS,
                        aliases: KSON_ALIASES,
                        configuration: './kson-configuration.json'
                    }],
                    grammars: [{
                        language: KSON_LANGUAGE_ID,
                        scopeName: KSON_SCOPE_NAME,
                        path: './kson-grammar.json'
                    }]
                }
            },
            filesOrContents: extensionFilesOrContents
        }],
        editorAppConfig: {
            codeResources: {
                modified: {
                    text: exampleText,
                    uri: '/workspace/example.kson'
                }
            },
            monacoWorkerFactory: configureDefaultWorkerFactory
        },
        languageClientConfigs: {
            configs: {
                kson: {
                    clientOptions: {
                        documentSelector: [KSON_LANGUAGE_ID]
                    },
                    connection: {
                        options: {
                            $type: 'WorkerDirect',
                            worker: params.worker
                        },
                        messageTransports: params.messageTransports
                    }
                }
            }
        }
    };
};
