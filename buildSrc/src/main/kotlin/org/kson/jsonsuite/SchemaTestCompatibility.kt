package org.kson.jsonsuite

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The dialects a [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite) test case
 * applies to, as given by its `compatibility` property.  A test case without one applies to every dialect: [ALL].
 *
 * Dialects are identified by their release: draft numbers up to 7, then years: 2019, 2020, ...
 */
@Serializable(with = SchemaTestCompatibilitySerializer::class)
class SchemaTestCompatibility private constructor(val text: String, private val constraints: List<Constraint>) {

    fun admits(draft: SchemaDraft): Boolean = admits(draft.release)

    /**
     * Follows the matching algorithm in the suite's `validation/README.md`: constraints are read in ascending
     * order, and the first one whose version is at or above [release] decides.  If none is, the last one decides.
     */
    internal fun admits(release: Int): Boolean {
        var admitted = true
        for (constraint in constraints) {
            admitted = when (constraint.operator) {
                Operator.MINIMUM -> release >= constraint.version
                Operator.MAXIMUM -> release <= constraint.version
                Operator.EXACT -> release == constraint.version
            }
            if (release <= constraint.version) {
                break
            }
        }
        return admitted
    }

    private enum class Operator { MINIMUM, MAXIMUM, EXACT }

    private class Constraint(val operator: Operator, val version: Int)

    companion object {
        val ALL = SchemaTestCompatibility("", emptyList())

        private val constraintPattern = Regex("(<=|=)?([0-9]+)")

        fun parse(text: String): SchemaTestCompatibility = SchemaTestCompatibility(text, text.split(",").map { constraint ->
            val (operator, version) = constraintPattern.matchEntire(constraint)?.destructured
                ?: throw IllegalArgumentException("Malformed compatibility constraint \"$constraint\" in \"$text\"")
            Constraint(
                when (operator) {
                    "<=" -> Operator.MAXIMUM
                    "=" -> Operator.EXACT
                    else -> Operator.MINIMUM
                },
                version.toInt()
            )
        })
    }
}

private object SchemaTestCompatibilitySerializer : KSerializer<SchemaTestCompatibility> {
    override val descriptor = PrimitiveSerialDescriptor("org.kson.jsonsuite.SchemaTestCompatibility", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder) = SchemaTestCompatibility.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: SchemaTestCompatibility) = encoder.encodeString(value.text)
}
