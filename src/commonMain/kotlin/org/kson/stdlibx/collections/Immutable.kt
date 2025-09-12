package org.kson.stdlibx.collections

/**
 * Some useful immutable collection extensions from https://stackoverflow.com/a/37936456
 */

class ImmutableList<T> private constructor(private val inner: List<T>) : List<T> by inner {
    companion object {
        fun <T> create(inner: List<T>) = if (inner is ImmutableList<T>) {
            inner
        } else {
            ImmutableList(inner.toList())
        }
    }
}

class ImmutableMap<K, V> private constructor(private val inner: Map<K, V>) : Map<K, V> by inner {
    companion object {
        fun <K, V> create(inner: Map<K, V>) = if (inner is ImmutableMap<K, V>) {
            inner
        } else {
            ImmutableMap(hashMapOf(*inner.toList().toTypedArray()))
        }
    }
}

fun <K, V> Map<K, V>.toImmutableMap(): ImmutableMap<K, V> = ImmutableMap.create(this)
fun <T> List<T>.toImmutableList(): ImmutableList<T> = ImmutableList.create(this)
fun <T> Set<T>.toImmutableList(): ImmutableList<T> = ImmutableList.create(this.toList())
