package org.kson.jsonsuite

/**
 * This is the list of [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 * tests which test as-yet unimplemented aspects of our Json Schema support.
 *
 * TODO: once Json Schema support is fully implemented, this list should be empty and hence deleted.
 *    This will likely be done in an iterative fashion, eliminating the exclusions here for few tests at a time
 *    as support is filled out
 */
fun schemaTestSuiteExclusions() = setOf(

    // ========================================
    // EXTERNAL SCHEMA REFERENCES
    // ========================================
    // These tests require loading external schema files or URLs.
    // Examples include:
    // - Relative file references (e.g., "folderInteger.json")
    // - Absolute URLs (e.g., "http://example.com/schema.json")
    // - References with fragments (e.g., "name.json#/definitions/orNull")
    // Implementation would require a schema loader/resolver that can fetch external resources.

    "refRemote_baseURIChange_baseURIChangeRefInvalid",
    "refRemote_baseURIChange_baseURIChangeRefValid",
    "refRemote_baseURIChange_ChangeFolder_numberIsValid",
    "refRemote_baseURIChange_ChangeFolder_stringIsInvalid",
    "refRemote_baseURIChange_ChangeFolderInSubschema_numberIsValid",
    "refRemote_baseURIChange_ChangeFolderInSubschema_stringIsInvalid",
    "refRemote_fragmentWithinRemoteRef_remoteFragmentInvalid",
    "refRemote_fragmentWithinRemoteRef_remoteFragmentValid",
    "refRemote_location_independentIdentifierInRemoteRef_integerIsValid",
    "refRemote_location_independentIdentifierInRemoteRef_stringIsInvalid",
    "refRemote_refWithinRemoteRef_refWithinRefInvalid",
    "refRemote_refWithinRemoteRef_refWithinRefValid",
    "refRemote_remoteRef_remoteRefInvalid",
    "refRemote_remoteRef_remoteRefValid",
    "refRemote_remoteRefWithRefToDefinitions_invalid",
    "refRemote_remoteRefWithRefToDefinitions_valid",
    "refRemote_retrievedNestedRefsResolveRelativeToTheirURINot______id_numberIsInvalid",
    "refRemote_retrievedNestedRefsResolveRelativeToTheirURINot______id_stringIsValid",
    "refRemote_rootRefInRemoteRef_nullIsValid",
    "refRemote_rootRefInRemoteRef_objectIsInvalid",
    "refRemote_rootRefInRemoteRef_stringIsValid",
    "ref_refWithAbsolute_path_reference_aStringIsValid",
    "ref_refWithAbsolute_path_reference_anIntegerIsInvalid",
    "ref_refsWithQuote_objectWithNumbersIsValid",
    "ref_refsWithQuote_objectWithStringsIsInvalid",
    "ref_refsWithRelativeUrisAndDefs_invalidOnInnerField",
    "ref_refsWithRelativeUrisAndDefs_invalidOnOuterField",
    "ref_refsWithRelativeUrisAndDefs_validOnBothFields",
    "ref_relativeRefsWithAbsoluteUrisAndDefs_invalidOnInnerField",
    "ref_relativeRefsWithAbsoluteUrisAndDefs_invalidOnOuterField",
    "ref_relativeRefsWithAbsoluteUrisAndDefs_validOnBothFields",
    "ref_remoteRef_ContainingRefsItself_remoteRefInvalid",
    "ref_remoteRef_ContainingRefsItself_remoteRefValid",
    "ref_refToElse_aNon_integerIsInvalidDueToThe______ref",
    "ref_refToElse_anIntegerIsValid",
    "ref_refToIf_aNon_integerIsInvalidDueToThe______ref",
    "ref_refToIf_anIntegerIsValid",
    "ref_refToThen_aNon_integerIsInvalidDueToThe______ref",
    "ref_refToThen_anIntegerIsValid",

    // ========================================
    // ANCHOR REFERENCES ($id)
    // ========================================
    // These tests require support for $id references.
    // Examples include:
    // - Simple anchor references (e.g., "#foo")
    // - URN-based identifiers (e.g., "urn:uuid:deadbeef-1234-0000-0000-4321feebdaed")
    // - Non-relative URI anchors (e.g., "https://example.com/schema#foo")

    "refRemote_______refTo______refFindsLocation_independent______id_non_numberIsInvalid",
    "refRemote_______refTo______refFindsLocation_independent______id_numberIsValid",
    "ref_location_independentIdentifier_match",
    "ref_location_independentIdentifier_mismatch",
    "ref_location_independentIdentifierWithBaseURIChangeInSubschema_match",
    "ref_location_independentIdentifierWithBaseURIChangeInSubschema_mismatch",
    "ref_referenceAnAnchorWithANon_relativeURI_match",
    "ref_referenceAnAnchorWithANon_relativeURI_mismatch",
    "ref_simpleURNBaseURIWith______refViaTheURN_invalidUnderTheURNIDedSchema",
    "ref_simpleURNBaseURIWith______refViaTheURN_validUnderTheURNIDedSchema",
    "ref_uRNBaseURIWithURNAndAnchorRef_aNon_stringIsInvalid",
    "ref_uRNBaseURIWithURNAndAnchorRef_aStringIsValid",
    "ref_uRNBaseURIWithURNAndJSONPointerRef_aNon_stringIsInvalid",
    "ref_uRNBaseURIWithURNAndJSONPointerRef_aStringIsValid",

    // ========================================
    // RECURSIVE/CIRCULAR REFERENCES
    // ========================================
    // These tests involve schemas that reference each other recursively.
    // Examples include tree/node structures where schemas mutually reference each other.
    // Proper handling requires careful management of reference resolution to avoid infinite loops.

    "ref_recursiveReferencesBetweenSchemas_invalidTree",
    "ref_recursiveReferencesBetweenSchemas_validTree",

    // ========================================
    // 5. $REF OVERRIDE SEMANTICS
    // ========================================
    // These tests verify that $ref overrides any sibling keywords in the same schema object.
    // According to JSON Schema spec, when $ref is present, all other properties in the same
    // object (except for $id, $anchor, etc.) should be ignored.

    "ref_refOverridesAnySiblingKeywords_refInvalid",
    "ref_refOverridesAnySiblingKeywords_refValid",
    "ref_refOverridesAnySiblingKeywords_refValid_MaxItemsIgnored",

    // ========================================
    // COMPLEX $id RESOLUTION
    // ========================================
    // These tests require advanced $id resolution with base URI changes.
    // The $id keyword can change the base URI for resolving references in subschemas.
    // This includes:
    // - Nested $id declarations that change resolution context
    // - $ref preventing sibling $id from changing base URI
    // - File URI resolution on different platforms

    "ref_______idMustBeResolvedAgainstNearestParent_NotJustImmediateParent_non_numberIsInvalid",
    "ref_______idMustBeResolvedAgainstNearestParent_NotJustImmediateParent_numberIsValid",
    "ref_______refPreventsASibling______idFromChangingTheBaseUri_______refResolvesTo_definitions_base_foo_DataDoesNotValidate", "ref_______refPreventsASibling______idFromChangingTheBaseUri_______refResolvesTo_definitions_base_foo_DataValidates",

    // ========================================
    // META-SCHEMA VALIDATION
    // ========================================
    // These tests validate schema definitions against the JSON Schema meta-schema.
    // This requires the ability to use the JSON Schema specification itself as a schema
    // to validate other schemas (meta-validation).

    "definitions_validateDefinitionAgainstMetaschema_invalidDefinitionSchema",
    "definitions_validateDefinitionAgainstMetaschema_validDefinitionSchema",
)
