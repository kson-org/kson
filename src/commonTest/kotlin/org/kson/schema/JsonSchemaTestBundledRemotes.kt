package org.kson.schema

import kotlin.test.Test

/**
 * These tests are ["bundled"/Compound Schema](https://json-schema.org/blog/posts/bundling-json-schema-compound-documents)
 * ports of the [org.kson.parser.json.generated.SchemaSuiteTest] tests which require fetching remote schemas.  Those
 * remote fetching tests are excluded in [org.kson.jsonsuite.schemaTestSuiteExclusions] because we do not current
 * support remote fetching. These tests let us have most of that coverage while also doing the the important work
 * of ensuring our Schema $id and $ref code effectively navigates compound schemas
 */
class JsonSchemaTestBundledRemotes : JsonSchemaTest {
    /**
     * "Bundled" version of [org.kson.parser.json.generated.SchemaSuiteTest.refRemote_______refTo______refFindsLocation_independent______id_non_numberIsInvalid]
     */
    @Test
    fun bundled_______refTo______refFindsLocation_independent______id_non_numberIsInvalid() {
        assertKsonEnforcesSchema(
            """
                "a"
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/draft7/detached-ref.json#/definitions/foo",
                  "definitions": {
                    "http://localhost:1234/draft7/detached-ref.json": {
                      "${'$'}schema": "http://json-schema.org/draft-07/schema#",
                      "${'$'}id": "http://localhost:1234/draft7/detached-ref.json",
                      "definitions": {
                        "detached": {
                          "${'$'}id": "#detached",
                          "type": "integer"
                        },
                        "foo": {
                          "${'$'}ref": "#detached"
                        }
                      }
                    }
                  }
                }
            """,
            false,
            """${'$'}ref to ${'$'}ref finds location-independent ${'$'}id -> non-number is invalid"""
        )
    }

