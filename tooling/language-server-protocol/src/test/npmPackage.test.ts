import {after, afterEach, before, describe, it} from 'mocha';
import assert from 'assert';
import {mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync} from 'fs';
import {tmpdir} from 'os';
import {join} from 'path';
import {pathToFileURL} from 'url';
import {DiagnosticSeverity} from 'vscode-languageserver';
import {LanguageServerProcess, packLanguageServer, run} from './LanguageServerProcess.js';

describe('npm package', function () {
    this.timeout(60_000);

    let workDir: string;
    let installDir: string;
    let server: LanguageServerProcess;

    before(() => {
        workDir = mkdtempSync(join(tmpdir(), 'kson-language-server-'));
        const tarball = packLanguageServer(workDir);

        installDir = join(workDir, 'install');
        mkdirSync(installDir);
        writeFileSync(join(installDir, 'package.json'), '{"private": true}\n');
        run('npm', ['install', tarball, '--offline', '--no-audit', '--no-fund', '--cache', join(workDir, 'npm-cache')], installDir);
    });

    afterEach(() => server?.kill());

    after(() => rmSync(workDir, {recursive: true, force: true}));

    it('should install as a single bundled file that needs no other package', () => {
        const installedPackages = readdirSync(join(installDir, 'node_modules')).filter(entry => !entry.startsWith('.'));
        assert.deepStrictEqual(installedPackages, ['kson-language-server']);
        assert.deepStrictEqual(
            readdirSync(join(installDir, 'node_modules', 'kson-language-server')).sort(),
            ['LICENSE', 'README.md', 'kson-language-server.js', 'package.json']
        );
    });

    it('should ship README-npm.md as its README', () => {
        assert.strictEqual(
            readFileSync(join(installDir, 'node_modules', 'kson-language-server', 'README.md'), 'utf8'),
            readFileSync(join(import.meta.dirname, '..', '..', 'README-npm.md'), 'utf8')
        );
    });

    it('should push diagnostics over stdio to a client that cannot pull them', async () => {
        const workspace = join(workDir, 'workspace');
        mkdirSync(workspace);
        server = LanguageServerProcess.spawn(join(installDir, 'node_modules', '.bin', 'kson-language-server'), ['--stdio']);
        await server.initialize(workspace);

        const diagnostics = await server.open({
            uri: pathToFileURL(join(workspace, 'invalid.kson')).href,
            languageId: 'kson',
            text: 'key: "value" extraValue'
        });

        assert.deepStrictEqual(diagnostics, [{
            range: {start: {line: 0, character: 13}, end: {line: 0, character: 23}},
            severity: DiagnosticSeverity.Error,
            source: 'kson',
            message: 'Unexpected trailing content. The previous content parsed as a complete Kson document.'
        }]);
        assert.strictEqual(await server.shutdown(), 0);
    });
});
