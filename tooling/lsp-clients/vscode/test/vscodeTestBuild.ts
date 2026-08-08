/**
 * The VS Code build our extension tests and dev launcher run against.
 *
 * We pin an exact build of course so a floating external dependency cannot
 * suddenly break our build.
 *
 * To bump, consult the VS Code update service for the latest stable build:
 *
 *     curl https://update.code.visualstudio.com/api/update/darwin/stable/latest
 *
 * Minding the naming collision when pull info in from that page:
 * their `productVersion` field (e.g. '1.132.0') is our `version`,
 * and their `version` field (a commit sha) is our `commit` here.
 * We do this name shuffle since our names work better for our
 * internal consumers of this data.
 */
export const vscodeTestBuild = {
    /** The release version, e.g. '1.132.0', as consumed by `@vscode/test-electron` */
    version: '1.132.0',
    /** The same build's commit sha, as consumed by `@vscode/test-web` */
    commit: 'df53daabb18cd157bdb08c7f01c34df936cf12f4',
    /** The release channel of this build, as consumed by `@vscode/test-web` */
    quality: 'stable',
} as const;