    // Remote ref tests
    @Test
    fun bundled_remoteRef_remoteRefValid() {
        assertKsonEnforcesSchema(
            """
                1
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/integer.json",
                  "definitions": {
                    "http://localhost:1234/integer.json": {
                      "${'$'}id": "http://localhost:1234/integer.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            true,
            """remote ref -> remote ref valid"""
        )
    }

    @Test
    fun bundled_remoteRef_remoteRefInvalid() {
        assertKsonEnforcesSchema(
            """
                "a"
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/integer.json",
                  "definitions": {
                    "http://localhost:1234/integer.json": {
                      "${'$'}id": "http://localhost:1234/integer.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            false,
            """remote ref -> remote ref invalid"""
        )
    }

    // Fragment within remote ref tests
    @Test
    fun bundled_fragmentWithinRemoteRef_remoteFragmentValid() {
        assertKsonEnforcesSchema(
            """
                1
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/subSchemas.json#/definitions/integer",
                  "definitions": {
                    "http://localhost:1234/subSchemas.json": {
                      "${'$'}id": "http://localhost:1234/subSchemas.json",
                      "definitions": {
                        "integer": {
                          "type": "integer"
                        },
                        "refToInteger": {
                          "${'$'}ref": "#/definitions/integer"
                        }
                      }
                    }
                  }
                }
            """,
            true,
            """fragment within remote ref -> remote fragment valid"""
        )
    }

    @Test
    fun bundled_fragmentWithinRemoteRef_remoteFragmentInvalid() {
        assertKsonEnforcesSchema(
            """
                "a"
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/subSchemas.json#/definitions/integer",
                  "definitions": {
                    "http://localhost:1234/subSchemas.json": {
                      "${'$'}id": "http://localhost:1234/subSchemas.json",
                      "definitions": {
                        "integer": {
                          "type": "integer"
                        },
                        "refToInteger": {
                          "${'$'}ref": "#/definitions/integer"
                        }
                      }
                    }
                  }
                }
            """,
            false,
            """fragment within remote ref -> remote fragment invalid"""
        )
    }

    // Ref within remote ref tests
    @Test
    fun bundled_refWithinRemoteRef_refWithinRefValid() {
        assertKsonEnforcesSchema(
            """
                1
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/subSchemas.json#/definitions/refToInteger",
                  "definitions": {
                    "http://localhost:1234/subSchemas.json": {
                      "${'$'}id": "http://localhost:1234/subSchemas.json",
                      "definitions": {
                        "integer": {
                          "type": "integer"
                        },
                        "refToInteger": {
                          "${'$'}ref": "#/definitions/integer"
                        }
                      }
                    }
                  }
                }
            """,
            true,
            """ref within remote ref -> ref within ref valid"""
        )
    }

    @Test
    fun bundled_refWithinRemoteRef_refWithinRefInvalid() {
        assertKsonEnforcesSchema(
            """
                "a"
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/subSchemas.json#/definitions/refToInteger",
                  "definitions": {
                    "http://localhost:1234/subSchemas.json": {
                      "${'$'}id": "http://localhost:1234/subSchemas.json",
                      "definitions": {
                        "integer": {
                          "type": "integer"
                        },
                        "refToInteger": {
                          "${'$'}ref": "#/definitions/integer"
                        }
                      }
                    }
                  }
                }
            """,
            false,
            """ref within remote ref -> ref within ref invalid"""
        )
    }

    // Base URI change tests
    @Test
    fun bundled_baseURIChange_baseURIChangeRefValid() {
        assertKsonEnforcesSchema(
            """
                [
                    [
                        1
                    ]
                ]
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/",
                  "items": {
                    "${'$'}id": "baseUriChange/",
                    "items": {
                      "${'$'}ref": "folderInteger.json"
                    }
                  },
                  "definitions": {
                    "http://localhost:1234/baseUriChange/folderInteger.json": {
                      "${'$'}id": "http://localhost:1234/baseUriChange/folderInteger.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            true,
            """base URI change -> base URI change ref valid"""
        )
    }

    @Test
    fun bundled_baseURIChange_baseURIChangeRefInvalid() {
        assertKsonEnforcesSchema(
            """
                [
                    [
                        "a"
                    ]
                ]
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/",
                  "items": {
                    "${'$'}id": "baseUriChange/",
                    "items": {
                      "${'$'}ref": "folderInteger.json"
                    },
                    "definitions": {
                      "folderInteger.json": {
                        "${'$'}id": "folderInteger.json",
                        "type": "integer"
                      }
                    }
                  }
                }
            """,
            false,
            """base URI change -> base URI change ref invalid"""
        )
    }

    // Base URI change - change folder tests
    @Test
    fun bundled_baseURIChange_ChangeFolder_numberIsValid() {
        assertKsonEnforcesSchema(
            """
                {
                    "list": [
                        1
                    ]
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/scope_change_defs1.json",
                  "type": "object",
                  "properties": {
                    "list": {
                      "${'$'}ref": "#/definitions/baz"
                    }
                  },
                  "definitions": {
                    "baz": {
                      "${'$'}id": "baseUriChangeFolder/",
                      "type": "array",
                      "items": {
                        "${'$'}ref": "folderInteger.json"
                      }
                    },
                    "http://localhost:1234/baseUriChangeFolder/folderInteger.json": {
                      "${'$'}id": "http://localhost:1234/baseUriChangeFolder/folderInteger.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            true,
            """base URI change - change folder -> number is valid"""
        )
    }

    @Test
    fun bundled_baseURIChange_ChangeFolder_stringIsInvalid() {
        assertKsonEnforcesSchema(
            """
                {
                    "list": [
                        "a"
                    ]
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/scope_change_defs1.json",
                  "type": "object",
                  "properties": {
                    "list": {
                      "${'$'}ref": "#/definitions/baz"
                    }
                  },
                  "definitions": {
                    "baz": {
                      "${'$'}id": "baseUriChangeFolder/",
                      "type": "array",
                      "items": {
                        "${'$'}ref": "folderInteger.json"
                      }
                    },
                    "http://localhost:1234/baseUriChangeFolder/folderInteger.json": {
                      "${'$'}id": "http://localhost:1234/baseUriChangeFolder/folderInteger.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            false,
            """base URI change - change folder -> string is invalid"""
        )
    }

    // Base URI change - change folder in subschema tests
    @Test
    fun bundled_baseURIChange_ChangeFolderInSubschema_numberIsValid() {
        assertKsonEnforcesSchema(
            """
                {
                    "list": [
                        1
                    ]
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/scope_change_defs2.json",
                  "type": "object",
                  "properties": {
                    "list": {
                      "${'$'}ref": "#/definitions/baz/definitions/bar"
                    }
                  },
                  "definitions": {
                    "baz": {
                      "${'$'}id": "baseUriChangeFolderInSubschema/",
                      "definitions": {
                        "bar": {
                          "type": "array",
                          "items": {
                            "${'$'}ref": "folderInteger.json"
                          }
                        }
                      }
                    },
                    "http://localhost:1234/baseUriChangeFolderInSubschema/folderInteger.json": {
                      "${'$'}id": "http://localhost:1234/baseUriChangeFolderInSubschema/folderInteger.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            true,
            """base URI change - change folder in subschema -> number is valid"""
        )
    }

    @Test
    fun bundled_baseURIChange_ChangeFolderInSubschema_stringIsInvalid() {
        assertKsonEnforcesSchema(
            """
                {
                    "list": [
                        "a"
                    ]
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/scope_change_defs2.json",
                  "type": "object",
                  "properties": {
                    "list": {
                      "${'$'}ref": "#/definitions/baz/definitions/bar"
                    }
                  },
                  "definitions": {
                    "baz": {
                      "${'$'}id": "baseUriChangeFolderInSubschema/",
                      "definitions": {
                        "bar": {
                          "type": "array",
                          "items": {
                            "${'$'}ref": "folderInteger.json"
                          }
                        }
                      }
                    },
                    "http://localhost:1234/baseUriChangeFolderInSubschema/folderInteger.json": {
                      "${'$'}id": "http://localhost:1234/baseUriChangeFolderInSubschema/folderInteger.json",
                      "type": "integer"
                    }
                  }
                }
            """,
            false,
            """base URI change - change folder in subschema -> string is invalid"""
        )
    }

    // Location-independent identifier tests
    @Test
    fun bundled_location_independentIdentifierInRemoteRef_integerIsValid() {
        assertKsonEnforcesSchema(
            """
                1
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/locationIndependentIdentifierPre2019.json#/definitions/refToInteger",
                  "definitions": {
                    "http://localhost:1234/locationIndependentIdentifierPre2019.json": {
                      "${'$'}id": "http://localhost:1234/locationIndependentIdentifierPre2019.json",
                      "definitions": {
                        "refToInteger": {
                          "${'$'}ref": "#foo"
                        },
                        "A": {
                          "${'$'}id": "#foo",
                          "type": "integer"
                        }
                      }
                    }
                  }
                }
            """,
            true,
            """Location-independent identifier in remote ref -> integer is valid"""
        )
    }

    @Test
    fun bundled_location_independentIdentifierInRemoteRef_stringIsInvalid() {
        assertKsonEnforcesSchema(
            """
                "foo"
            """,
            """
                {
                  "${'$'}ref": "http://localhost:1234/locationIndependentIdentifierPre2019.json#/definitions/refToInteger",
                  "definitions": {
                    "http://localhost:1234/locationIndependentIdentifierPre2019.json": {
                      "${'$'}id": "http://localhost:1234/locationIndependentIdentifierPre2019.json",
                      "definitions": {
                        "refToInteger": {
                          "${'$'}ref": "#foo"
                        },
                        "A": {
                          "${'$'}id": "#foo",
                          "type": "integer"
                        }
                      }
                    }
                  }
                }
            """,
            false,
            """Location-independent identifier in remote ref -> string is invalid"""
        )
    }

    // Remote ref with ref to definitions tests
    @Test
    fun bundled_remoteRefWithRefToDefinitions_valid() {
        assertKsonEnforcesSchema(
            """
                {
                    "bar": "a"
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/schema-remote-ref-ref-defs1.json",
                  "allOf": [
                    {
                      "${'$'}ref": "ref-and-definitions.json"
                    }
                  ],
                  "definitions": {
                    "http://localhost:1234/ref-and-definitions.json": {
                      "${'$'}id": "http://localhost:1234/ref-and-definitions.json",
                      "definitions": {
                        "inner": {
                          "properties": {
                            "bar": { "type": "string" }
                          }
                        }
                      },
                      "allOf": [ { "${'$'}ref": "#/definitions/inner" } ]
                    }
                  }
                }
            """,
            true,
            """remote ref with ref to definitions -> valid"""
        )
    }

    @Test
    fun bundled_remoteRefWithRefToDefinitions_invalid() {
        assertKsonEnforcesSchema(
            """
                {
                    "bar": 1
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/schema-remote-ref-ref-defs1.json",
                  "allOf": [
                    {
                      "${'$'}ref": "ref-and-definitions.json"
                    }
                  ],
                  "definitions": {
                    "http://localhost:1234/ref-and-definitions.json": {
                      "${'$'}id": "http://localhost:1234/ref-and-definitions.json",
                      "definitions": {
                        "inner": {
                          "properties": {
                            "bar": { "type": "string" }
                          }
                        }
                      },
                      "allOf": [ { "${'$'}ref": "#/definitions/inner" } ]
                    }
                  }
                }
            """,
            false,
            """remote ref with ref to definitions -> invalid"""
        )
    }

    // Retrieved nested refs tests
    @Test
    fun bundled_retrievedNestedRefsResolveRelativeToTheirURINot______id_stringIsValid() {
        assertKsonEnforcesSchema(
            """
                {
                    "name": {
                        "foo": "a"
                    }
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/some-id",
                  "properties": {
                    "name": {
                      "${'$'}ref": "nested/foo-ref-string.json"
                    }
                  },
                  "definitions": {
                    "http://localhost:1234/nested/foo-ref-string.json": {
                      "${'$'}id": "http://localhost:1234/nested/foo-ref-string.json",
                      "type": "object",
                      "properties": {
                        "foo": {"${'$'}ref": "string.json"}
                      }
                    },
                    "http://localhost:1234/nested/string.json": {
                      "${'$'}id": "http://localhost:1234/nested/string.json",
                      "type": "string"
                    }
                  }
                }
            """,
            true,
            """retrieved nested refs resolve relative to their URI not ${'$'}id -> string is valid"""
        )
    }

    @Test
    fun bundled_retrievedNestedRefsResolveRelativeToTheirURINot______id_numberIsInvalid() {
        assertKsonEnforcesSchema(
            """
                {
                    "name": {
                        "foo": 1
                    }
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/some-id",
                  "properties": {
                    "name": {
                      "${'$'}ref": "nested/foo-ref-string.json"
                    }
                  },
                  "definitions": {
                    "http://localhost:1234/nested/foo-ref-string.json": {
                      "${'$'}id": "http://localhost:1234/nested/foo-ref-string.json",
                      "type": "object",
                      "properties": {
                        "foo": {"${'$'}ref": "string.json"}
                      }
                    },
                    "http://localhost:1234/nested/string.json": {
                      "${'$'}id": "http://localhost:1234/nested/string.json",
                      "type": "string"
                    }
                  }
                }
            """,
            false,
            """retrieved nested refs resolve relative to their URI not ${'$'}id -> number is invalid"""
        )
    }

    // Root ref in remote ref tests
    @Test
    fun bundled_rootRefInRemoteRef_stringIsValid() {
        assertKsonEnforcesSchema(
            """
                {
                    "name": "foo"
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/object",
                  "type": "object",
                  "properties": {
                    "name": {
                      "${'$'}ref": "name.json#/definitions/orNull"
                    }
                  },
                  "definitions": {
                    "http://localhost:1234/name.json": {
                      "${'$'}id": "http://localhost:1234/name.json",
                      "definitions": {
                        "orNull": {
                          "anyOf": [
                            {
                              "type": "null"
                            },
                            {
                              "${'$'}ref": "#"
                            }
                          ]
                        }
                      },
                      "type": "string"
                    }
                  }
                }
            """,
            true,
            """root ref in remote ref -> string is valid"""
        )
    }

    @Test
    fun bundled_rootRefInRemoteRef_nullIsValid() {
        assertKsonEnforcesSchema(
            """
                {
                    "name": null
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/object",
                  "type": "object",
                  "properties": {
                    "name": {
                      "${'$'}ref": "name.json#/definitions/orNull"
                    }
                  },
                  "definitions": {
                    "http://localhost:1234/name.json": {
                      "${'$'}id": "http://localhost:1234/name.json",
                      "definitions": {
                        "orNull": {
                          "anyOf": [
                            {
                              "type": "null"
                            },
                            {
                              "${'$'}ref": "#"
                            }
                          ]
                        }
                      },
                      "type": "string"
                    }
                  }
                }
            """,
            true,
            """root ref in remote ref -> null is valid"""
        )
    }

    @Test
    fun bundled_rootRefInRemoteRef_objectIsInvalid() {
        assertKsonEnforcesSchema(
            """
                {
                    "name": {
                        "name": null
                    }
                }
            """,
            """
                {
                  "${'$'}id": "http://localhost:1234/object",
                  "type": "object",
                  "properties": {
                    "name": {
                      "${'$'}ref": "name.json#/definitions/orNull"
                    }
                  },
                  "definitions": {
                    "http://localhost:1234/name.json": {
                      "${'$'}id": "http://localhost:1234/name.json",
                      "definitions": {
                        "orNull": {
                          "anyOf": [
                            {
                              "type": "null"
                            },
                            {
                              "${'$'}ref": "#"
                            }
                          ]
                        }
                      },
                      "type": "string"
                    }
                  }
                }
            """,
            false,
            """root ref in remote ref -> object is invalid"""
        )
    }
}
