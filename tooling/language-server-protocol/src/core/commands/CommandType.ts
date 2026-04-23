/**
 * Enum defining all commands the Kson Language Server can execute.
 *
 * Values are **unqualified** ids used as internal discriminators (executor
 * switch, {@link ./CommandParameters} keys). The VSCode/LSP wire form prepends
 * the active configuration namespace via {@link toWireCommandId} and is
 * stripped again in {@link fromWireCommandId}, so a client built under a
 * non-default namespace (e.g. `config.plainFormat`) can coexist with the
 * base `kson` extension in the same VSCode host without command-registration
 * collisions.
 */
export enum CommandType {
    /** Format the document as plain Kson */
    PLAIN_FORMAT = 'plainFormat',

    /** Format the document as delimited Kson */
    DELIMITED_FORMAT = 'delimitedFormat',

    /** Format the document as compact Kson */
    COMPACT_FORMAT = 'compactFormat',

    /** Format the document as classic Kson */
    CLASSIC_FORMAT = 'classicFormat',

    /** Associate a schema with the current document */
    ASSOCIATE_SCHEMA = 'associateSchema',

    /** Remove schema association from the current document */
    REMOVE_SCHEMA = 'removeSchema',
}

/**
 * Default configuration namespace used by the base kson extension and by the
 * server when the client hasn't specified otherwise via initializationOptions.
 * Commands and client settings both live under this prefix on the wire.
 */
export const DEFAULT_CONFIG_NAMESPACE = 'kson';

/**
 * Get all unqualified command ids.
 */
export function getAllCommandIds(): CommandType[] {
    return Object.values(CommandType);
}

/**
 * Build the VSCode/LSP wire id for a command by prepending the active
 * configuration namespace.
 */
export function toWireCommandId(commandId: CommandType | string, namespace: string): string {
    return `${namespace}.${commandId}`;
}

/**
 * Parse a wire command id back to its {@link CommandType}, or return
 * `undefined` if the id is not namespaced under `namespace` or doesn't resolve
 * to a known command.
 */
export function fromWireCommandId(wireId: string, namespace: string): CommandType | undefined {
    const prefix = `${namespace}.`;
    if (!wireId.startsWith(prefix)) {
        return undefined;
    }
    const unqualified = wireId.slice(prefix.length);
    return (Object.values(CommandType) as string[]).includes(unqualified)
        ? (unqualified as CommandType)
        : undefined;
}
