import {after, afterEach, before, describe, it} from 'mocha';
import assert from 'assert';
import {spawnSync} from 'child_process';
import {mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from 'fs';
import {tmpdir} from 'os';
import {extname, join} from 'path';
import {pathToFileURL} from 'url';
import {DiagnosticSeverity} from 'vscode-languageserver-protocol';
import {LanguageServerProcess, packLanguageServer} from '../../../language-server-protocol/src/test/LanguageServerProcess.js';

interface LspServerConfig {
    command: string;
    args: string[];
    extensionToLanguage: Record<string, string>;
}

/** The server config fields Claude Code accepts; any other makes it skip the whole `.lsp.json` */
const LSP_SERVER_FIELDS = new Set([
    'command', 'extensionToLanguage', 'args', 'transport', 'env', 'initializationOptions', 'settings',
    'workspaceFolder', 'startupTimeout', 'shutdownTimeout', 'requestTimeout', 'restartOnCrash', 'maxRestarts',
    'diagnostics',
]);

const pluginDir = join(import.meta.dirname, '..');
const manifest = readJson(join(pluginDir, '.claude-plugin', 'plugin.json'));
const lspServers: Record<string, LspServerConfig> = readJson(join(pluginDir, '.lsp.json'));
const languageServerVersion: string = readJson(join(pluginDir, '..', '..', 'language-server-protocol', 'package.json')).version;

const isClaudeCliOnPath = spawnSync('claude', ['--version'], {shell: process.platform === 'win32'}).status === 0;

function readJson(path: string) {
    return JSON.parse(readFileSync(path, 'utf8'));
}

describe('Claude Code plugin', () => {
    it('should configure its language servers with only the fields Claude Code accepts', () => {
        for (const [name, config] of Object.entries(lspServers)) {
            assert.deepStrictEqual(Object.keys(config).filter(field => !LSP_SERVER_FIELDS.has(field)), [], `${name}: unknown fields`);
        }
    });

    it("should pin this repository's version and serve only .kson files", () => {
        assert.strictEqual(manifest.version, languageServerVersion);
        assert.deepStrictEqual(lspServers, {
            kson: {
                command: 'npx',
                args: ['-y', `kson-language-server@${languageServerVersion}`, '--stdio'],
                extensionToLanguage: {'.kson': 'kson'},
            },
        });
    });

    (isClaudeCliOnPath ? it : it.skip)('should pass `claude plugin validate --strict` (needs the claude CLI on PATH)', () => {
        const result = spawnSync('claude', ['plugin', 'validate', '--strict', pluginDir], {
            encoding: 'utf8',
            shell: process.platform === 'win32',
        });
        assert.strictEqual(result.status, 0, result.stdout + result.stderr);
    });

    describe('language server', function () {
        this.timeout(60_000);

        let workDir: string;
        let server: LanguageServerProcess;

        before(() => workDir = mkdtempSync(join(tmpdir(), 'kson-claude-code-')));

        afterEach(() => server?.kill());

        after(() => rmSync(workDir, {recursive: true, force: true}));

        it('should start as configured and push syntax and schema diagnostics for .kson files', async () => {
            const {command, args, extensionToLanguage} = lspServers.kson;
            const tarball = packLanguageServer(workDir);
            const publishedPackage = `kson-language-server@${languageServerVersion}`;
            server = LanguageServerProcess.spawn(
                command,
                args.map(arg => arg === publishedPackage ? `file:${tarball}` : arg),
                {...process.env, npm_config_cache: join(workDir, 'npm-cache'), npm_config_offline: 'true'}
            );

            const workspace = join(workDir, 'workspace');
            mkdirSync(workspace);
            writeFileSync(
                join(workspace, '.kson-schema.kson'),
                '{"schemas": [{"fileMatch": ["settings.kson"], "schema": "settings.schema.kson"}]}'
            );
            writeFileSync(join(workspace, 'settings.schema.kson'), '{"properties": {"port": {"type": "integer"}}}');
            await server.initialize(workspace);

            const open = (fileName: string, text: string) => server.open({
                uri: pathToFileURL(join(workspace, fileName)).href,
                languageId: extensionToLanguage[extname(fileName)],
                text
            });

            assert.deepStrictEqual(await open('broken.kson', 'key: "value" extraValue'), [{
                range: {start: {line: 0, character: 13}, end: {line: 0, character: 23}},
                severity: DiagnosticSeverity.Error,
                source: 'kson',
                message: 'Unexpected trailing content. The previous content parsed as a complete Kson document.'
            }]);
            assert.deepStrictEqual(await open('settings.kson', 'port: "eighty"'), [{
                range: {start: {line: 0, character: 7}, end: {line: 0, character: 13}},
                severity: DiagnosticSeverity.Warning,
                source: 'kson',
                message: "Property 'port': Expected one of: integer, but got: string"
            }]);
            assert.strictEqual(await server.shutdown(), 0);
        });
    });
});
