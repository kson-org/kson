package org.kson.schema

import org.kson.parser.messages.MessageType.*
import org.kson.schema.validators.*
import org.kson.value.*

/** The keywords Draft 7 compiles, in the order validation applies them and reports their errors */
internal val draft7Keywords: List<SchemaKeyword> = listOf(
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
)

internal object RefKeyword : SchemaKeyword(setOf($$"$ref")) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val ref = context.properties.getValue($$"$ref")
        if (ref !is KsonString) {
            context.messageSink.error(ref.location, SCHEMA_STRING_REQUIRED.create($$"$ref"))
            return null
        }
        val target = when (val resolution = context.resolveRef(ref.value)) {
            is RefResolution.Found -> resolution.target
            is RefResolution.Failure -> {
                context.rejectSchema(UnresolvedRef.MissingTarget(ref, resolution))
                return null
            }
        }
        return RefValidator(target, ref.value)
    }
}

/**
 * A keyword [name] whose subschemas validate nothing themselves and are only there for `$ref`s to point to, like
 * `definitions`.  They are still compiled, to report errors in them.
 */
internal class DefinitionsKeyword(private val name: String) : SchemaKeyword(setOf(name)) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> =
        (schema.propertyLookup[name] as? KsonObject)?.propertyLookup.orEmpty()
            .map { (key, definition) -> DeclaredSubschema(listOf(name, key), definition, Applies.NotApplied) }

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val definitions = context.properties.getValue(name)
        if (definitions !is KsonObject) {
            context.messageSink.error(definitions.location, SCHEMA_OBJECT_REQUIRED.create(name))
        }
        subschemas.forEach { it.compiled }
        return null
    }
}

internal class NumberKeyword(
    private val name: String,
    private val factory: (Double) -> JsonSchemaValidator
) : SchemaKeyword(setOf(name)) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val value = context.properties.getValue(name)
        if (value !is KsonNumber) {
            context.messageSink.error(value.location, SCHEMA_NUMBER_REQUIRED.create(name))
            return null
        }
        return factory(value.value.asDouble)
    }
}

/**
 * A keyword [name] whose value must be an integer (see [asSchemaInteger]), which [factory] turns into a validator.  A
 * number that isn't an integer gives no validator and no error.
 */
internal class IntegerKeyword(
    private val name: String,
    private val factory: (Long) -> JsonSchemaValidator
) : SchemaKeyword(setOf(name)) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val value = context.properties.getValue(name)
        if (value !is KsonNumber) {
            context.messageSink.error(value.location, SCHEMA_INTEGER_REQUIRED.create(name))
            return null
        }
        return asSchemaInteger(value)?.let(factory)
    }
}

internal object PatternKeyword : SchemaKeyword(setOf("pattern")) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val pattern = context.properties.getValue("pattern")
        if (pattern !is KsonString) {
            context.messageSink.error(pattern.location, SCHEMA_STRING_REQUIRED.create("pattern"))
            return null
        }
        return runCatching { Regex(pattern.value) }
            .onFailure { context.messageSink.error(pattern.location, SCHEMA_INVALID_REGEX.create("pattern", pattern.value)) }
            .getOrNull()
            ?.let(::PatternValidator)
    }
}

internal object EnumKeyword : SchemaKeyword(setOf("enum")) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val enum = context.properties.getValue("enum")
        if (enum !is KsonList) {
            context.messageSink.error(enum.location, SCHEMA_ARRAY_REQUIRED.create("enum"))
            return null
        }
        return EnumValidator(enum)
    }
}

internal object ConstKeyword : SchemaKeyword(setOf("const")) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator =
        ConstValidator(context.properties.getValue("const"))
}

internal object UniqueItemsKeyword : SchemaKeyword(setOf("uniqueItems")) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val uniqueItems = context.properties.getValue("uniqueItems")
        if (uniqueItems !is KsonBoolean) {
            context.messageSink.error(uniqueItems.location, SCHEMA_BOOLEAN_REQUIRED.create("uniqueItems"))
            return null
        }
        return UniqueItemsValidator(uniqueItems.value)
    }
}

internal object RequiredKeyword : SchemaKeyword(setOf("required")) {
    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val required = context.properties.getValue("required")
        if (required !is KsonList) {
            context.messageSink.error(required.location, SCHEMA_ARRAY_REQUIRED.create("required"))
            return null
        }
        val requiredArrayEntries = ArrayList<KsonString>()
        for (element in required.elements) {
            if (element is KsonString) {
                requiredArrayEntries.add(element)
            } else {
                context.messageSink.error(element.location, SCHEMA_STRING_ARRAY_ENTRY_ERROR.create("required"))
            }
        }
        return RequiredValidator(requiredArrayEntries)
    }
}

