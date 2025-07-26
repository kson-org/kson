package org.kson.bindings

import org.kson.metadata.FullyQualifiedClassName
import org.kson.metadata.FunctionKind
import org.kson.metadata.IncludeParentClass
import org.kson.metadata.SimpleClassKind
import org.kson.metadata.SimpleClassMetadata
import org.kson.metadata.SimpleConstructorMetadata
import org.kson.metadata.SimpleEnumMetadata
import org.kson.metadata.SimpleFunctionMetadata
import org.kson.metadata.SimplePackageMetadata
import org.kson.metadata.SimpleParamMetadata
import org.kson.metadata.SimpleType

class PythonGen() : LanguageSpecificBindingsGenerator {
    val builder = StringBuilder()
    var metadata: SimplePackageMetadata = SimplePackageMetadata.empty()
    val classHierarchy = hashMapOf<String, ArrayList<SimpleType>>()

    override fun generate(packageMetadata: SimplePackageMetadata): String {
        builder.clear()
        classHierarchy.clear()
        metadata = packageMetadata

        packageMetadata.classes.forEach { clazz ->
            clazz.value.supertypes.forEach { supertype ->
                if (metadata.classes.containsKey(supertype.fullyQualifiedName())) {
                    val subtypes = classHierarchy.getOrPut(supertype.fullyQualifiedName(), { arrayListOf() })
                    val classType = SimpleType(clazz.value.name.fullyQualifiedName())
                    subtypes.add(classType)
                }
            }
        }

        builder.append(
            """
            |from enum import Enum
            |from cffi import FFI
            |ffi = FFI()
            |
            |with open('libkson/kson.h', 'r') as f:
            |   header = f.read()
            |ffi.cdef(header)
            |lib = ffi.dlopen('${Platform.sharedLibraryName}')
            |symbols = lib.${Platform.symbolPrefix}kson_symbols()
            |
            |def cast_and_call(func, args):
            |    paramTypes = ffi.typeof(func).args
            |
            |    casted_args = []
            |    for (arg, paramType) in zip(args, paramTypes):
            |        if isinstance(arg, ffi.CData):
            |            casted_args.append(cast(paramType.cname, arg))
            |        else:
            |            casted_args.append(arg)
            |
            |    return func(*casted_args)
            |
            |def cast(targetTypeName, arg):
            |    addr = ffi.addressof(arg)
            |    return ffi.cast(f"{targetTypeName} *", addr)[0]
            |
            |def init_wrapper(target_type, ptr):
            |    ptr.pinned = ffi.gc(ptr.pinned, symbols.DisposeStablePointer)
            |    result = object.__new__(target_type)
            |    result.ptr = ptr
            |    return result
            |
            |def init_enum_wrapper(target_type, ptr):
            |    enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
            |    ordinal = symbols.kotlin.root.org.kson.EnumHelper.ordinal(enum_helper_instance, cast("${Platform.symbolPrefix}kson_kref_kotlin_Enum", ptr))
            |    instance = target_type(ordinal)
            |    symbols.DisposeStablePointer(ptr.pinned)
            |    return instance
            |
            |def from_kotlin_string(ptr):
            |    python_string = ffi.string(ptr).decode('utf-8')
            |    symbols.DisposeString(ptr)
            |    return python_string
            |
            |def from_kotlin_list(list, item_type, wrap_as):
            |    python_list = []
            |    iterator = symbols.kotlin.root.org.kson.SimpleListIterator.SimpleListIterator(list)
            |    while True:
            |        item = symbols.kotlin.root.org.kson.SimpleListIterator.next(iterator)
            |        if item.pinned == ffi.NULL:
            |            break
            |
            |        if wrap_as is not None:
            |            tmp = object.__new__(wrap_as)
            |            tmp.ptr = item
            |            item = tmp
            |
            |        python_list.append(item)
            |
            |    symbols.DisposeStablePointer(iterator.pinned)
            |    return python_list
            |
        """.trimMargin()
        )

        metadata.classes.forEach {
            // Skip nested classes, since they will be recursively declared by their parents
            if (!it.value.name.isNestedClass()) {
                generateClass(it.value, 0)
            }
        }

        metadata.enums.forEach {
            generateEnum(it.value)
        }

        return builder.toString()
    }

