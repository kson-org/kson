package org.kson.schema

import org.kson.schema.validators.*
import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonString

/**
 * A JSON Schema draft, described by its keywords, its meta-schemas and whether a `$ref` hides the keywords beside it.
 *
 * @param refHidesSiblings Whether a string `$ref` hides every keyword beside it.  That includes `$id`, so an `$id`
 *   beside such a `$ref` doesn't identify its schema.
 * @param keywords The keywords the draft compiles, in the order validation applies them and reports their errors
 * @param metaSchemas The draft's meta-schemas, which a schema can reference without supplying them.  It is a function
 *   so that they are only parsed when needed.
 * @throws IllegalArgumentException if two of [keywords] read the same property
 */
internal class SchemaDraft(
    val refHidesSiblings: Boolean,
    val keywords: List<SchemaKeyword>,
    val metaSchemas: () -> List<SchemaDocument>
) {
    /** The keyword in [keywords] that reads each property, by property name */
    val keywordsByName: Map<String, SchemaKeyword> = buildMap {
        for (keyword in keywords) {
            for (name in keyword.names) {
                require(put(name, keyword) == null) { "Two keywords read `$name`" }
            }
        }
    }

    companion object {
        val DRAFT_7 = SchemaDraft(
            refHidesSiblings = true,
            keywords = listOf(
                RefKeyword,
                DefinitionsKeyword("definitions"),
                NumberKeyword("minimum", ::MinimumValidator),
                NumberKeyword("maximum", ::MaximumValidator),
                NumberKeyword("multipleOf", ::MultipleOfValidator),
                NumberKeyword("exclusiveMinimum", ::ExclusiveMinimumValidator),
                NumberKeyword("exclusiveMaximum", ::ExclusiveMaximumValidator),
                IntegerKeyword("minLength", ::MinLengthValidator),
                IntegerKeyword("maxLength", ::MaxLengthValidator),
                PatternKeyword,
                EnumKeyword,
                ItemsKeyword,
                SubschemaKeyword("contains", Applies.SomeItem, ::ContainsValidator),
                IntegerKeyword("minItems", ::MinItemsValidator),
                IntegerKeyword("maxItems", ::MaxItemsValidator),
                UniqueItemsKeyword,
                PropertiesKeyword,
                RequiredKeyword,
                IntegerKeyword("minProperties", ::MinPropertiesValidator),
                IntegerKeyword("maxProperties", ::MaxPropertiesValidator),
                ConstKeyword,
                SchemaListKeyword("allOf", { Applies.InPlace }, ::AllOfValidator),
                SchemaListKeyword("anyOf", Applies::Alternative, ::AnyOfValidator),
                SchemaListKeyword("oneOf", Applies::Alternative, ::OneOfValidator),
                SubschemaKeyword("not", Applies.Negated, ::NotValidator),
                IfKeyword,
                DependenciesKeyword,
                SubschemaKeyword("propertyNames", Applies.PropertyNames, ::PropertyNamesValidator)
            ),
            metaSchemas = {
                listOf(SchemaDocument(KsonDraft7MetaSchema.schemaValue, UriReference.parse(KsonDraft7MetaSchema.ID)))
            }
        )
    }
}

internal fun hasHidingRef(schemaObject: KsonObject, draft: SchemaDraft): Boolean =
    draft.refHidesSiblings && schemaObject.propertyLookup["\$ref"] is KsonString
