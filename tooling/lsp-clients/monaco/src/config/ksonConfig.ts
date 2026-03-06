/* --------------------------------------------------------------------------------------------
 * Copyright (c) 2024 TypeFox and others.
 * Licensed under the MIT License. See LICENSE in the package root for license information.
 * ------------------------------------------------------------------------------------------ */

import getKeybindingsServiceOverride from '@codingame/monaco-vscode-keybindings-service-override';
import getThemeServiceOverride from '@codingame/monaco-vscode-theme-service-override';
import getTextmateServiceOverride from '@codingame/monaco-vscode-textmate-service-override';
import type { MonacoVscodeApiConfig } from 'monaco-languageclient/vscodeApiWrapper';
import type { LanguageClientConfig } from 'monaco-languageclient/lcwrapper';
import type { EditorAppConfig } from 'monaco-languageclient/editorApp';
import { configureDefaultWorkerFactory } from 'monaco-languageclient/workerFactory';
import { languageConfigurationString, tmLanguageString, KSON_LANGUAGE_ID, KSON_EXTENSIONS, KSON_ALIASES, KSON_SCOPE_NAME } from '@kson/lsp-shared';

// Import language extensions for embedded language support
import '@codingame/monaco-vscode-typescript-basics-default-extension';
import '@codingame/monaco-vscode-javascript-default-extension';
import '@codingame/monaco-vscode-sql-default-extension';
import '@codingame/monaco-vscode-python-default-extension';

export interface KsonLanguageConfig {
    vscodeApiConfig: MonacoVscodeApiConfig;
    languageClientConfig: LanguageClientConfig;
    editorAppConfig: EditorAppConfig;
}

/**
 * Creates the configuration needed for Kson language support, split into the
 * three components required by monaco-languageclient v10+.
 */
export function createKsonLanguageConfig(params: {
    worker: Worker;
}): KsonLanguageConfig {
    const extensionFilesOrContents = new Map<string, string>();
    extensionFilesOrContents.set('/kson-configuration.json', languageConfigurationString);
    extensionFilesOrContents.set('/kson-grammar.json', tmLanguageString);

    return {
        vscodeApiConfig: {
            $type: 'extended',
            viewsConfig: {
                $type: 'EditorService',
            },
            serviceOverrides: {
                ...getKeybindingsServiceOverride(),
                ...getThemeServiceOverride(),
                ...getTextmateServiceOverride()
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
                            path: './kson-grammar.json',
                            embeddedLanguages: {
                                'meta.embedded.typescript': 'typescript',
                                'meta.embedded.javascript': 'javascript',
                                'meta.embedded.sql': 'sql',
                                'meta.embedded.python': 'python'
                            }
                        }]
                    }
                },
                filesOrContents: extensionFilesOrContents
            }],
            monacoWorkerFactory: configureDefaultWorkerFactory
        },
        languageClientConfig: {
            languageId: KSON_LANGUAGE_ID,
            clientOptions: {
                documentSelector: [KSON_LANGUAGE_ID]
            },
            connection: {
                options: {
                    $type: 'WorkerDirect',
                    worker: params.worker
                }
            }
        },
        editorAppConfig: {}
    };
}
