import type { WrapperConfig } from 'monaco-editor-wrapper';
import { configureDefaultWorkerFactory } from "monaco-editor-wrapper/workers/workerLoaders";
import getTextmateServiceOverride from "@codingame/monaco-vscode-textmate-service-override";
import yamlTmLanguage from './yaml.tmLanguage.xml?raw';

// Simple YAML language configuration
const yamlLanguageConfiguration = {
    comments: {
        lineComment: '#'
    },
    brackets: [
        ['{', '}'],
        ['[', ']']
    ],
    autoClosingPairs: [
        { open: '{', close: '}' },
        { open: '[', close: ']' },
        { open: '"', close: '"' },
        { open: "'", close: "'" }
    ],
    surroundingPairs: [
        { open: '{', close: '}' },
        { open: '[', close: ']' },
        { open: '"', close: '"' },
        { open: "'", close: "'" }
    ],
    folding: {
        offSide: true
    }
};

export function createYamlConfig(overrides: Partial<WrapperConfig> = {}): WrapperConfig {
    const extensionFilesOrContents = new Map<string, string>();
    extensionFilesOrContents.set('/yaml-configuration.json', JSON.stringify(yamlLanguageConfiguration, null, 2));
    extensionFilesOrContents.set('/yaml.tmLanguage.xml', yamlTmLanguage);
    
    return {
        $type: 'extended',
        ...overrides,
        vscodeApiConfig: {
            ...overrides.vscodeApiConfig,
            vscodeApiInitPerformExternally: false,
            serviceOverrides: {
                ...getTextmateServiceOverride()
            }
        },
        extensions: [{
            config: {
                name: 'yaml-monaco',
                publisher: 'yaml.org',
                version: '0.0.1',
                engines: {
                    vscode: '*'
                },
                contributes: {
                    languages: [{
                        id: 'yaml',
                        extensions: ['.yaml', '.yml'],
                        aliases: ['YAML', 'yaml'],
                        configuration: './yaml-configuration.json'
                    }],
                    grammars: [{
                        language: 'yaml',
                        scopeName: 'source.yaml',
                        path: '/yaml.tmLanguage.xml'
                    }]
                }
            },
            filesOrContents: extensionFilesOrContents
        }],
        editorAppConfig: {
            monacoWorkerFactory: configureDefaultWorkerFactory,
            ...overrides.editorAppConfig
        }
    };
}