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
    "refRemote::remote ref::remote ref valid",
    "refRemote::remote ref::remote ref invalid",
    "refRemote::fragment within remote ref::remote fragment valid",
    "refRemote::fragment within remote ref::remote fragment invalid",
    "refRemote::ref within remote ref::ref within ref valid",
    "refRemote::ref within remote ref::ref within ref invalid",
    "refRemote::base URI change::base URI change ref valid",
    "refRemote::base URI change::base URI change ref invalid",
    "refRemote::base URI change - change folder::number is valid",
    "refRemote::base URI change - change folder::string is invalid",
    "refRemote::base URI change - change folder in subschema::number is valid",
    "refRemote::base URI change - change folder in subschema::string is invalid",
    "refRemote::root ref in remote ref::string is valid",
    "refRemote::root ref in remote ref::null is valid",
    "refRemote::root ref in remote ref::object is invalid",
    "refRemote::remote ref with ref to definitions::invalid",
    "refRemote::remote ref with ref to definitions::valid",
    "refRemote::Location-independent identifier in remote ref::integer is valid",
    "refRemote::Location-independent identifier in remote ref::string is invalid",
    "refRemote::retrieved nested refs resolve relative to their URI not ${'$'}id::number is invalid",
    "refRemote::retrieved nested refs resolve relative to their URI not ${'$'}id::string is valid",
    "refRemote::${'$'}ref to ${'$'}ref finds location-independent ${'$'}id::number is valid",
    "refRemote::${'$'}ref to ${'$'}ref finds location-independent ${'$'}id::non-number is invalid"
)
