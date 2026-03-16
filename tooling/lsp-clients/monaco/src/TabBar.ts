/**
 * A minimal tab bar for switching between documents in a KSON editor.
 *
 * The tab bar is hidden when only one document is open, and appears
 * automatically when go-to-definition navigates to a second document
 * (e.g. a bundled schema).
 */

/** Injects tab-bar styles into the document head (idempotent). */
let stylesInjected = false;
function injectStyles(): void {
    if (stylesInjected) return;
    stylesInjected = true;

    const style = document.createElement('style');
    style.textContent = `
        .kson-tab-bar {
            display: none;
            flex-shrink: 0;
            overflow-x: auto;
            background: #f8f8f8;
            border-bottom: 1px solid #e0e0e0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            font-size: 13px;
            line-height: 1;
        }
        .kson-tab-bar-visible {
            display: flex;
        }
        .kson-tab {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 8px 12px;
            cursor: pointer;
            color: #888888;
            border-right: 1px solid #e0e0e0;
            white-space: nowrap;
            user-select: none;
            border-bottom: 2px solid transparent;
        }
        .kson-tab:hover {
            color: #333333;
        }
        .kson-tab-active {
            color: #333333;
            background: #ffffff;
            border-bottom-color: #007acc;
        }
        .kson-tab-close {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 16px;
            height: 16px;
            border-radius: 3px;
            border: none;
            background: transparent;
            color: inherit;
            cursor: pointer;
            font-size: 14px;
            line-height: 1;
            padding: 0;
            opacity: 0;
        }
        .kson-tab:hover .kson-tab-close,
        .kson-tab-active .kson-tab-close {
            opacity: 0.6;
        }
        .kson-tab-close:hover {
            background: rgba(0, 0, 0, 0.1);
            opacity: 1 !important;
        }
        .kson-tab-readonly {
            background: #fdf6e3;
        }
        .kson-tab-readonly.kson-tab-active {
            background: #fdf6e3;
            border-bottom-color: #b58900;
        }
    `;
    document.head.appendChild(style);
}

interface Tab {
    readonly uri: string;
    readonly label: string;
    readonly closeable: boolean;
    readonly readOnly: boolean;
}

export interface TabBarCallbacks {
    /** Called when a tab is activated by the user clicking on it. */
    onActivate(uri: string): void;
    /** Called when a tab is closed.  Also fires onActivate for the newly active tab. */
    onClose(uri: string): void;
}

export class TabBar {
    private readonly tabs: Tab[] = [];
    private activeUri: string | null = null;
    readonly element: HTMLElement;
    private readonly callbacks: TabBarCallbacks;

    constructor(callbacks: TabBarCallbacks) {
        injectStyles();
        this.callbacks = callbacks;
        this.element = document.createElement('div');
        this.element.className = 'kson-tab-bar';
    }

    /**
     * Opens a tab for the given URI. If a tab already exists, it is activated.
     * This is called programmatically (e.g. from the editor opener) so it does
     * NOT fire onActivate — the caller is already handling the model switch.
     */
    open(uri: string, label: string, closeable: boolean, readOnly: boolean = false): void {
        if (!this.tabs.find(t => t.uri === uri)) {
            this.tabs.push({ uri, label, closeable, readOnly });
        }
        this.activeUri = uri;
        this.render();
    }

    /**
     * Closes a tab. Non-closeable tabs are ignored.
     *
     * If the closed tab was active, the nearest neighbor is activated and
     * onActivate fires before onClose — the caller can switch to the new
     * model first, then clean up the old one.
     */
    close(uri: string): void {
        const index = this.tabs.findIndex(t => t.uri === uri);
        if (index < 0) return;
        if (!this.tabs[index].closeable) return;

        this.tabs.splice(index, 1);

        if (this.activeUri === uri) {
            if (this.tabs.length > 0) {
                const neighbor = this.tabs[Math.max(0, index - 1)];
                this.activeUri = neighbor.uri;
                this.callbacks.onActivate(neighbor.uri);
            } else {
                this.activeUri = null;
            }
        }
        this.callbacks.onClose(uri);
        this.render();
    }

    dispose(): void {
        this.element.remove();
    }

    private render(): void {
        const visible = this.tabs.length > 1;
        this.element.classList.toggle('kson-tab-bar-visible', visible);

        this.element.innerHTML = '';
        for (const tab of this.tabs) {
            const tabEl = document.createElement('div');
            let className = 'kson-tab';
            if (tab.uri === this.activeUri) className += ' kson-tab-active';
            if (tab.readOnly) className += ' kson-tab-readonly';
            tabEl.className = className;

            const labelEl = document.createElement('span');
            labelEl.textContent = tab.label;
            tabEl.appendChild(labelEl);

            if (tab.closeable) {
                const closeEl = document.createElement('button');
                closeEl.className = 'kson-tab-close';
                closeEl.textContent = '\u00d7';
                closeEl.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.close(tab.uri);
                });
                tabEl.appendChild(closeEl);
            }

            tabEl.addEventListener('click', () => {
                if (tab.uri !== this.activeUri) {
                    this.activeUri = tab.uri;
                    this.render();
                    this.callbacks.onActivate(tab.uri);
                }
            });

            this.element.appendChild(tabEl);
        }
    }
}
