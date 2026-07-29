import { execFileSync } from 'node:child_process';
import { mkdtempSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

// The bundler inlines `@kson/lsp-shared` and `kson-language-server`, so they are
// build-time only; shipping them as runtime deps hands consumers a monorepo-relative
// `file:` path that resolves on no registry. The demos miss this — a directory `file:`
// install resolves the nested `file:` against the source tree — so only a packed
// tarball is a faithful check.
const LOCAL_ONLY_PROTOCOLS = ['file:', 'link:', 'workspace:'];

const packageRoot = resolve(__dirname, '..');

let packedManifest: {
    dependencies?: Record<string, string>;
    peerDependencies?: Record<string, string>;
};
let packDir: string;

// 60s timeout: packing reads the whole ~8MB dist tree, which is slow on a cold CI disk.
beforeAll(() => {
    packDir = mkdtempSync(join(tmpdir(), 'kson-monaco-pack-'));
    execFileSync('pnpm', ['pack', '--pack-destination', packDir], {
        cwd: packageRoot,
        stdio: 'pipe',
    });

    const tarball = readdirSync(packDir).find((f) => f.endsWith('.tgz'));
    if (!tarball) throw new Error(`pnpm pack produced no tarball in ${packDir}`);

    const manifest = execFileSync('tar', ['-xzOf', join(packDir, tarball), 'package/package.json'], {
        encoding: 'utf8',
    });
    packedManifest = JSON.parse(manifest);
}, 60_000);

afterAll(() => {
    if (packDir) rmSync(packDir, { recursive: true, force: true });
});

describe('packed tarball manifest', () => {
    it.each(['dependencies', 'peerDependencies'] as const)(
        'declares no local-only specifiers in %s',
        (field) => {
            const offenders = Object.entries(packedManifest[field] ?? {}).filter(([, spec]) =>
                LOCAL_ONLY_PROTOCOLS.some((protocol) => spec.startsWith(protocol)),
            );

            expect(offenders).toEqual([]);
        },
    );
});
