import {build} from 'esbuild';
import {copyFileSync, readFileSync, rmSync, writeFileSync} from 'fs';
import {join} from 'path';

const sourceDir = import.meta.dirname;
const packageDir = join(sourceDir, 'dist');
const executable = 'kson-language-server.js';

rmSync(packageDir, {recursive: true, force: true});

await build({
    entryPoints: [join(sourceDir, 'src', 'ksonServerMain.ts')],
    outfile: join(packageDir, executable),
    bundle: true,
    platform: 'node',
    format: 'cjs',
    minify: true,
    logLevel: 'info',
});

const {name, version, description, keywords, license, homepage, repository} =
    JSON.parse(readFileSync(join(sourceDir, 'package.json'), 'utf8'));
const manifest = {name, version, description, keywords, license, homepage, repository, bin: {[name]: executable}};
writeFileSync(join(packageDir, 'package.json'), JSON.stringify(manifest, null, 2) + '\n');

copyFileSync(join(sourceDir, 'README-npm.md'), join(packageDir, 'README.md'));
copyFileSync(join(sourceDir, '..', '..', 'LICENSE'), join(packageDir, 'LICENSE'));
