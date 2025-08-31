package org.kson.jsonsuite

/**
 * This is the list of [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 * tests which should not be run as part of test suite.  See comments on specific exclusions for more details.
 */
fun schemaTestSuiteExclusions() = setOf(

    /**
     * These excludes are all tests which require fetching remote schemas.  We do not want to support fetching
     * right now and so require that schemas be [bundled](https://json-schema.org/blog/posts/bundling-json-schema-compound-documents)
     * before they are passed to Kson.
     *
     * We test bundled versions of these test in [org.kson.schema.JsonSchemaTestBundledRemotes].
     */
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
    "ref_remoteRef_ContainingRefsItself_remoteRefInvalid",
    "ref_remoteRef_ContainingRefsItself_remoteRefValid",
    "refRemote_______refTo______refFindsLocation_independent______id_non_numberIsInvalid",
    "refRemote_______refTo______refFindsLocation_independent______id_numberIsValid",
)
