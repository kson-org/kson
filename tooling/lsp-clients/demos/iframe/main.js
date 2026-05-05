// Uses globals defined by /kson-editor.js — see vite.config.ts publicDir.
const logEl = document.getElementById('log');
let firstLog = true;
function log(label, value, cls = 'log-value') {
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

const btnGet = document.getElementById('btn-get');
const btnSet = document.getElementById('btn-set');
const btnDispose = document.getElementById('btn-dispose');

(async () => {
    log('>', 'KsonEditor.create(container, options)', 'log-label');

    const editor = await KsonEditor.create(
        document.getElementById('editor'),
        {
            value: '{\n  # Try adding an unknown key to see schema validation\n  name: "my-project"\n  version: "1.0.0"\n  tags: ["demo" "editor"]\n}',
            schema: { fileExtension: 'kson', schemaContent: schema },
            onChange(value) {
                log('onChange', `${value.length} chars`, 'log-event');
            },
        },
    );

    log('=', 'editor ready', 'log-event');
    btnGet.disabled = false;
    btnSet.disabled = false;
    btnDispose.disabled = false;

    btnGet.onclick = () => {
        log('>', 'editor.getValue()');
        log('=', editor.getValue());
    };

    btnSet.onclick = () => {
        const newValue = '{\n  name: "updated"\n  version: "2.0.0"\n}';
        log('>', `editor.setValue('...')`, 'log-label');
        editor.setValue(newValue);
        log('=', 'done', 'log-event');
    };

    btnDispose.onclick = () => {
        log('>', 'editor.dispose()', 'log-label');
        editor.dispose();
        log('=', 'disposed', 'log-event');
        btnGet.disabled = true;
        btnSet.disabled = true;
        btnDispose.disabled = true;
    };
})();
