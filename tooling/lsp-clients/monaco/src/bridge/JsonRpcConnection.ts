/**
 * JSON-RPC 2.0 transport over a Web Worker.
 *
 * Speaks the same protocol as vscode-languageserver's BrowserMessageReader/Writer,
 * so the LSP server worker doesn't need any changes.
 */

interface PendingRequest {
    resolve: (result: unknown) => void;
    reject: (error: Error) => void;
}

interface JsonRpcRequest {
    jsonrpc: '2.0';
    id: number;
    method: string;
    params?: unknown;
}

interface JsonRpcNotification {
    jsonrpc: '2.0';
    method: string;
    params?: unknown;
}

interface JsonRpcResponse {
    jsonrpc: '2.0';
    id: number;
    result?: unknown;
    error?: { code: number; message: string; data?: unknown };
}

type ServerRequestHandler = (params: unknown) => unknown | Promise<unknown>;

export class JsonRpcConnection {
    private nextId = 1;
    private readonly pending = new Map<number, PendingRequest>();
    private readonly notificationHandlers = new Map<string, (params: unknown) => void>();
    private readonly requestHandlers = new Map<string, ServerRequestHandler>();

    constructor(private readonly worker: Worker) {
        this.worker.onmessage = (event: MessageEvent) => this.handleMessage(event.data);
    }

    sendRequest<T = unknown>(method: string, params?: unknown): Promise<T> {
        return new Promise<T>((resolve, reject) => {
            const id = this.nextId++;
            this.pending.set(id, {
                resolve: resolve as (result: unknown) => void,
                reject,
            });
            const message: JsonRpcRequest = { jsonrpc: '2.0', id, method, params };
            this.worker.postMessage(message);
        });
    }

    sendNotification(method: string, params?: unknown): void {
        const message: JsonRpcNotification = { jsonrpc: '2.0', method, params };
        this.worker.postMessage(message);
    }

    /** Register a handler for server-initiated notifications (e.g. window/logMessage). */
    onNotification(method: string, handler: (params: unknown) => void): void {
        this.notificationHandlers.set(method, handler);
    }

    /** Register a handler for server-initiated requests (e.g. workspace/diagnostic/refresh). */
    onRequest(method: string, handler: ServerRequestHandler): void {
        this.requestHandlers.set(method, handler);
    }

    private handleMessage(message: JsonRpcResponse | JsonRpcNotification | JsonRpcRequest): void {
        if ('id' in message && !('method' in message)) {
            // Response to one of our requests
            const pending = this.pending.get(message.id);
            if (pending) {
                this.pending.delete(message.id);
                if (message.error) {
                    pending.reject(new Error(message.error.message));
                } else {
                    pending.resolve(message.result);
                }
            }
        } else if ('method' in message && 'id' in message) {
            // Server-initiated request (needs a response)
            const handler = this.requestHandlers.get(message.method);
            const id = message.id as number;
            if (handler) {
                new Promise<unknown>((resolve) => resolve(handler(message.params))).then(
                    (result) => this.worker.postMessage({ jsonrpc: '2.0', id, result }),
                    (err) => this.worker.postMessage({
                        jsonrpc: '2.0', id,
                        error: { code: -32603, message: String(err) },
                    }),
                );
            } else {
                // Acknowledge unknown server requests to avoid hanging
                this.worker.postMessage({ jsonrpc: '2.0', id, result: null });
            }
        } else if ('method' in message) {
            // Server-initiated notification
            const handler = this.notificationHandlers.get(message.method);
            if (handler) {
                handler(message.params);
            }
        }
    }

    dispose(): void {
        for (const { reject } of this.pending.values()) {
            reject(new Error('Connection disposed'));
        }
        this.pending.clear();
    }
}
