package org.kson.schema

/**
 * Which part of the instance a subschema validates, relative to the instance its parent schema validates.  For
 * example, a subschema under `properties/name` validates the parent instance's `name` property.
 */
sealed interface Applies {
    /** Whether the subschema validates the same instance as its parent schema */
    val toSameInstance: Boolean
        get() = when (this) {
            InPlace, is Alternative, Negated, Condition, Then, Else, is IfPresent -> true
            is Property, is PatternProperty, OtherProperties, PropertyNames, is Item, is ItemsFrom, SomeItem,
            NotApplied -> false
        }

    /** `allOf`, and a `$ref` that keeps the keywords beside it: validates the same instance. */
    data object InPlace : Applies

    /** `anyOf` and `oneOf`: the alternative at [index] validates the same instance. */
    data class Alternative(val index: Int) : Applies

    /** `not`: validates the same instance, which must fail the subschema. */
    data object Negated : Applies

    /** `if`: validates the same instance.  Whether the instance passes decides whether `then` or `else` applies. */
    data object Condition : Applies

    /** `then`: validates the same instance, but only when the instance passes the `if`. */
    data object Then : Applies

    /** `else`: validates the same instance, but only when the instance fails the `if`. */
    data object Else : Applies

    /** `dependencies`: validates the same instance, but only when the instance has a property named [property]. */
    data class IfPresent(val property: String) : Applies

    /** `properties`: validates the instance's property named [name]. */
    data class Property(val name: String) : Applies

    /** `patternProperties`: validates each of the instance's properties whose name [pattern] matches. */
    data class PatternProperty(val pattern: String) : Applies

    /**
     * `additionalProperties`: validates each of the instance's properties that neither `properties` nor
     * `patternProperties` matches.
     */
    data object OtherProperties : Applies

    /** `propertyNames`: validates the name of each of the instance's properties. */
    data object PropertyNames : Applies

    /** A list of `items`: the subschema at [index] validates the instance's item at the same index. */
    data class Item(val index: Int) : Applies

    /**
     * A single `items` schema, and `additionalItems` after a list of `items`: validates each of the instance's items
     * from index [start] on.
     */
    data class ItemsFrom(val start: Int) : Applies

    /** `contains`: validates each of the instance's items, at least one of which must pass. */
    data object SomeItem : Applies

    /** `definitions`, and `additionalItems` without a list of `items`: validates no part of the instance. */
    data object NotApplied : Applies
}
