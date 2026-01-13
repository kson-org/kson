package org.kson.value.navigation.json_pointer

/**
 * Represents a validated JsonPointerGlob - an extension of JSON Pointer with glob-style pattern matching.
 *
 * JsonPointerGlob extends RFC 6901 JSON Pointer with:
 * - Wildcard tokens: A token that is exactly `*` matches any single key or array index
 * - Recursive descent: A token that is exactly `**` matches zero or more levels
 * - Glob patterns: A token containing * or ? (but not only `*` or `**`) is treated as a pattern
 * - Backslash escaping: for literal characters in patterns
 * - RFC 6901 escaping: still supported for compatibility
 *
 * Examples:
 * - `JsonPointerGlob("")` for root document
 * - `JsonPointerGlob("/users/*/email")` for email of any user (wildcard, use `*`)
 * - `JsonPointerGlob("/users/**/email")` for all emails at any depth under users (recursive, use `**`)
 * - `JsonPointerGlob("/ **/email")` for all emails anywhere in document (recursive)
 * - `JsonPointerGlob("/users/*admin*/role")` for role of users with "admin" in name (pattern)
 *
 * Recursive descent behavior:
 * - Matches zero or more path segments (object keys or array indices)
 * - Performs depth-first traversal through the document tree
 * - Performance: O(n) where n is the size of the subtree being searched
 *
 * @property pointerString The JsonPointerGlob string (must be valid)
 * @throws IllegalArgumentException if the pointer string is invalid
 */
class JsonPointerGlob(pointerString: String) : BaseJsonPointer(JsonPointerGlobParser(pointerString))

