package org.kson

import kotlin.test.Test

class KsonCoreTestNumber : KsonTest {
    /**
     * See also [org.kson.parser.NumberParserTest] for more targeted number parsing tests
     */
    @Test
    fun testNumberLiteralSource() {
        assertParsesTo("42", "42", "42", "42")
        assertParsesTo("042", "42", "42", "42")
        assertParsesTo("42.1", "42.1", "42.1", "42.1")
        assertParsesTo("00042.1", "42.1", "42.1", "42.1")
        assertParsesTo("42.1E0", "42.1e0", "42.1e0", "42.1e0")
        assertParsesTo("42.1e0", "42.1e0", "42.1e0", "42.1e0")
        assertParsesTo("4.21E1", "4.21e1", "4.21e1", "4.21e1")
        assertParsesTo("421E-1", "421e-1", "421e-1", "421e-1")
        assertParsesTo("4210e-2", "4210e-2", "4210e-2", "4210e-2")
        assertParsesTo("0.421e2", "0.421e2", "0.421e2", "0.421e2")
        assertParsesTo("0.421e+2", "0.421e+2", "0.421e+2", "0.421e+2")
        assertParsesTo("42.1E+0", "42.1e+0", "42.1e+0", "42.1e+0")
        assertParsesTo("00042.1E0", "42.1e0", "42.1e0", "42.1e0")
        assertParsesTo("-42.1", "-42.1", "-42.1", "-42.1")
        assertParsesTo("-42.1E0", "-42.1e0", "-42.1e0", "-42.1e0")
        assertParsesTo("-42.1e0", "-42.1e0", "-42.1e0", "-42.1e0")
        assertParsesTo("-4.21E1", "-4.21e1", "-4.21e1", "-4.21e1")
        assertParsesTo("-421E-1", "-421e-1", "-421e-1", "-421e-1")
        assertParsesTo("-4210e-2", "-4210e-2", "-4210e-2", "-4210e-2")
        assertParsesTo("-0.421e2", "-0.421e2", "-0.421e2", "-0.421e2")
        assertParsesTo("-0.421e+2", "-0.421e+2", "-0.421e+2", "-0.421e+2")
        assertParsesTo("-42.1E+0", "-42.1e+0", "-42.1e+0", "-42.1e+0")
        assertParsesTo("-00042.1E0", "-42.1e0", "-42.1e0", "-42.1e0")
    }
}
