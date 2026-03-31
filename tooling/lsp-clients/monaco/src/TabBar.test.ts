// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest';
import { TabBar, type TabBarCallbacks } from './TabBar.js';

/** Records callback invocations for assertions. */
function trackingCallbacks(): TabBarCallbacks & { activations: string[]; closes: string[] } {
    const activations: string[] = [];
    const closes: string[] = [];
    return {
        activations,
        closes,
        onActivate(uri) { activations.push(uri); },
        onClose(uri) { closes.push(uri); },
    };
}

describe('TabBar', () => {
    let callbacks: ReturnType<typeof trackingCallbacks>;
    let tabBar: TabBar;

    beforeEach(() => {
        callbacks = trackingCallbacks();
        tabBar = new TabBar(callbacks);
        document.body.appendChild(tabBar.element);
    });

    it('is hidden when only one tab is open', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);

        expect(tabBar.element.classList.contains('kson-tab-bar-visible')).toBe(false);
    });

    it('becomes visible when a second tab is opened', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        expect(tabBar.element.classList.contains('kson-tab-bar-visible')).toBe(true);
        expect(tabBar.element.querySelectorAll('.kson-tab')).toHaveLength(2);
    });

    it('marks the most recently opened tab as active', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        const tabs = tabBar.element.querySelectorAll('.kson-tab');
        expect(tabs[0].classList.contains('kson-tab-active')).toBe(false);
        expect(tabs[1].classList.contains('kson-tab-active')).toBe(true);
    });

    it('re-activates an existing tab without duplicating it', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);
        tabBar.open('file:///a.kson', 'a.kson', false);

        expect(tabBar.element.querySelectorAll('.kson-tab')).toHaveLength(2);
        const tabs = tabBar.element.querySelectorAll('.kson-tab');
        expect(tabs[0].classList.contains('kson-tab-active')).toBe(true);
        expect(tabs[1].classList.contains('kson-tab-active')).toBe(false);
    });

    it('fires onActivate when a tab is clicked', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        // Click the first (inactive) tab
        const firstTab = tabBar.element.querySelector('.kson-tab') as HTMLElement;
        firstTab.click();

        expect(callbacks.activations).toEqual(['file:///a.kson']);
    });

    it('does not fire onActivate when clicking the already-active tab', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        // Click the second (already active) tab
        const tabs = tabBar.element.querySelectorAll('.kson-tab');
        (tabs[1] as HTMLElement).click();

        expect(callbacks.activations).toEqual([]);
    });

    it('shows a close button only on closeable tabs', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        const tabs = tabBar.element.querySelectorAll('.kson-tab');
        expect(tabs[0].querySelector('.kson-tab-close')).toBeNull();
        expect(tabs[1].querySelector('.kson-tab-close')).not.toBeNull();
    });

    it('closes a tab and activates the neighbor', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        // Click the close button on tab b
        const closeBtn = tabBar.element.querySelector('.kson-tab-close') as HTMLElement;
        closeBtn.click();

        expect(callbacks.closes).toEqual(['file:///b.kson']);
        // Tab b was active, so closing it should activate tab a
        expect(callbacks.activations).toEqual(['file:///a.kson']);
        // Only one tab remains, so the bar is hidden again
        expect(tabBar.element.classList.contains('kson-tab-bar-visible')).toBe(false);
    });

    it('activates the left neighbor when closing a middle tab', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);
        tabBar.open('file:///c.kson', 'c.kson', true);

        // Activate b, then close it
        const tabB = tabBar.element.querySelectorAll('.kson-tab')[1] as HTMLElement;
        tabB.click();  // activate b
        callbacks.activations.length = 0; // reset

        const closeBtn = tabB.querySelector('.kson-tab-close') as HTMLElement;
        closeBtn.click();

        // Should activate a (left neighbor of b)
        expect(callbacks.activations).toEqual(['file:///a.kson']);
        expect(tabBar.element.querySelectorAll('.kson-tab')).toHaveLength(2);
    });

    it('ignores close() on non-closeable tabs', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('file:///b.kson', 'b.kson', true);

        // Programmatically try to close the non-closeable tab
        tabBar.close('file:///a.kson');

        expect(tabBar.element.querySelectorAll('.kson-tab')).toHaveLength(2);
        expect(callbacks.closes).toEqual([]);
    });

    it('removes its element from the DOM on dispose', () => {
        expect(document.body.contains(tabBar.element)).toBe(true);
        tabBar.dispose();
        expect(document.body.contains(tabBar.element)).toBe(false);
    });

    it('renders tab labels correctly', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('bundled://schema/kson.schema.kson', 'kson.schema.kson', true);

        const labels = Array.from(tabBar.element.querySelectorAll('.kson-tab span'))
            .map(el => el.textContent);
        expect(labels).toEqual(['a.kson', 'kson.schema.kson']);
    });

    it('applies read-only styling to read-only tabs', () => {
        tabBar.open('file:///a.kson', 'a.kson', false);
        tabBar.open('bundled://schema/kson.schema.kson', 'kson.schema.kson', true, true);

        const tabs = tabBar.element.querySelectorAll('.kson-tab');
        expect(tabs[0].classList.contains('kson-tab-readonly')).toBe(false);
        expect(tabs[1].classList.contains('kson-tab-readonly')).toBe(true);
    });


});