    fun generateEnum(metadata: SimpleEnumMetadata) {
        val unqualifiedClassName = metadata.name.unqualifiedName()
        builder.append("\nclass ${unqualifiedClassName}(Enum):\n")
        builder.append(PythonDocStringFormatter.format("    ", metadata.docString))

        // Mapping from Python enum to Kotlin enum
        builder.append("""
            |    def to_kotlin_enum(self):
            |        enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
            |        match self:
            |
            """.trimMargin()
        )
        for (entry in metadata.entries) {
            val entryUnqualifiedName = entry.unqualifiedName()
            val fnCall = "symbols.kotlin.root.${metadata.name.javaClassName()}.${entryUnqualifiedName}.get()"
            builder.append("""
                |            case ${entry.unqualifiedName(IncludeParentClass.Yes())}:
                |                result = $fnCall
                |                result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)
                |                return result
                |
            """.trimMargin())
        }

        // Name function corresponding to the `Enum.name` property
        builder.append("""
            |    def name(self):
            |        enum_helper_instance = symbols.kotlin.root.org.kson.EnumHelper._instance()
            |        kotlin_enum = self.to_kotlin_enum()
            |        return from_kotlin_string(symbols.kotlin.root.org.kson.EnumHelper.name(enum_helper_instance, cast("${Platform.symbolPrefix}kson_kref_kotlin_Enum", kotlin_enum)))
            |
            """.trimMargin()
        )

        metadata.entries.forEachIndexed { index, entry ->
            builder.append("    ${entry.unqualifiedName()} = $index\n")
        }
    }

    fun generateClass(metadata: SimpleClassMetadata, nesting: Int) {
        val indent = " ".repeat(nesting * 4)

        // Wrapper class
        val unqualifiedClassName = metadata.name.unqualifiedName()
        builder.append("\n${indent}class ${unqualifiedClassName}:\n")
        builder.append(PythonDocStringFormatter.format("$indent    ", metadata.docString))

        val builderLength = builder.length

        // Constructor
        metadata.constructors.forEach {
            generateWrapperConstructor(indent, metadata.name, it)
        }

        if (metadata.kind == SimpleClassKind.OBJECT) {
            generateInstanceFunction(indent, metadata.name)
        }

        // Wrapper functions
        metadata.functions.forEach {
            generateWrapperFunction(indent, metadata.name, it)
        }

        metadata.supertypes.forEach { parent ->
            val parentClass = this.metadata.classes[parent.fullyQualifiedName()]
            parentClass?.functions?.forEach {
                if (!metadata.functions.map { f -> f.name }.contains(it.name)) {
                    generateWrapperFunction(indent, parent, it)
                }
            }
        }

        // Translate
        val subclasses = classHierarchy.get(metadata.name.fullyQualifiedName())
        if (subclasses != null && subclasses.isNotEmpty()) {
            builder.append("$indent    def _translate(self):\n")
            subclasses.forEach { subclass ->
                val subclassName = FullyQualifiedClassName(subclass.classifier)
                val subclassGetType = "symbols.kotlin.root.${subclassName.javaClassName()}._type"
                builder.append("""
                    |$indent        subclassType = $subclassGetType()
                    |$indent        if symbols.IsInstance(self.ptr.pinned, subclassType):
                    |$indent            return init_wrapper(${subclassName.unqualifiedName(IncludeParentClass.Yes())}, self.ptr)
                    |
                """.trimMargin())
            }
        }

        // Nested classes
        this.metadata.nestedClasses.getOrDefault(metadata.name.javaClassName(), arrayListOf()).forEach {
            val fullName = "${metadata.name.fullyQualifiedName()}.${it}"
            generateClass(this.metadata.classes[fullName]!!, nesting + 1)
        }

        if (builderLength == builder.length) {
            builder.append("$indent    pass\n")
        }
    }

    fun generateWrapperConstructor(classIndent: String, className: FullyQualifiedClassName, metadata: SimpleConstructorMetadata) {
        // Wrapper function signature
        val declarationIndent = "$classIndent    "
        builder.append("${declarationIndent}def __init__(self, ")
        metadata.params.forEach {
            builder.append("${it.name}, ")
        }
        builder.append("):\n")
        builder.append(PythonDocStringFormatter.format("$declarationIndent    ", metadata.docString))

        // FFI call
        val fnName = "symbols.kotlin.root.${className.javaClassName()}.${className.unqualifiedName()}"
        generateFunctionCall("$declarationIndent    ", fnName, "", metadata.params, SimpleType.fromClassName(className))

        builder.append("$declarationIndent    self.ptr = result\n")
    }

