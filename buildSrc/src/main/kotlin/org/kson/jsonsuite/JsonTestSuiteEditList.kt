package org.kson.jsonsuite

/**
 * This class owns the list of modifications to the [JSONTestSuite](https://github.com/nst/JSONTestSuite)
 * tests that we want to incorporate for Kson when generating [org.kson.parser.json.generated.JsonSuiteTest]
 *
 * Note: we wrap the raw [jsonTestSuiteEditList] val in this class because we want to be able to link to this list
 *   from other places in the source (particularly the generated tests this affects, and Kotlin doc
 *   seems to only link properly from there using a class reference: [JsonTestSuiteEditList]
 */
class JsonTestSuiteEditList {
    companion object {
        fun all(): Set<String> {
            return jsonTestSuiteEditList.keys
        }
        fun get(testName: String): JsonTestEditType {
            return jsonTestSuiteEditList[testName] ?: JsonTestEditType.NONE
        }
    }
}

/**
 * The types of "edits" we make when generating our native Kotlin version of these tests
 */
enum class JsonTestEditType {
    /**
     * Assert that a "No"/"n_" test for Json should pass in Kson due to it being a superset of JSON
     */
    ACCEPT_N_FOR_SUPERSET,

    /**
     * Comment out this test in our generated file until we've had a chance to review how it should behave
     *
     * TODO: currently not used in [JsonTestSuiteEditList] as no tests need investigation!  Keep this around for now
     *   as it is an important part of the test infrastructure here, but note that unless a bunch of new
     *   tests are added that need deferred investigation, this is strictly speaking dead code and should be deleted
     *   if/when it causes even a single headache
     */
    SKIP_NEEDS_INVESTIGATION,

    /**
     * Default used for all tests not listed in [jsonTestSuiteEditList]: use the test as is, without "edits"
     */
    NONE
}

/**
 * Map of [JSONTestSuite](https://github.com/nst/JSONTestSuite) test file names to the [JsonTestEditType] that
 * we want applied to that test in [JsonTestSuiteGenerator]
 */
private val jsonTestSuiteEditList = mapOf(
    "n_array_1_true_without_comma.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_array_colon_instead_of_comma.json" to JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_array_comma_and_number.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_array_extra_comma.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_array_inner_array_no_comma.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_array_missing_value.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_array_number_and_comma.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_incomplete_false.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_incomplete_null.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_incomplete_true.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_-01.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_1_000.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_Inf.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_minus_space_1.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_NaN.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_with_leading_zero.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_bad_value.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_infinity.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_number_neg_int_starting_with_zero.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_lone_continuation_byte_in_key_and_trailing_comma.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_trailing_comma.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_unquoted_key.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_with_trailing_garbage.json" to JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_string_accentuated_char_no_quotes.json" to JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_structure_ascii-unicode-identifier.json" to JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_structure_unicode-identifier.json" to JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_string_single_string_no_double_quotes.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_string_unescaped_newline.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_structure_capitalized_True.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_structure_trailing_#.json" to JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_key_with_single_quotes.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_object_single_quote.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_string_single_quote.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "n_string_unescaped_tab.json" to  JsonTestEditType.ACCEPT_N_FOR_SUPERSET,
    "y_object_duplicated_key.json" to JsonTestEditType.SKIP_NEEDS_INVESTIGATION,
    "y_object_duplicated_key_and_value.json" to JsonTestEditType.SKIP_NEEDS_INVESTIGATION
)