package org.kson.jsonsuite

/**
 * This class own the list of [JSONTestSuite](https://github.com/nst/JSONTestSuite)
 * tests that we currently skip/exclude when generating [org.kson.parser.json.generated.JsonSuiteTest]
 *
 * Part of completing the Kson parser implementation is removing entries from this, enhancing the parser
 * to satisfy/pass the test, rinse, repeat.
 *
 * Note: we wrap [jsonTestSuiteSkipList] in this class because we want to be able to link to this list
 *   from other places in the source (particularly the generated tests this affects), and Kotlin doc
 *   seems to only link properly from there using a class reference: [JsonTestSuiteSkipList]
 */
class JsonTestSuiteSkipList {
    companion object {
        fun all(): Set<String> {
            return jsonTestSuiteSkipList
        }
        fun contains(testName: String): Boolean {
            return jsonTestSuiteSkipList.contains(testName)
        }
    }
}

private val jsonTestSuiteSkipList = setOf(
    // TODO: https://github.com/kson-org/kson/issues/21 Enable more tests that require parse failures
    "n_array_1_true_without_comma.json",
    "n_array_comma_and_number.json",
    "n_array_double_comma.json",
    "n_array_double_extra_comma.json",
    "n_array_extra_comma.json",
    "n_array_inner_array_no_comma.json",
    "n_array_just_comma.json",
    "n_array_just_minus.json",
    "n_array_missing_value.json",
    "n_array_number_and_comma.json",
    "n_array_number_and_several_commas.json",
    "n_incomplete_false.json",
    "n_incomplete_null.json",
    "n_incomplete_true.json",
    "n_multidigit_number_then_00.json",
    "n_number_-01.json",
    "n_number_1_000.json",
    "n_number_hex_1_digit.json",
    "n_number_hex_2_digits.json",
    "n_number_infinity.json",
    "n_number_Inf.json",
    "n_number_invalid-negative-real.json",
    "n_number_minus_infinity.json",
    "n_number_minus_sign_with_trailing_garbage.json",
    "n_number_minus_space_1.json",
    "n_number_-NaN.json",
    "n_number_NaN.json",
    "n_number_neg_int_starting_with_zero.json",
    "n_number_neg_real_without_int_part.json",
    "n_number_neg_with_garbage_at_end.json",
    "n_number_with_alpha_char.json",
    "n_number_with_alpha.json",
    "n_number_with_leading_zero.json",
    "n_object_bad_value.json",
    "n_object_double_colon.json",
    "n_object_lone_continuation_byte_in_key_and_trailing_comma.json",
    "n_object_missing_value.json",
    "n_object_non_string_key_but_huge_number_instead.json",
    "n_object_trailing_comma.json",
    "n_object_unquoted_key.json",
    "n_single_space.json",
    "n_string_1_surrogate_then_escape_u1.json",
    "n_string_1_surrogate_then_escape_u1x.json",
    "n_string_1_surrogate_then_escape_u.json",
    "n_string_escaped_ctrl_char_tab.json",
    "n_string_escaped_emoji.json",
    "n_string_escape_x.json",
    "n_string_incomplete_escaped_character.json",
    "n_string_incomplete_surrogate_escape_invalid.json",
    "n_string_incomplete_surrogate.json",
    "n_string_invalid_backslash_esc.json",
    "n_string_invalid_unicode_escape.json",
    "n_string_invalid_utf8_after_escape.json",
    "n_string_invalid-utf-8-in-escape.json",
    "n_string_single_string_no_double_quotes.json",
    "n_string_unescaped_newline.json",
    "n_string_unescaped_tab.json",
    "n_string_unicode_CapitalU.json",
    "n_structure_100000_opening_arrays.json",
    "n_structure_capitalized_True.json",
    "n_structure_end_array.json",
    "n_structure_no_data.json",
    "n_structure_object_unclosed_no_value.json",
    "n_structure_open_array_object.json",

    "n_object_key_with_single_quotes.json", // skip because we accept single quotes
    "n_object_single_quote.json", // skip because we accept single quotes
    "n_string_single_quote.json", // skip because we accept single quotes

    // TODO: https://github.com/kson-org/kson/issues/23 Enable "i_" tests
    "i_number_neg_int_huge_exp.json",
    "i_number_real_neg_overflow.json",
    "i_number_too_big_neg_int.json",
    "i_number_very_big_negative_int.json",
    "i_string_utf16BE_no_BOM.json",
    "i_string_UTF-16LE_with_BOM.json",
    "i_structure_UTF-8_BOM_empty_object.json"
)