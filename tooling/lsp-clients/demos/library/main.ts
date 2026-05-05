import { createKsonEditor } from '@kson/monaco-editor';

const logEl = document.getElementById('log')!;
let firstLog = true;
function log(label: string, value: string, cls: string = 'log-value') {
    if (firstLog) { logEl.textContent = ''; firstLog = false; }
    const line = document.createElement('div');
    const labelSpan = document.createElement('span');
    labelSpan.className = 'log-label';
    labelSpan.textContent = label + ' ';
    const valueSpan = document.createElement('span');
    valueSpan.className = cls;
    valueSpan.textContent = value;
    line.append(labelSpan, valueSpan);
    logEl.appendChild(line);
    logEl.scrollTop = logEl.scrollHeight;
}

const schema = JSON.stringify({
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "name":    { "type": "string",  "description": "Project name" },
        "version": { "type": "string",  "description": "Semantic version" },
        "tags":    { "type": "array", "items": { "type": "string" } }
    },
    "required": ["name", "version"],
    "additionalProperties": false
});

const btnGet = document.getElementById('btn-get') as HTMLButtonElement;
const btnSet = document.getElementById('btn-set') as HTMLButtonElement;
const btnDispose = document.getElementById('btn-dispose') as HTMLButtonElement;
const btnDispose2 = document.getElementById('btn-dispose-2') as HTMLButtonElement;

log('>', 'createKsonEditor(container, options) x2', 'log-label');

const editor1 = await createKsonEditor(document.getElementById('editor-1')!, {
    uri: 'inmemory://kson/document-1.kson',
    value: '{\n  # Try adding an unknown key to see schema validation\n  name: "my-project"\n  version: "1.0.0"\n  tags: ["demo" "editor"]\n}',
    lspOptions: {
        bundledSchemas: [{ fileExtension: 'kson', schemaContent: schema }],
        enableBundledSchemas: true,
    },
});

const editor2 = await createKsonEditor(document.getElementById('editor-2')!, {
    uri: 'inmemory://kson/document-2.kson',
    value: '{\n  # editor 2 active — sharing one LSP worker\n  name: "second-doc"\n  version: "0.1.0"\n}',
    // lspOptions are ignored on the second acquire — the registry is already initialized.
});

log('=', 'editor 1 ready', 'log-event');
log('=', 'editor 2 active — sharing one LSP worker', 'log-event');
btnGet.disabled = false;
btnSet.disabled = false;
btnDispose.disabled = false;
btnDispose2.disabled = false;

// Show change events from editor 1 in the log.
editor1.editor.onDidChangeModelContent(() => {
    log('onChange', `${editor1.editor.getValue().length} chars`, 'log-event');
});

btnGet.onclick = () => {
    log('>', 'editor.editor.getValue()', 'log-label');
    log('=', editor1.editor.getValue());
};

btnSet.onclick = () => {
    const newValue = '{\n  name: "updated"\n  version: "2.0.0"\n}';
    log('>', `editor.editor.setValue('...')`, 'log-label');
    editor1.editor.setValue(newValue);
    log('=', 'done', 'log-event');
};

btnDispose.onclick = () => {
    log('>', 'editor1.dispose()', 'log-label');
    editor1.dispose();
    log('=', 'editor 1 disposed (editor 2 keeps the worker alive)', 'log-event');
    btnGet.disabled = true;
    btnSet.disabled = true;
    btnDispose.disabled = true;
};

btnDispose2.onclick = () => {
    log('>', 'editor2.dispose()', 'log-label');
    editor2.dispose();
    log('=', 'editor 2 disposed', 'log-event');
    btnDispose2.disabled = true;
};
