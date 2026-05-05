/**
 * React demo: drop kson into an existing `@monaco-editor/react` app.
 *
 * Three `<Editor />` instances share one Monaco runtime — no second copy,
 * no CDN load.  The two kson editors call the `useKsonLsp` hook and share
 * a single language-server worker via the refcounted registry.
 */

import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import * as monaco from 'monaco-editor';
import { Editor, loader } from '@monaco-editor/react';
import { useKsonLsp } from '@kson/monaco-editor/react';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

// Must run before any <Editor> renders — otherwise @monaco-editor/react
// race-fetches a second monaco from CDN and our LSP providers register
// against a different module than the one the editors are using.
loader.config({ monaco });

// Expose monaco for browser-based smoke tests.
(window as unknown as { monaco: typeof monaco }).monaco = monaco;

// Worker factory.  Monaco's built-in features (tokenization, diff,
// json/css/html/ts) all use named workers; kson uses the plain editor.worker.
// The default branch is what attachKsonLsp relies on.
if (!self.MonacoEnvironment) {
    self.MonacoEnvironment = {
        getWorker(_workerId: string, _label: string): Worker {
            // Add label-specific branches (json, typescript, ...) here as you need them.
            return new editorWorker();
        },
    };
}

const SAMPLE_JSON = `{
  "name": "kson-react-demo",
  "version": "1.0.0"
}`;

const SAMPLE_KSON = `{
  # The "unknown_key" below violates the schema — hover for the diagnostic.
  name: "kson-react-demo"
  version: "1.0.0"
  tags: ["demo" "react"]
  unknown_key: "schema forbids this"
}`;

const SAMPLE_KSON_2 = `{
  # Second kson editor — shares the LSP worker with the first via the registry.
  name: "second-document"
  version: "0.1.0"
  tags: ["registry" "shared"]
}`;

const SAMPLE_SCHEMA = JSON.stringify({
    $schema: 'http://json-schema.org/draft-07/schema#',
    type: 'object',
    properties: {
        name: { type: 'string', description: 'Project name' },
        version: { type: 'string', description: 'Semantic version' },
        tags: { type: 'array', items: { type: 'string' } },
    },
    required: ['name', 'version'],
    additionalProperties: false,
});

function App() {
    // Capture the editors via onMount — useKsonLsp drives the rest.
    const [kson1, setKson1] = useState<monaco.editor.IStandaloneCodeEditor | null>(null);
    const [kson2, setKson2] = useState<monaco.editor.IStandaloneCodeEditor | null>(null);

    useKsonLsp(kson1, {
        lspOptions: {
            bundledSchemas: [{ fileExtension: 'kson', schemaContent: SAMPLE_SCHEMA }],
            enableBundledSchemas: true,
        },
    });
    useKsonLsp(kson2);

    return (
        <div className="layout">
            <div className="guide">
                <div className="guide-content">
                    <h1>React Demo</h1>
                    <p className="subtitle">
                        Adding KSON to an existing <code>@monaco-editor/react</code> app.
                    </p>

                    <h2>1. Reuse the bundled monaco</h2>
                    <p>
                        Without this, <code>@monaco-editor/react</code> fetches a second
                        Monaco from a CDN, breaking type sharing and worker config.
                    </p>
                    <pre><code>{`import * as monaco from 'monaco-editor';
import { loader } from '@monaco-editor/react';

loader.config({ monaco });`}</code></pre>

                    <h2>2. Attach the LSP with one hook</h2>
                    <p>
                        <code>useKsonLsp</code> registers the language and wires
                        completions, diagnostics, hover, etc. into the editor that
                        the React component creates.  Capture the editor with
                        <code>useState</code> in <code>onMount</code> and hand it
                        to the hook — StrictMode-safe cleanup is built in.
                    </p>
                    <pre><code>{`const [editor, setEditor] = useState(null);
useKsonLsp(editor, { lspOptions: {...} });

<Editor onMount={setEditor} />`}</code></pre>

                    <h2>3. Worker factory cohabitation</h2>
                    <p>
                        KSON tokenization runs in the default <code>editor.worker</code>.
                        If you already define <code>MonacoEnvironment.getWorker</code>,
                        keep your label-specific branches and add a default fallback:
                    </p>
                    <pre><code>{`self.MonacoEnvironment = {
  getWorker(_id, label) {
    if (label === 'json') return new JsonWorker();
    return new EditorWorker(); // default
  },
};`}</code></pre>
                    <p>
                        The <code>?worker</code> import this demo uses is Vite syntax.
                        For webpack, swap the import for <code>new Worker(new URL('monaco-editor/esm/vs/editor/editor.worker.js', import.meta.url))</code>;
                        for Next.js, load the editor with <code>dynamic(..., {`{ ssr: false }`})</code> since
                        Monaco is browser-only.
                    </p>

                    <h2>4. Model URI must end in <code>.kson</code></h2>
                    <p>
                        The LSP keys schemas on the file extension via the model's URI.
                        With <code>@monaco-editor/react</code>, <code>defaultPath</code> is what
                        controls that URI — give each editor a distinct path ending in
                        <code>.kson</code> or the language server won't pick it up.
                    </p>
                    <pre><code>{`<Editor language="kson" defaultPath="config.kson" ... />`}</code></pre>
                </div>
            </div>

            <div className="editors">
                <div className="editor-pane">
                    <div className="label">json — plain @monaco-editor/react</div>
                    <div className="body">
                        <Editor
                            language="json"
                            defaultValue={SAMPLE_JSON}
                            defaultPath="config.json"
                            options={{ minimap: { enabled: false }, automaticLayout: true }}
                        />
                    </div>
                </div>
                <div className="editor-pane">
                    <div className="label">kson #1 — LSP attached</div>
                    <div className="body">
                        <Editor
                            language="kson"
                            defaultValue={SAMPLE_KSON}
                            // URI must end in .kson so the LSP picks it up — see step 4.
                            defaultPath="config-1.kson"
                            options={{
                                minimap: { enabled: false },
                                automaticLayout: true,
                                'semanticHighlighting.enabled': true,
                            }}
                            onMount={setKson1}
                        />
                    </div>
                </div>
                <div className="editor-pane">
                    <div className="label">kson #2 — sharing the LSP worker</div>
                    <div className="body">
                        <Editor
                            language="kson"
                            defaultValue={SAMPLE_KSON_2}
                            // Distinct path so the model URI doesn't collide with kson #1.
                            defaultPath="config-2.kson"
                            options={{
                                minimap: { enabled: false },
                                automaticLayout: true,
                                'semanticHighlighting.enabled': true,
                            }}
                            onMount={setKson2}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}

const root = createRoot(document.getElementById('root')!);
// StrictMode double-invokes effects in dev — verifies the cancelled-flag pattern inside useKsonLsp.
root.render(<StrictMode><App /></StrictMode>);
