package org.kson.walker

import org.kson.parser.Location
import org.kson.value.*

/**
 * [KsonTreeWalker] implementation for [KsonValue] trees.
 */
object KsonValueWalker : KsonTreeWalker<KsonValue> {

    override fun getChildren(node: KsonValue): NodeChildren<KsonValue> = when (node) {
        is KsonObject -> NodeChildren.Object(node.propertyMap.map { (key, prop) -> TreeProperty(key, prop.propValue) })
        is KsonList -> NodeChildren.Array(node.elements)
        else -> NodeChildren.Leaf
    }

    override fun getLocation(node: KsonValue): Location = node.location
}
