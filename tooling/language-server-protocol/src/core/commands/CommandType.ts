/**
 * Enum defining all commands the KSON Language Server can execute.
 *
 * Values are unqualified ids used internally as discriminators. On the wire,
 * they are prefixed with the active distribution id via {@link toWireCommandId}
 * and stripped again in {@link fromWireCommandId}.
 */
export enum CommandType {
    /** Format the document as plain KSON */
    PLAIN_FORMAT = 'plainFormat',

    /** Format the document as delimited KSON */
    DELIMITED_FORMAT = 'delimitedFormat',

    /** Format the document as compact KSON */
    COMPACT_FORMAT = 'compactFormat',

    /** Format the document as classic KSON */
    CLASSIC_FORMAT = 'classicFormat',

    /** Associate a schema with the current document */
    ASSOCIATE_SCHEMA = 'associateSchema',

    /** Remove schema association from the current document */
    REMOVE_SCHEMA = 'removeSchema',
}

/**
 * Get all unqualified command ids.
 */
export function getAllCommandIds(): CommandType[] {
    return Object.values(CommandType);
}

/**
 * Build the VSCode/LSP wire id for a command by prepending the active
 * distribution id.
 */
export function toWireCommandId(commandId: CommandType | string, distributionId: string): string {
    return `${distributionId}.${commandId}`;
}

/**
 * Parse a wire command id back to its {@link CommandType}, or return
 * `undefined` if the id is not prefixed by `distributionId` or doesn't
 * resolve to a known command.
 */
export function fromWireCommandId(wireId: string, distributionId: string): CommandType | undefined {
    const prefix = `${distributionId}.`;
    if (!wireId.startsWith(prefix)) {
        return undefined;
    }
    const unqualified = wireId.slice(prefix.length);
    return (Object.values(CommandType) as string[]).includes(unqualified)
        ? (unqualified as CommandType)
        : undefined;
}
