/** happy-dom has no canvas, but Monaco needs a 2D context to create an editor. */
const context = new Proxy<Record<string | symbol, unknown>>(
    { webkitBackingStorePixelRatio: 1 },
    { get: (target, property) => target[property] ?? (() => {}), set: () => true },
);

HTMLCanvasElement.prototype.getContext = (() => context) as unknown as HTMLCanvasElement['getContext'];
