/* --------------------------------------------------------------------------------------------
 * Copyright (c) 2024 TypeFox and others.
 * Licensed under the MIT License. See LICENSE in the package root for license information.
 * ------------------------------------------------------------------------------------------ */

import getKeybindingsServiceOverride from '@codingame/monaco-vscode-keybindings-service-override';
import getThemeServiceOverride from '@codingame/monaco-vscode-theme-service-override';
import getTextmateServiceOverride from '@codingame/monaco-vscode-textmate-service-override';
import type { WrapperConfig } from 'monaco-editor-wrapper';
import { languageConfigurationString, tmLanguageString, KSON_LANGUAGE_ID, KSON_EXTENSIONS, KSON_ALIASES, KSON_SCOPE_NAME } from '@kson/lsp-shared';
import {configureDefaultWorkerFactory} from "monaco-editor-wrapper/workers/workerLoaders";

// Import language extensions for embedded language support
import '@codingame/monaco-vscode-typescript-basics-default-extension';
import '@codingame/monaco-vscode-javascript-default-extension';
import '@codingame/monaco-vscode-sql-default-extension';
import '@codingame/monaco-vscode-python-default-extension';

/**
 * Creates the minimal WrapperConfig needed for Kson language support.
 * All other configuration can be provided via overrides.
 */
export function createKsonLanguageConfig(params: {
    worker: Worker;
}): WrapperConfig {
    const extensionFilesOrContents = new Map<string, string>();
    extensionFilesOrContents.set('/kson-configuration.json', languageConfigurationString);
    extensionFilesOrContents.set('/kson-grammar.json', tmLanguageString);

    return {
        $type: 'extended',
        vscodeApiConfig: {
            // Each editor needs its own initialization to ensure proper formatter registration
            vscodeApiInitPerformExternally: false,
            serviceOverrides: {
                ...getKeybindingsServiceOverride(),
                ...getThemeServiceOverride(),
                ...getTextmateServiceOverride()
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
        editorAppConfig: {
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
                        }
                    }
                }
            }
        }
    };
}
