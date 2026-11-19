import {ChildProcessWithoutNullStreams, spawn, spawnSync} from 'child_process';
import {basename, join} from 'path';
import {pathToFileURL} from 'url';
import {
    createProtocolConnection,
    Diagnostic,
    DidOpenTextDocumentNotification,
    ExitNotification,
    InitializedNotification,
    InitializeRequest,
    InitializeResult,
    ProtocolConnection,
    PublishDiagnosticsNotification,
    PublishDiagnosticsParams,
    ShutdownRequest,
    StreamMessageReader,
    StreamMessageWriter,
} from 'vscode-languageserver-protocol/node';

const languageServerDir = join(import.meta.dirname, '..', '..');

/** Builds the npm package from source and packs it into `destination`, returning the tarball's path. */
export function packLanguageServer(destination: string): string {
    run('npm', ['run', 'package'], languageServerDir);
    const packed = run('npm', ['pack', join(languageServerDir, 'dist'), '--pack-destination', destination, '--json'], destination);
    return join(destination, JSON.parse(packed)[0].filename);
}

export function run(command: string, args: string[], cwd: string): string {
    const result = spawnSync(command, args, {cwd, encoding: 'utf8', shell: process.platform === 'win32'});
    if (result.status !== 0) {
        throw new Error(`${command} ${args.join(' ')} failed (${result.error ?? `exit code ${result.status}`}):\n${result.stdout}${result.stderr}`);
    }
    return result.stdout;
}

export interface OpenedDocument {
    uri: string;
    languageId: string;
    text: string;
}

export class LanguageServerProcess {
    private readonly exitCode: Promise<number | null>;
    private readonly diagnosticsWaiters = new Map<string, (params: PublishDiagnosticsParams) => void>();
    private stderr = '';
    private failure?: Error;

    private constructor(private readonly child: ChildProcessWithoutNullStreams, private readonly connection: ProtocolConnection) {
        this.exitCode = new Promise(resolve => {
            child.once('error', error => {
                this.failure = error;
                resolve(null);
            });
            child.once('exit', resolve);
        });
        child.stderr.setEncoding('utf8').on('data', (chunk: string) => this.stderr += chunk);

        // A reader error means the server wrote something other than the protocol to stdout
        connection.onError(([error]) => {
            this.failure = error;
            child.kill();
        });
        connection.onNotification(PublishDiagnosticsNotification.type, params => {
            this.diagnosticsWaiters.get(params.uri)?.(params);
        });
        connection.listen();
    }

    static spawn(command: string, args: string[]): LanguageServerProcess {
        const child = spawn(command, args, {shell: process.platform === 'win32'});
        return new LanguageServerProcess(child, createProtocolConnection(
            new StreamMessageReader(child.stdout),
            new StreamMessageWriter(child.stdin)
        ));
    }

    async initialize(workspaceFolder: string): Promise<InitializeResult> {
        const uri = pathToFileURL(workspaceFolder).href;
        const result = await this.whileRunning(
            this.connection.sendRequest(InitializeRequest.type, {
                processId: process.pid,
                rootUri: uri,
                workspaceFolders: [{uri, name: basename(workspaceFolder)}],
                capabilities: {textDocument: {publishDiagnostics: {}}},
            }),
            'answering initialize'
        );
        await this.connection.sendNotification(InitializedNotification.type, {});
        return result;
    }

    async open(document: OpenedDocument): Promise<Diagnostic[]> {
        const published = new Promise<PublishDiagnosticsParams>(resolve => this.diagnosticsWaiters.set(document.uri, resolve));
        await this.connection.sendNotification(DidOpenTextDocumentNotification.type, {
            textDocument: {...document, version: 1}
        });
        return (await this.whileRunning(published, `publishing diagnostics for ${document.uri}`)).diagnostics;
    }

    async shutdown(): Promise<number | null> {
        await this.whileRunning(this.connection.sendRequest(ShutdownRequest.type), 'answering shutdown');
        await this.connection.sendNotification(ExitNotification.type);
        return this.exitCode;
    }

    /** Killing `npx` stops the server it started too: the server exits when its stdin closes. */
    async kill(): Promise<void> {
        this.child.kill();
        await this.exitCode;
    }

    private whileRunning<T>(operation: Promise<T>, purpose: string): Promise<T> {
        const stopped = this.exitCode.then(code => {
            throw new Error(
                `The language server stopped (exit code ${code}) before ${purpose}` +
                (this.failure ? `: ${this.failure.message}` : '') +
                `\nstderr:\n${this.stderr}`
            );
        });
        return Promise.race([operation, stopped]);
    }
}
