package org.kson.schema

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobMatcherTest {

    // Basic wildcard tests
    @Test
    fun `asterisk matches zero characters`() {
        assertTrue(GlobMatcher.matches("*", ""))
        assertTrue(GlobMatcher.matches("prefix*", "prefix"))
    }

    @Test
    fun `asterisk matches one character`() {
        assertTrue(GlobMatcher.matches("*", "a"))
        assertTrue(GlobMatcher.matches("prefix*", "prefixa"))
    }

    @Test
    fun `asterisk matches multiple characters`() {
        assertTrue(GlobMatcher.matches("*", "hello"))
        assertTrue(GlobMatcher.matches("user*", "user123"))
        assertTrue(GlobMatcher.matches("*admin", "superadmin"))
        assertTrue(GlobMatcher.matches("*admin*", "mysuperadminuser"))
    }

    @Test
    fun `question mark matches exactly one character`() {
        assertTrue(GlobMatcher.matches("file?.txt", "file1.txt"))
        assertTrue(GlobMatcher.matches("file?.txt", "filea.txt"))
        assertTrue(GlobMatcher.matches("???", "abc"))
    }

    @Test
    fun `question mark does not match zero characters`() {
        assertFalse(GlobMatcher.matches("file?.txt", "file.txt"))
    }

    @Test
    fun `question mark does not match multiple characters`() {
        assertFalse(GlobMatcher.matches("file?.txt", "file12.txt"))
    }

    // Combined wildcard tests
    @Test
    fun `combined asterisk and question mark`() {
        assertTrue(GlobMatcher.matches("*.???", "file.txt"))
        assertTrue(GlobMatcher.matches("user?*", "user1"))
        assertTrue(GlobMatcher.matches("user?*", "user123"))
        assertTrue(GlobMatcher.matches("*?*", "a"))
    }

    @Test
    fun `multiple asterisks`() {
        assertTrue(GlobMatcher.matches("*test*case*", "mytestusecasename"))
        assertTrue(GlobMatcher.matches("**", "anything"))
    }

    // Exact match tests
    @Test
    fun `exact match without wildcards`() {
        assertTrue(GlobMatcher.matches("exact", "exact"))
        assertFalse(GlobMatcher.matches("exact", "notexact"))
        assertFalse(GlobMatcher.matches("exact", "exactlonger"))
    }

    // Escaped special characters
    @Test
    fun `escaped asterisk matches literal asterisk`() {
        assertTrue(GlobMatcher.matches("file\\*.txt", "file*.txt"))
        assertFalse(GlobMatcher.matches("file\\*.txt", "file123.txt"))
    }

    @Test
    fun `escaped question mark matches literal question mark`() {
        assertTrue(GlobMatcher.matches("what\\?.txt", "what?.txt"))
        assertFalse(GlobMatcher.matches("what\\?.txt", "whata.txt"))
    }

    @Test
    fun `escaped backslash matches literal backslash`() {
        assertTrue(GlobMatcher.matches("path\\\\to", "path\\to"))
        assertFalse(GlobMatcher.matches("path\\\\to", "path/to"))
    }

    @Test
    fun `mixed escaped and unescaped wildcards`() {
        assertTrue(GlobMatcher.matches("*\\*.txt", "file*.txt"))
        assertTrue(GlobMatcher.matches("prefix\\*suffix*", "prefix*suffixanything"))
        assertTrue(GlobMatcher.matches("a\\*b*c", "a*bXYZc"))
    }

    // Edge cases
    @Test
    fun `empty pattern matches empty string`() {
        assertTrue(GlobMatcher.matches("", ""))
    }

    @Test
    fun `empty pattern does not match non-empty string`() {
        assertFalse(GlobMatcher.matches("", "anything"))
    }

    @Test
    fun `pattern with only asterisk matches everything`() {
        assertTrue(GlobMatcher.matches("*", ""))
        assertTrue(GlobMatcher.matches("*", "anything"))
        assertTrue(GlobMatcher.matches("*", "multiple words"))
    }

    // Regex metacharacter escaping
    @Test
    fun `pattern with regex metacharacters`() {
        assertTrue(GlobMatcher.matches("test.value", "test.value"))
        assertTrue(GlobMatcher.matches("a+b", "a+b"))
        assertTrue(GlobMatcher.matches("x[y]z", "x[y]z"))
        assertTrue(GlobMatcher.matches("a(b)c", "a(b)c"))
        assertTrue(GlobMatcher.matches("x|y", "x|y"))
    }

    @Test
    fun `wildcards work with regex metacharacters in value`() {
        assertTrue(GlobMatcher.matches("*.json", "file.json"))
        assertTrue(GlobMatcher.matches("test.*", "test.value"))
        assertTrue(GlobMatcher.matches("*[*]*", "a[b]c"))
    }

    // Real-world examples
    @Test
    fun `real world pattern - all admin users`() {
        assertTrue(GlobMatcher.matches("*admin*", "admin"))
        assertTrue(GlobMatcher.matches("*admin*", "superadmin"))
        assertTrue(GlobMatcher.matches("*admin*", "admin-user"))
        assertTrue(GlobMatcher.matches("*admin*", "mysuperadminuser"))
        assertFalse(GlobMatcher.matches("*admin*", "user"))
    }

    @Test
    fun `real world pattern - temp files`() {
        assertTrue(GlobMatcher.matches("*-temp", "file-temp"))
        assertTrue(GlobMatcher.matches("*-temp", "user-data-temp"))
        assertFalse(GlobMatcher.matches("*-temp", "permanent"))
    }

    @Test
    fun `real world pattern - user prefix`() {
        assertTrue(GlobMatcher.matches("user*", "user"))
        assertTrue(GlobMatcher.matches("user*", "user1"))
        assertTrue(GlobMatcher.matches("user*", "user123"))
        assertFalse(GlobMatcher.matches("user*", "admin"))
    }

    @Test
    fun `real world pattern - numbered files`() {
        assertTrue(GlobMatcher.matches("file?.txt", "file1.txt"))
        assertTrue(GlobMatcher.matches("file?.txt", "fileA.txt"))
        assertFalse(GlobMatcher.matches("file?.txt", "file10.txt"))
    }
}