/** A keyword [name] whose value is a subschema, such as `not`. */
internal class SubschemaKeyword(
    private val name: String,
    private val applies: Applies,
    private val factory: (JsonSchema) -> JsonSchemaValidator
) : SchemaKeyword(setOf(name)) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> =
        listOfNotNull(schema.propertyLookup[name]?.let { DeclaredSubschema(listOf(name), it, applies) })

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? =
        subschemas.single().compiled?.let(factory)
}

/**
 * A keyword [name] whose value is a list of subschemas, such as `allOf`.  [applies] gives the part of the instance
 * that the subschema at each index validates.
 */
internal class SchemaListKeyword(
    private val name: String,
    private val applies: (Int) -> Applies,
    private val factory: (List<JsonSchema>) -> JsonSchemaValidator
) : SchemaKeyword(setOf(name)) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> =
        (schema.propertyLookup[name] as? KsonList)?.elements.orEmpty()
            .mapIndexed { index, element -> DeclaredSubschema(listOf(name, "$index"), element, applies(index)) }

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val value = context.properties.getValue(name)
        if (value !is KsonList) {
            context.messageSink.error(value.location, SCHEMA_ARRAY_REQUIRED.create(name))
            return null
        }
        return factory(subschemas.mapNotNull { it.compiled })
    }
}

/**
 * `items` and `additionalItems`.  When `items` is a list, each of its schemas validates the item at the same index,
 * and `additionalItems` validates the items after those.  Any other `items` validates every item.  Without a list of
 * `items`, `additionalItems` validates nothing.
 */
internal object ItemsKeyword : SchemaKeyword(setOf("items", "additionalItems")) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> {
        val items = schema.propertyLookup["items"]
        val itemSubschemas = when (items) {
            null -> emptyList()
            is KsonList -> items.elements.mapIndexed { index, item ->
                DeclaredSubschema(listOf("items", "$index"), item, Applies.Item(index))
            }
            else -> listOf(DeclaredSubschema(listOf("items"), items, Applies.ItemsFrom(0)))
        }
        val additionalItems = schema.propertyLookup["additionalItems"]?.let {
            val applies = if (items is KsonList) Applies.ItemsFrom(items.elements.size) else Applies.NotApplied
            DeclaredSubschema(listOf("additionalItems"), it, applies)
        }
        return itemSubschemas + listOfNotNull(additionalItems)
    }

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val items = context.properties["items"] ?: return null
        val additionalItems = subschemas.singleOrNull { it.keyword == "additionalItems" }
        if (items is KsonList) {
            val tupleSchemas = subschemas.filter { it.keyword == "items" }.mapNotNull { it.compiled }
            return ItemsValidator(LeadingItemsTupleValidator(tupleSchemas), additionalItems(additionalItems, tupleSchemas.size))
        }
        val itemsSchema = subschemas.first { it.keyword == "items" }.compiled ?: return null
        return ItemsValidator(LeadingItemsSchemaValidator(itemsSchema), additionalItems(additionalItems, 0))
    }

    private fun additionalItems(subschema: CompilableSubschema?, tupleLength: Int): AdditionalItemsValidator? {
        val value = subschema?.value ?: return null
        return if (value is KsonBoolean) {
            AdditionalItemsBooleanValidator(value.value, tupleLength)
        } else {
            subschema.compiled?.let(::AdditionalItemsSchemaValidator)
        }
    }
}

/**
 * `properties`, `patternProperties` and `additionalProperties`, which together decide which subschemas validate each
 * of the instance's properties.  `additionalProperties` validates the properties that the other two don't match.
 */
