package org.kson.walker

import org.kson.parser.Location
import org.kson.value.*

/**
 * [KsonTreeWalker] implementation for [KsonValue] trees.
 */
object KsonValueWalker : KsonTreeWalker<KsonValue> {

    override fun isObject(node: KsonValue): Boolean = node is KsonObject

    override fun isArray(node: KsonValue): Boolean = node is KsonList

    override fun getObjectProperties(node: KsonValue): List<TreeProperty<KsonValue>> {
        return (node as? KsonObject)?.propertyMap?.map { (key, prop) -> TreeProperty(key, prop.propValue) }
            ?: emptyList()
    }

    /** O(1) map lookup via [KsonObject.propertyLookup]. */
    override fun getObjectProperty(node: KsonValue, key: String): KsonValue? =
        (node as? KsonObject)?.propertyLookup?.get(key)

    override fun getArrayElements(node: KsonValue): List<KsonValue> {
        return (node as? KsonList)?.elements ?: emptyList()
    }

    override fun getStringValue(node: KsonValue): String? {
        return (node as? KsonString)?.value
    }

    override fun getLocation(node: KsonValue): Location = node.location
}
