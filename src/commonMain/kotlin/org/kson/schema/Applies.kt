package org.kson.schema

/**
 * Which part of the instance a subschema validates, relative to the instance its parent schema validates.  For
 * example, a subschema under `properties/name` validates the parent instance's `name` property.
 */
internal sealed interface Applies {
    /** The same instance, as for an `allOf` member */
    data object InPlace : Applies

    /** The same instance, as alternative [index] of an `anyOf` or `oneOf` */
    data class Alternative(val index: Int) : Applies

    /** The same instance, as for `not`.  The instance must fail the subschema. */
    data object Negated : Applies

    /** The same instance, as for `if`.  Whether the instance passes decides whether [Then] or [Else] applies. */
    data object Condition : Applies

    /** The same instance, as for `then`, when it passes the [Condition] */
    data object Then : Applies

    /** The same instance, as for `else`, when it fails the [Condition] */
    data object Else : Applies

    /** The same instance, when it is an object with [property], as for a schema under `dependencies` */
    data class IfPresent(val property: String) : Applies

    /** The instance's property [name] */
    data class Property(val name: String) : Applies

    /** Each of the instance's properties whose name [pattern] matches */
    data class PatternProperty(val pattern: String) : Applies

    /** Each of the instance's properties that no [Property] or [PatternProperty] matches */
    data object OtherProperties : Applies

    /** The name of each of the instance's properties, as for `propertyNames` */
    data object PropertyNames : Applies

    /** The instance's item at [index] */
    data class Item(val index: Int) : Applies

    /** Each of the instance's items from [start] on */
    data class ItemsFrom(val start: Int) : Applies

    /** The instance's items, as for `contains`.  At least one item must pass the subschema. */
    data object SomeItem : Applies

    /** None of it, as for a subschema under `definitions`, or for `additionalItems` without a list of `items` */
    data object NotApplied : Applies
}