internal object PropertiesKeyword : SchemaKeyword(setOf("properties", "patternProperties", "additionalProperties")) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> {
        val properties = (schema.propertyLookup["properties"] as? KsonObject)?.propertyLookup.orEmpty()
            .map { (name, value) -> DeclaredSubschema(listOf("properties", name), value, Applies.Property(name)) }
        val patternProperties = (schema.propertyLookup["patternProperties"] as? KsonObject)?.propertyLookup.orEmpty()
            .map { (pattern, value) ->
                DeclaredSubschema(listOf("patternProperties", pattern), value, Applies.PatternProperty(pattern))
            }
        val additionalProperties = schema.propertyLookup["additionalProperties"]
            ?.let { DeclaredSubschema(listOf("additionalProperties"), it, Applies.OtherProperties) }
        return properties + patternProperties + listOfNotNull(additionalProperties)
    }

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val propertySchemas = context.properties["properties"]?.let { properties ->
            if (properties is KsonObject) {
                subschemas.filter { it.keyword == "properties" }
                    .associate { (it.applies as Applies.Property).name to it.compiled }
            } else {
                context.messageSink.error(properties.location, SCHEMA_OBJECT_REQUIRED.create("properties"))
                null
            }
        }

        val compiledPatterns = context.properties["patternProperties"]?.let { patternProperties ->
            if (patternProperties is KsonObject) {
                subschemas.filter { it.keyword == "patternProperties" }.mapNotNull { subschema ->
                    val pattern = (subschema.applies as Applies.PatternProperty).pattern
                    runCatching { Regex(pattern) }
                        .onFailure {
                            val patternLocation = patternProperties.propertyMap.getValue(pattern).propName.location
                            context.messageSink.error(patternLocation, SCHEMA_INVALID_REGEX.create("patternProperties", pattern))
                        }
                        .getOrNull()
                        ?.let { CompiledPatternSchema(it, subschema.compiled) }
                }
            } else {
                context.messageSink.error(patternProperties.location, SCHEMA_OBJECT_REQUIRED.create("patternProperties"))
                null
            }
        }

        val additionalPropertiesValidator = subschemas.singleOrNull { it.keyword == "additionalProperties" }?.let { subschema ->
            when (val additionalProperties = subschema.value) {
                is KsonBoolean -> AdditionalPropertiesBooleanValidator(
                    additionalProperties.value,
                    context.title,
                    propertySchemas.orEmpty().keys.toList(),
                    compiledPatterns.orEmpty().map { it.regex.pattern })
                else -> subschema.compiled?.let(::AdditionalPropertiesSchemaValidator)
            }
        }

        return if (propertySchemas != null || compiledPatterns != null || additionalPropertiesValidator != null) {
            PropertiesValidator(propertySchemas, compiledPatterns, additionalPropertiesValidator)
        } else {
            null
        }
    }
}

/** `if`, `then` and `else`.  Without an `if` that compiles, neither `then` nor `else` applies. */
internal object IfKeyword : SchemaKeyword(setOf("if", "then", "else")) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> = listOfNotNull(
        schema.propertyLookup["if"]?.let { DeclaredSubschema(listOf("if"), it, Applies.Condition) },
        schema.propertyLookup["then"]?.let { DeclaredSubschema(listOf("then"), it, Applies.Then) },
        schema.propertyLookup["else"]?.let { DeclaredSubschema(listOf("else"), it, Applies.Else) }
    )

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val ifSchema = subschemas.find { it.keyword == "if" }?.compiled ?: return null
        val thenSchema = subschemas.find { it.keyword == "then" }?.compiled
        val elseSchema = subschemas.find { it.keyword == "else" }?.compiled
        return IfValidator(ifSchema, thenSchema, elseSchema)
    }
}

/**
 * `dependencies`, which maps a property name to either a list of other property names or a subschema.  An object
 * instance that has the property must also have the other properties, or must pass the subschema.
 */
internal object DependenciesKeyword : SchemaKeyword(setOf("dependencies")) {
    override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> =
        (schema.propertyLookup["dependencies"] as? KsonObject)?.propertyLookup.orEmpty()
            .filterValues { it !is KsonList }
            .map { (property, value) -> DeclaredSubschema(listOf("dependencies", property), value, Applies.IfPresent(property)) }

    override fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator? {
        val dependencies = context.properties.getValue("dependencies")
        if (dependencies !is KsonObject) {
            context.messageSink.error(dependencies.location, SCHEMA_OBJECT_REQUIRED.create("dependencies"))
            return null
        }
        val subschemasByProperty = subschemas.associateBy { (it.applies as Applies.IfPresent).property }
        val dependencyMap = buildMap {
            dependencies.propertyLookup.forEach { (property, value) ->
                val dependency = if (value is KsonList) {
                    requiredProperties(value, context)
                } else {
                    subschemasByProperty.getValue(property).compiled?.let(::DependencyValidatorSchema)
                }
                dependency?.let { put(property, it) }
            }
        }
        return DependenciesValidator(dependencyMap)
    }

    private fun requiredProperties(list: KsonList, context: KeywordContext): DependencyValidatorArray {
        val dependencyArrayEntries = mutableSetOf<KsonString>()
        for (element in list.elements) {
            if (element is KsonString) {
                dependencyArrayEntries.add(element)
            } else {
                context.messageSink.error(element.location, SCHEMA_DEPENDENCIES_ARRAY_STRING_REQUIRED.create())
            }
        }
        return DependencyValidatorArray(dependencyArrayEntries)
    }
}
