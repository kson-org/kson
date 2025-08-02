package org.kson.metadata

import kotlinx.serialization.Serializable

@Serializable
class SimplePackageMetadata(
    val classes: HashMap<String, SimpleClassMetadata>,
    val enums: HashMap<String, SimpleEnumMetadata>,
    val nestedClasses: HashMap<String, ArrayList<String>>,
    val externalTypes: List<SimpleType>) {

    companion object {
        fun empty(): SimplePackageMetadata {
            return SimplePackageMetadata(HashMap(), HashMap(), HashMap(), ArrayList())
        }
    }
}

@Serializable
class SimpleEnumMetadata(
    val name: FullyQualifiedClassName,
    val entries: MutableList<SimpleEnumEntry>,
    val docString: String?,
)

@Serializable
class SimpleEnumEntry(
    val name: FullyQualifiedClassName,
    val docString: String?,
)

@Serializable
class SimpleClassMetadata(
    val kind: SimpleClassKind,
    val name: FullyQualifiedClassName,
    val supertypes: Array<FullyQualifiedClassName>,
    val constructors: Array<SimpleConstructorMetadata>,
    val functions: Array<SimpleFunctionMetadata>,
    val properties: Array<SimplePropertyMetadata>,
    val docString: String?,
)

@Serializable
enum class SimpleClassKind {
    OBJECT,
    OTHER
}

@Serializable
class SimplePropertyMetadata(val name: String, val getter: SimpleFunctionMetadata)

@Serializable
class SimpleConstructorMetadata(val params: List<SimpleParamMetadata>, val docString: String?)

@Serializable
class SimpleFunctionMetadata(val name: String, val kind: FunctionKind, val params: List<SimpleParamMetadata>, val returnType: SimpleType?, val docString: String?) {
    val isStatic = kind == FunctionKind.StaticTopLevel || kind == FunctionKind.StaticCompanion
}

@Serializable
class SimpleParamMetadata(val name: String, val type: SimpleType)

@Serializable
class FullyQualifiedClassName(private val name: String) {
    fun isNestedClass(): Boolean {
        val parts = name.split('.')
        val firstClassIndex = parts.indexOfFirst { name -> name[0].isUpperCase() }

        return firstClassIndex != parts.size - 1
    }

    fun fullyQualifiedName(): String {
        return name
    }

    fun unqualifiedName(includeParentClassName: IncludeParentClass = IncludeParentClass.No): String {
        val parts = name.split('.')

        return when (includeParentClassName) {
            IncludeParentClass.No -> parts.last()
            is IncludeParentClass.Yes -> {
                val firstClassIndex = parts.indexOfFirst { name -> name[0].isUpperCase() }
                parts.subList(firstClassIndex, parts.size).joinToString(includeParentClassName.separator)
            }
        }
    }

    fun javaClassName(): String {
        return name.replace('/', '.')
    }
}

@Serializable
data class SimpleType(val classifier: String, val params: List<SimpleType> = emptyList(), val isStackAllocated: Boolean = false) {
    companion object {
        fun fromClassName(className: FullyQualifiedClassName): SimpleType {
            return SimpleType(className.fullyQualifiedName(), listOf(), isStackAllocated = false)
        }
    }
}

@Serializable
sealed class IncludeParentClass {
    @Serializable
    object No : IncludeParentClass()
    @Serializable
    class Yes(val separator: String = ".") : IncludeParentClass()
}

@Serializable
sealed class FunctionKind {
    @Serializable
    object StaticTopLevel : FunctionKind()
    @Serializable
    object StaticCompanion : FunctionKind()
    @Serializable
    object NonStatic : FunctionKind()
}
