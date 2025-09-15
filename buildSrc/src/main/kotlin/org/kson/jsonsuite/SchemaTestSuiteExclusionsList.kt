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
    "refRemote::${'$'}ref to ${'$'}ref finds location-independent ${'$'}id::non-number is invalid",
    
    /**
     * These excludes are for tests that are currently failing in the Schema 2020-12 test suite.
     * These tests need further investigation and implementation work.
     */

    // anchor tests
    "anchor::Location-independent identifier with absolute URI::match",
    "anchor::Location-independent identifier with absolute URI::mismatch",
    "anchor::Location-independent identifier with base URI change in subschema::match",
    "anchor::Location-independent identifier with base URI change in subschema::mismatch",
    "anchor::Location-independent identifier::match",
    "anchor::Location-independent identifier::mismatch",
    "anchor::same ${'$'}anchor with different base uri::${'$'}ref does not resolve to /${'$'}defs/A/allOf/0",
    "anchor::same ${'$'}anchor with different base uri::${'$'}ref resolves to /${'$'}defs/A/allOf/1",

    // defs tests
    "defs::validate definition against metaschema::invalid definition schema",
    "defs::validate definition against metaschema::valid definition schema",

    // dependentRequired tests
    "dependentRequired::dependencies with escaped characters::CRLF missing dependent",
    "dependentRequired::dependencies with escaped characters::quoted quotes missing dependent",
    "dependentRequired::multiple dependents required::missing both dependencies",
    "dependentRequired::multiple dependents required::missing dependency",
    "dependentRequired::multiple dependents required::missing other dependency",
    "dependentRequired::single dependency::missing dependency",

    // dependentSchemas tests
    "dependentSchemas::boolean subschemas::object with both properties is invalid",
    "dependentSchemas::boolean subschemas::object with property having schema false is invalid",
    "dependentSchemas::dependencies with escaped characters::quoted quote",
    "dependentSchemas::dependencies with escaped characters::quoted quote invalid under dependent schema",
    "dependentSchemas::dependencies with escaped characters::quoted tab invalid under dependent schema",
    "dependentSchemas::dependent subschema incompatible with root::matches both",
    "dependentSchemas::dependent subschema incompatible with root::matches root",
    "dependentSchemas::single dependency::wrong type",
    "dependentSchemas::single dependency::wrong type both",
    "dependentSchemas::single dependency::wrong type other",

    // dynamicRef tests
    "dynamicRef::${'$'}dynamicRef points to a boolean schema::follow ${'$'}dynamicRef to a false schema",
    "dynamicRef::${'$'}dynamicRef skips over intermediate resources - direct reference::string property fails",
    "dynamicRef::${'$'}ref and ${'$'}dynamicAnchor are independent of order - ${'$'}defs first::correct extended schema",
    "dynamicRef::${'$'}ref and ${'$'}dynamicAnchor are independent of order - ${'$'}defs first::incorrect extended schema",
    "dynamicRef::${'$'}ref and ${'$'}dynamicAnchor are independent of order - ${'$'}defs first::incorrect parent schema",
    "dynamicRef::${'$'}ref and ${'$'}dynamicAnchor are independent of order - ${'$'}ref first::correct extended schema",
    "dynamicRef::${'$'}ref and ${'$'}dynamicAnchor are independent of order - ${'$'}ref first::incorrect extended schema",
    "dynamicRef::${'$'}ref and ${'$'}dynamicAnchor are independent of order - ${'$'}ref first::incorrect parent schema",
    "dynamicRef::${'$'}ref to ${'$'}dynamicRef finds detached ${'$'}dynamicAnchor::non-number is invalid",
    "dynamicRef::${'$'}ref to ${'$'}dynamicRef finds detached ${'$'}dynamicAnchor::number is valid",
    "dynamicRef::A ${'$'}dynamicRef resolves to the first ${'$'}dynamicAnchor still in scope that is encountered when the schema is evaluated::An array containing non-strings is invalid",
    "dynamicRef::A ${'$'}dynamicRef that initially resolves to a schema with a matching ${'$'}dynamicAnchor resolves to the first ${'$'}dynamicAnchor in the dynamic scope::The recursive part is not valid against the root",
    "dynamicRef::A ${'$'}dynamicRef to a ${'$'}dynamicAnchor in the same schema resource behaves like a normal ${'$'}ref to an ${'$'}anchor::An array containing non-strings is invalid",
    "dynamicRef::A ${'$'}dynamicRef to an ${'$'}anchor in the same schema resource behaves like a normal ${'$'}ref to an ${'$'}anchor::An array containing non-strings is invalid",
    "dynamicRef::A ${'$'}dynamicRef with intermediate scopes that don't include a matching ${'$'}dynamicAnchor does not affect dynamic scope resolution::An array containing non-strings is invalid",
    "dynamicRef::A ${'$'}dynamicRef without anchor in fragment behaves identical to ${'$'}ref::An array of strings is invalid",
    "dynamicRef::A ${'$'}ref to a ${'$'}dynamicAnchor in the same schema resource behaves like a normal ${'$'}ref to an ${'$'}anchor::An array containing non-strings is invalid",
    "dynamicRef::A ${'$'}ref to a ${'$'}dynamicAnchor in the same schema resource behaves like a normal ${'$'}ref to an ${'$'}anchor::An array of strings is valid",
    "dynamicRef::after leaving a dynamic scope, it is not used by a ${'$'}dynamicRef::first_scope is not in dynamic scope for the ${'$'}dynamicRef",
    "dynamicRef::after leaving a dynamic scope, it is not used by a ${'$'}dynamicRef::string matches /${'$'}defs/thingy, but the ${'$'}dynamicRef does not stop here",
    "dynamicRef::multiple dynamic paths to the ${'$'}dynamicRef keyword::number list with string values",
    "dynamicRef::multiple dynamic paths to the ${'$'}dynamicRef keyword::string list with number values",
    "dynamicRef::strict-tree schema, guards against misspelled properties::instance with correct field",
    "dynamicRef::strict-tree schema, guards against misspelled properties::instance with misspelled field",
    "dynamicRef::tests for implementation dynamic anchor and reference link::correct extended schema",
    "dynamicRef::tests for implementation dynamic anchor and reference link::incorrect extended schema",
    "dynamicRef::tests for implementation dynamic anchor and reference link::incorrect parent schema",

    // items tests
    "items::items and subitems::fewer items is valid",
    "items::items and subitems::valid items",
    "items::items with heterogeneous array::valid instance",
    "items::prefixItems validation adjusts the starting index for items::valid items",
    "items::prefixItems with no additional items allowed::equal number of items present",
    "items::prefixItems with no additional items allowed::fewer number of items present (1)",
    "items::prefixItems with no additional items allowed::fewer number of items present (2)",

    // maxContains tests
    "maxContains::maxContains with contains, value with a decimal::too many elements match, invalid maxContains",
    "maxContains::maxContains with contains::all elements match, invalid maxContains",
    "maxContains::maxContains with contains::some elements match, invalid maxContains",
    "maxContains::minContains < maxContains::minContains < maxContains < actual",

    // minContains tests
    "minContains::maxContains < minContains::invalid maxContains",
    "minContains::maxContains < minContains::invalid maxContains and minContains",
    "minContains::maxContains < minContains::invalid minContains",
    "minContains::maxContains = minContains::all elements match, invalid maxContains",
    "minContains::maxContains = minContains::all elements match, invalid minContains",
    "minContains::minContains = 0 with maxContains::empty data",
    "minContains::minContains = 0 with maxContains::too many",
    "minContains::minContains = 0::empty data",
    "minContains::minContains = 0::minContains = 0 makes contains always pass",
    "minContains::minContains=2 with contains with a decimal value::one element matches, invalid minContains",
    "minContains::minContains=2 with contains::all elements match, invalid minContains",
    "minContains::minContains=2 with contains::some elements match, invalid minContains",

    // not tests
    "not::collect annotations inside a 'not', even if collection is disabled::unevaluated property",

    // prefixItems tests
    "prefixItems::a schema given for prefixItems::wrong types",
    "prefixItems::prefixItems with boolean schemas::array with two items is invalid",

    // ref tests
    "ref::order of evaluation: ${'$'}id and ${'$'}anchor and ${'$'}ref::data is invalid against first definition",
    "ref::order of evaluation: ${'$'}id and ${'$'}anchor and ${'$'}ref::data is valid against first definition",
    "ref::ref applies alongside sibling keywords::ref valid, maxItems invalid",
    "ref::ref creates new scope when adjacent to keywords::referenced subschema doesn't see annotations from properties",
    "ref::refs with relative uris and defs::invalid on inner field",
    "ref::refs with relative uris and defs::invalid on outer field",
    "ref::relative pointer ref to array::mismatch array",
    "ref::relative refs with absolute uris and defs::invalid on inner field",
    "ref::relative refs with absolute uris and defs::invalid on outer field",
    "ref::remote ref, containing refs itself::remote ref invalid",
    "ref::remote ref, containing refs itself::remote ref valid",
    "ref::URN base URI with URN and anchor ref::a non-string is invalid",
    "ref::URN base URI with URN and anchor ref::a string is valid",
    "ref::URN ref with nested pointer ref::a non-string is invalid",

    // refRemote tests (additional)
    "refRemote::${'$'}ref to ${'$'}ref finds detached ${'$'}anchor::non-number is invalid",
    "refRemote::${'$'}ref to ${'$'}ref finds detached ${'$'}anchor::number is valid",
    "refRemote::anchor within remote ref::remote anchor invalid",
    "refRemote::anchor within remote ref::remote anchor valid",
    "refRemote::remote HTTP ref with different ${'$'}id::number is invalid",
    "refRemote::remote HTTP ref with different ${'$'}id::string is valid",
    "refRemote::remote HTTP ref with different URN ${'$'}id::number is invalid",
    "refRemote::remote HTTP ref with different URN ${'$'}id::string is valid",
    "refRemote::remote HTTP ref with nested absolute ref::number is invalid",
    "refRemote::remote HTTP ref with nested absolute ref::string is valid",
    "refRemote::remote ref with ref to defs::invalid",
    "refRemote::remote ref with ref to defs::valid",

    // unevaluatedItems tests
    "unevaluatedItems::item is evaluated in an uncle schema to unevaluatedItems::uncle keyword evaluation is not significant",
    "unevaluatedItems::unevaluatedItems and contains interact to control item dependency relationship::only a's and c's are invalid",
    "unevaluatedItems::unevaluatedItems and contains interact to control item dependency relationship::only b's and c's are invalid",
    "unevaluatedItems::unevaluatedItems and contains interact to control item dependency relationship::only b's are invalid",
    "unevaluatedItems::unevaluatedItems and contains interact to control item dependency relationship::only c's are invalid",
    "unevaluatedItems::unevaluatedItems as schema::with invalid unevaluated items",
    "unevaluatedItems::unevaluatedItems before ${'$'}ref::with unevaluated items",
    "unevaluatedItems::unevaluatedItems can see annotations from if without then and else::invalid in case if is evaluated",
    "unevaluatedItems::unevaluatedItems can't see inside cousins::always fails",
    "unevaluatedItems::unevaluatedItems depends on adjacent contains::contains passes, second item is not evaluated",
    "unevaluatedItems::unevaluatedItems depends on multiple nested contains::7 not evaluated, fails unevaluatedItems",
    "unevaluatedItems::unevaluatedItems false::with unevaluated items",
    "unevaluatedItems::unevaluatedItems with ${'$'}dynamicRef::with unevaluated items",
    "unevaluatedItems::unevaluatedItems with ${'$'}ref::with unevaluated items",
    "unevaluatedItems::unevaluatedItems with anyOf::when one schema matches and has unevaluated items",
    "unevaluatedItems::unevaluatedItems with anyOf::when two schemas match and has unevaluated items",
    "unevaluatedItems::unevaluatedItems with boolean schemas::with unevaluated items",
    "unevaluatedItems::unevaluatedItems with if/then/else::when if doesn't match and it has unevaluated items",
    "unevaluatedItems::unevaluatedItems with if/then/else::when if matches and it has unevaluated items",
    "unevaluatedItems::unevaluatedItems with nested items::with invalid additional item",
    "unevaluatedItems::unevaluatedItems with nested tuple::with unevaluated items",
    "unevaluatedItems::unevaluatedItems with not::with unevaluated items",
    "unevaluatedItems::unevaluatedItems with oneOf::with no unevaluated items",
    "unevaluatedItems::unevaluatedItems with tuple::with unevaluated items",

    // unevaluatedProperties tests
    "unevaluatedProperties::cousin unevaluatedProperties, true and false, false with properties::with nested unevaluated properties",
    "unevaluatedProperties::cousin unevaluatedProperties, true and false, true with properties::with nested unevaluated properties",
    "unevaluatedProperties::cousin unevaluatedProperties, true and false, true with properties::with no nested unevaluated properties",
    "unevaluatedProperties::dependentSchemas with unevaluatedProperties::unevaluatedProperties doesn't consider dependentSchemas",
    "unevaluatedProperties::dependentSchemas with unevaluatedProperties::unevaluatedProperties doesn't see bar when foo2 is absent",
    "unevaluatedProperties::dynamic evalation inside nested refs::xx + foo is invalid",
    "unevaluatedProperties::in-place applicator siblings, allOf has unevaluated::base case: both properties present",
    "unevaluatedProperties::in-place applicator siblings, allOf has unevaluated::in place applicator siblings, foo is missing",
    "unevaluatedProperties::in-place applicator siblings, anyOf has unevaluated::base case: both properties present",
    "unevaluatedProperties::in-place applicator siblings, anyOf has unevaluated::in place applicator siblings, bar is missing",
    "unevaluatedProperties::nested unevaluatedProperties, outer true, inner false, properties inside::with nested unevaluated properties",
    "unevaluatedProperties::nested unevaluatedProperties, outer true, inner false, properties outside::with nested unevaluated properties",
    "unevaluatedProperties::nested unevaluatedProperties, outer true, inner false, properties outside::with no nested unevaluated properties",
    "unevaluatedProperties::property is evaluated in an uncle schema to unevaluatedProperties::uncle keyword evaluation is not significant",
    "unevaluatedProperties::unevaluatedProperties + single cyclic ref::Unevaluated on 1st level is invalid",
    "unevaluatedProperties::unevaluatedProperties + single cyclic ref::Unevaluated on 2nd level is invalid",
    "unevaluatedProperties::unevaluatedProperties + single cyclic ref::Unevaluated on 3rd level is invalid",
    "unevaluatedProperties::unevaluatedProperties before ${'$'}ref::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties can see annotations from if without then and else::invalid in case if is evaluated",
    "unevaluatedProperties::unevaluatedProperties can't see inside cousins (reverse order)::always fails",
    "unevaluatedProperties::unevaluatedProperties can't see inside cousins::always fails",
    "unevaluatedProperties::unevaluatedProperties false::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties not affected by propertyNames::string property is invalid",
    "unevaluatedProperties::unevaluatedProperties schema::with invalid unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with ${'$'}dynamicRef::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with ${'$'}ref::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with adjacent patternProperties::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with adjacent properties::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with anyOf::when one matches and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with anyOf::when two match and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with boolean schemas::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with dependentSchemas::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else, else not defined::when if is false and has no unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else, else not defined::when if is false and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else, else not defined::when if is true and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else, then not defined::when if is false and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else, then not defined::when if is true and has no unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else, then not defined::when if is true and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else::when if is false and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with if/then/else::when if is true and has unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with nested patternProperties::with additional properties",
    "unevaluatedProperties::unevaluatedProperties with nested properties::with additional properties",
    "unevaluatedProperties::unevaluatedProperties with not::with unevaluated properties",
    "unevaluatedProperties::unevaluatedProperties with oneOf::with unevaluated properties",

    // uniqueItems tests
    "uniqueItems::uniqueItems with an array of items and additionalItems=false::[false, true] from items array is valid",
    "uniqueItems::uniqueItems with an array of items and additionalItems=false::[true, false] from items array is valid",
    "uniqueItems::uniqueItems=false with an array of items and additionalItems=false::[false, false] from items array is valid",
    "uniqueItems::uniqueItems=false with an array of items and additionalItems=false::[false, true] from items array is valid",
    "uniqueItems::uniqueItems=false with an array of items and additionalItems=false::[true, false] from items array is valid",
    "uniqueItems::uniqueItems=false with an array of items and additionalItems=false::[true, true] from items array is valid",

    // vocabulary tests
    "vocabulary::schema that uses custom metaschema with with no validation vocabulary::no validation: invalid number, but it still validates"
)
