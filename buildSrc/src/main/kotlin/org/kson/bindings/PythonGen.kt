package org.kson.bindings

class PythonGen(val metadata: SimplePackageMetadata) : LanguageSpecificBindingsGenerator {
    val builder = StringBuilder()

    override fun generate(): String {
        builder.clear()
        builder.append(
            """
            |from cffi import FFI
            |ffi = FFI()
            |
            |with open('libkson/kson.h', 'r') as f:
            |   header = f.read()
            |ffi.cdef(header)
            |lib = ffi.dlopen('kson.dll')
            |symbols = lib.kson_symbols()
            |
            |def cast_and_call(func, args):
            |    paramTypes = ffi.typeof(func).args
            |
            |    casted_args = []
            |    for (arg, paramType) in zip(args, paramTypes):
            |        if isinstance(arg, ffi.CData):
            |            casted_args.append(cast(paramType, arg))
            |        else:
            |            casted_args.append(arg)
            |
            |    return func(*casted_args)
            |
            |def cast(targetType, arg):
            |    addr = ffi.addressof(arg)
            |    return ffi.cast(f"{targetType.cname} *", addr)[0]
            |
        """.trimMargin()
        )

        metadata.classes.forEach {
            // Skip nested classes, since they will be recursively declared by their parents
            if (!it.value.name.isNestedClass()) {
                generateClass(it.value, 0)
            }
        }

        return builder.toString()
    }

    fun generateClass(metadata: SimpleClassMetadata, nesting: Int) {
        val indent = " ".repeat(nesting * 4)

        // Wrapper class
        val unqualifiedClassName = metadata.name.unqualifiedName()
        builder.append("\n${indent}class ${unqualifiedClassName}:\n")
        builder.append(PythonDocStringFormatter.format(indent + "    ", metadata.docString))

        val builderLength = builder.length

        // Constructor
        metadata.constructors.forEach {
            generateWrapperConstructor(indent, metadata.name, it)
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
        builder.append(PythonDocStringFormatter.format(declarationIndent + "    ", metadata.docString))

        // FFI call
        val fnName = "symbols.kotlin.root.${className.javaClassName()}.${className.unqualifiedName()}"
        generateFunctionCall("$declarationIndent    ", fnName, "", metadata.params, SimpleType.fromClassName(className))

        builder.append("$declarationIndent    self.ptr = result\n")
    }

    fun generateWrapperFunction(classIndent: String, className: FullyQualifiedClassName, metadata: SimpleFunctionMetadata) {
        // Wrapper function signature
        val declarationIndent = "$classIndent    "
        if (metadata.static) {
            builder.append("$declarationIndent@staticmethod\n")
        }
        builder.append("${declarationIndent}def ${metadata.name}(")
        if (!metadata.static) {
            builder.append("self, ")
        }
        metadata.params.forEach {
            builder.append("${it.name}, ")
        }
        builder.append("):\n")
        builder.append(PythonDocStringFormatter.format(declarationIndent + "    ", metadata.docString))

        // FFI call
        val self = if (metadata.static) {
            "symbols.kotlin.root.${className.javaClassName()}.Companion._instance(), "
        } else {
            "self.ptr, "
        }
        val maybeCompanion = if (metadata.static) {
            ".Companion"
        } else {
            ""
        }
        val fnName = "symbols.kotlin.root.${className.javaClassName()}${maybeCompanion}.${metadata.name}"
        generateFunctionCall("$declarationIndent    ", fnName, self, metadata.params, metadata.returnType)


        if (metadata.returnType != null) {
            if (this.metadata.classes.containsKey(metadata.returnType.classifier)) {
                val className = FullyQualifiedClassName(metadata.returnType.classifier)
                builder.append("$declarationIndent    resultObj = object.__new__(${className.unqualifiedName(IncludeParentClass.Yes())})\n")
                builder.append("$declarationIndent    resultObj.ptr = result\n")
                builder.append("$declarationIndent    result = resultObj\n")
            }

            builder.append("$declarationIndent    return ")
            if (metadata.returnType.classifier == "kotlin.String") {
                builder.append("ffi.string(result).decode('utf-8')")
            } else {
                builder.append("result")
            }
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

        params.forEach {
            val modifyArg = if (it.type.classifier == "kotlin.String") {
                ".encode('utf-8')"
            } else if (this.metadata.externalTypes.contains(it.type.classifier)) {
                ""
            } else {
                ".ptr"
            }

            builder.append("${it.name}$modifyArg, ")
        }
        builder.append("])\n")

        if (returnType != null) {
            if (returnType.classifier == "kotlin.String") {
                builder.append("${indent}result = ffi.gc(result, symbols.DisposeString)\n")
            } else if (!this.metadata.externalTypes.contains(returnType.classifier)) {
                builder.append("${indent}result.pinned = ffi.gc(result.pinned, symbols.DisposeStablePointer)\n")
            }
        }
    }
}
