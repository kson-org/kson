package org.kson.metadata

import kotlinx.serialization.Serializable

@Serializable
class SimplePackageMetadata(val classes: HashMap<String, SimpleClassMetadata>, val nestedClasses: HashMap<String, ArrayList<String>>, val externalTypes: List<String>) {
    companion object {
        fun empty(): SimplePackageMetadata {
            return SimplePackageMetadata(HashMap(), HashMap(), ArrayList())
        }
    }
}

@Serializable
class SimpleClassMetadata(val name: FullyQualifiedClassName, val supertypes: Array<FullyQualifiedClassName>, val constructors: Array<SimpleConstructorMetadata>, val functions: Array<SimpleFunctionMetadata>, val enumConstants: Array<String>, val docString: String?)

@Serializable
class SimpleConstructorMetadata(val params: List<SimpleParamMetadata>, val docString: String?)

@Serializable
class SimpleFunctionMetadata(val name: String, val static: Boolean, val params: List<SimpleParamMetadata>, val returnType: SimpleType?, val docString: String?)

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
class SimpleType(val classifier: String) {
    companion object {
        fun fromClassName(className: FullyQualifiedClassName): SimpleType {
            return SimpleType(className.fullyQualifiedName())
        }
    }
}

@Serializable
sealed class IncludeParentClass {
    object No : IncludeParentClass()
    class Yes(val separator: String = ".") : IncludeParentClass()
}