    private fun generateInstanceFunction(classIndent: String, className: FullyQualifiedClassName) {
        val declarationIndent = "$classIndent    "
        builder.append("$declarationIndent@staticmethod\n")
        builder.append("${declarationIndent}def get():\n")

        val fnName = "symbols.kotlin.root.${className.javaClassName()}._instance"
        generateFunctionCall("$declarationIndent    ", fnName, "", emptyList(), SimpleType(className.fullyQualifiedName(), emptyList(), false))

        val className = FullyQualifiedClassName(className.fullyQualifiedName())
        builder.append("$declarationIndent    resultObj = object.__new__(${className.unqualifiedName(
            IncludeParentClass.Yes())})\n")
        builder.append("$declarationIndent    resultObj.ptr = result\n")
        builder.append("$declarationIndent    return resultObj\n")
    }

    fun generateWrapperFunction(classIndent: String, className: FullyQualifiedClassName, metadata: SimpleFunctionMetadata) {
        // Wrapper function signature
        val declarationIndent = "$classIndent    "
        if (metadata.isStatic) {
            builder.append("$declarationIndent@staticmethod\n")
        }
        builder.append("${declarationIndent}def ${metadata.name}(")
        if (!metadata.isStatic) {
            builder.append("self, ")
        }
        metadata.params.forEach {
            builder.append("${it.name}, ")
        }
        builder.append("):\n")
        builder.append(PythonDocStringFormatter.format(declarationIndent + "    ", metadata.docString))

        val maybeCompanion = if (metadata.kind == FunctionKind.StaticCompanion) {
            ".Companion"
        } else {
            ""
        }

        // FFI call
        val self = if (metadata.isStatic) {
            "symbols.kotlin.root.${className.javaClassName()}${maybeCompanion}._instance(), "
        } else {
            "self.ptr, "
        }
        val fnName = "symbols.kotlin.root.${className.javaClassName()}${maybeCompanion}.${metadata.name}"
        generateFunctionCall("$declarationIndent    ", fnName, self, metadata.params, metadata.returnType)

        if (metadata.returnType != null) {
            translateReturnExpr("$declarationIndent    ", metadata.returnType)
            builder.append("\n$declarationIndent    return result")
        }

        builder.append("\n")
    }

    fun generateFunctionCall(indent: String, fnName: String, self: String, params: List<SimpleParamMetadata>, returnType: SimpleType?) {
        builder.append(indent)
        if (returnType == null) {
            builder.append("cast_and_call($fnName, [$self")
        } else {
            builder.append("result = cast_and_call($fnName, [$self")
        }

        params.forEach { param ->
            val modifyArg = if (param.type.classifier == "kotlin.String") {
                ".encode('utf-8')"
            } else if (this.metadata.externalTypes.contains(param.type)) {
                ""
            } else if (this.metadata.enums.contains(param.type.classifier)) {
                ".to_kotlin_enum()"
            } else {
                ".ptr"
            }

            builder.append("${param.name}$modifyArg, ")
        }
        builder.append("])\n")
    }

    fun translateReturnExpr(indent: String, returnType: SimpleType) {
        val returnTypeClassifier = returnType.classifier
        val translateFn = when (returnTypeClassifier) {
            "kotlin.String" -> "from_kotlin_string(result)"
            "kotlin.collections.List" -> {
                val itemType = returnType.params[0]
                val itemCType = "kson_kref_${itemType.classifier.replace('.', '_')}"

                val wrapAs = if (this.metadata.classes.containsKey(itemType.classifier)) {
                    val className = FullyQualifiedClassName(itemType.classifier)
                    className.unqualifiedName(IncludeParentClass.Yes())
                } else {
                    "None"
                }

                "from_kotlin_list(result, \"$itemCType\", $wrapAs)"
            }
            else -> null
        }

        if (translateFn != null) {
            builder.append("${indent}result = $translateFn")
        } else if (this.metadata.enums.containsKey(returnTypeClassifier)) {
            val className = FullyQualifiedClassName(returnTypeClassifier)
            builder.append("${indent}result = init_enum_wrapper(${className.unqualifiedName(
                IncludeParentClass.Yes())}, result)\n")
        } else if (this.metadata.classes.containsKey(returnTypeClassifier)) {
            val className = FullyQualifiedClassName(returnTypeClassifier)
            builder.append("${indent}result = init_wrapper(${className.unqualifiedName(
                IncludeParentClass.Yes())}, result)\n")

            val subclasses = classHierarchy.get(returnType.classifier)
            if (subclasses != null && subclasses.isNotEmpty()) {
                builder.append("${indent}result = result._translate()\n")
            }
        }
    }
}
