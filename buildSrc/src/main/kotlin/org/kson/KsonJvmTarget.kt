package org.kson

/**
 * The oldest JVM that can consume KSON's published JVM artifacts.
 *
 * Every project that publishes a JVM artifact must compile to this target, so that the floor
 * documented in docs/dependency_maintenance.md holds for all of them.
 */
object KsonJvmTarget {
    const val VERSION = "11"
}
