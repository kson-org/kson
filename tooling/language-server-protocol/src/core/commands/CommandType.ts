/**
 * Enum defining all available commands in the Kson Language Server
 */
export enum CommandType {
    /**
     * Format the document as plain Kson
     */
    PLAIN_FORMAT = 'kson.plainFormat',

    /**
     * Format the document as delimited Kson
     */
    DELIMITED_FORMAT = 'kson.delimitedFormat',

    /**
     * Format the document as compact Kson
     */
    COMPACT_FORMAT = 'kson.compactFormat',

     /**
     * Format the document as classic Kson
     */
    CLASSIC_FORMAT = 'kson.classicFormat',

    /**
     * Associate a schema with the current document
     */
    ASSOCIATE_SCHEMA = 'kson.associateSchema',

    /**
     * Remove schema association from the current document
     */
    REMOVE_SCHEMA = 'kson.removeSchema'
}

/**
 * Get all available command IDs
 */
export function getAllCommandIds(): string[] {
    return Object.values(CommandType);
}