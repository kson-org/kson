package org.kson.bindings

class RustGen(val metadata: SimplePackageMetadata) : LanguageSpecificBindingsGenerator {
    val builder = StringBuilder()

    override fun generate(): String {
        builder.clear()
        builder.append(
            """
            |#![allow(non_snake_case)]
            |
            |mod kson_ffi;
            |
            |#[cfg(test)]
            |mod test;
            |
            |static KSON_LIB: std::sync::LazyLock<libloading::Library> = std::sync::LazyLock::new(|| unsafe { libloading::Library::new("libkson/kson.dll").unwrap() });
            |
            |static KSON_SYMBOLS: std::sync::LazyLock<kson_ffi::kson_ExportedSymbols> = std::sync::LazyLock::new(||
            |  unsafe {
            |    let func: libloading::Symbol<unsafe extern "C" fn() -> *mut kson_ffi::kson_ExportedSymbols> = KSON_LIB.get(b"kson_symbols").unwrap();
            |    *func()
            |  }
            |);
            |
            |struct GcKsonPtr {
            |  inner: kson_ffi::kson_KNativePtr,
            |}
            |
            |impl Drop for GcKsonPtr {
            |  fn drop(&mut self) {
            |    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(self.inner) };
            |  }
            |}
            |
            |struct KsonPtr {
            |  inner: std::sync::Arc<GcKsonPtr>,
            |}
            |
        """.trimMargin()
        )

        metadata.classes.forEach {
            generateClass(it.value)
        }

        return builder.toString()
    }

    private fun generateClass(metadata: SimpleClassMetadata) {
        // Wrapper struct
        val unqualifiedClassName = metadata.name.unqualifiedName(IncludeParentClass.Yes(""))
        val docString = RustDocStringFormatter.format("", metadata.docString)
        builder.append(
            """
            |${docString}pub struct $unqualifiedClassName {
            |  kson_ref: KsonPtr,
            |}
            |
            """.trimMargin()
        )

        // Constructor wrappers
        builder.append("impl $unqualifiedClassName {\n")
        var overloadCount = 0
        metadata.constructors.sortedBy { it.params.size }.forEach {
            generateWrapperConstructor(metadata.name, it, overloadCount)
            overloadCount++
        }

        // Function wrappers
        metadata.functions.forEach {
            generateWrapperFunction(metadata.name, it)
        }

        // Upcasting
        metadata.supertypes.forEach {
            if (this.metadata.classes.contains(it.fullyQualifiedName())) {
                generateUpcastFunction(it)
            }
        }

        builder.append("}\n")
    }

    private fun generateWrapperConstructor(
        className: FullyQualifiedClassName,
        metadata: SimpleConstructorMetadata,
        overloadCount: Int
    ) {
        val fnNameSuffix = "_".repeat(overloadCount)

        // Wrapper function signature
        val docString = RustDocStringFormatter.format("  ", metadata.docString)
        builder.append("  pub fn new$fnNameSuffix(")
        metadata.params.forEach {
            builder.append("${it.name}: ${translateType(it.type, false)}, ")
        }
        builder.append(") -> Self {\n")

        // FFI call
        val fnName = "kotlin.root.${className.javaClassName()}.${className.unqualifiedName()}$fnNameSuffix"
        generateFunctionCall(fnName, "", metadata.params, SimpleType.fromClassName(className))
        builder.append("  }\n")
    }

    private fun generateWrapperFunction(className: FullyQualifiedClassName, metadata: SimpleFunctionMetadata) {
        // Wrapper function signature
        val docString = RustDocStringFormatter.format("  ", metadata.docString)
        builder.append("${docString}  pub fn ${metadata.name}(")
        if (!metadata.static) {
            builder.append("&self, ")
        }
        metadata.params.forEach {
            builder.append("${it.name}: ${translateType(it.type, false)}, ")
        }
        builder.append(")")

        if (metadata.returnType != null) {
            builder.append(" -> ${translateType(metadata.returnType!!, true)}")
        }

        builder.append(" {\n")

        // FFI call
        val self = if (metadata.static) {
            "KSON_SYMBOLS.kotlin.root.${className.javaClassName()}.Companion._instance.unwrap()(), "
        } else {
            "${bindgenStructName(className)} { pinned: self.kson_ref.inner.inner }, "
        }
        val maybeCompanion = if (metadata.static) {
            ".Companion"
        } else {
            ""
        }
        val fnName = "kotlin.root.${className.javaClassName()}${maybeCompanion}.${metadata.name}"
        generateFunctionCall(fnName, self, metadata.params, metadata.returnType)
        builder.append("  }\n")
    }

    fun generateUpcastFunction(parent: FullyQualifiedClassName) {
        // Upcast function signature
        val parentType = translateType(SimpleType.fromClassName(parent), true)
        builder.append(
            """
            |  pub fn upcast(self) -> $parentType {
            |    $parentType {
            |      kson_ref: self.kson_ref
            |    }
            |  }
            |
        """.trimMargin()
        )
    }

    fun generateFunctionCall(fnName: String, self: String, params: List<SimpleParamMetadata>, returnType: SimpleType?) {
        builder.append("    let f = KSON_SYMBOLS.$fnName.unwrap();\n")
        params.forEachIndexed { i, param ->
            if (param.type.classifier == "kotlin.String") {
                // Strings need to transformed to C strings and passed as pointers
                builder.append("    let c_str = std::ffi::CString::new(${param.name}).unwrap();\n")
                builder.append("    let p$i = c_str.as_ptr();\n")
            } else if (this.metadata.externalTypes.contains(param.type.classifier)) {
                // Native types need no translation
                builder.append("    let p$i = ${param.name};\n")
            } else {
                // Objects need to be passed as the kson ref
                builder.append("    let p$i = ${bindgenStructName(FullyQualifiedClassName(param.type.classifier))} { pinned: ${param.name}.kson_ref.inner.inner };\n")
            }
        }

        builder.append("    let result = unsafe { f($self")
        params.forEachIndexed { i, _ ->
            builder.append("p$i, ")
        }

        builder.append(") };\n")

        if (returnType != null) {
            translateReturnExpr(returnType)
        }
    }

    fun translateType(simpleType: SimpleType, owned: Boolean): String {
        val classifier = simpleType.classifier
        return if (classifier == "kotlin.String") {
            if (owned) {
                "String"
            } else {
                "&str"
            }
        } else if (this.metadata.externalTypes.contains(classifier)) {
            when (classifier) {
                "kotlin.Int" -> "i32"
                "kotlin.Boolean" -> "bool"
                else -> classifier
            }
        } else {
            val className = FullyQualifiedClassName(classifier)
            val structName = className.unqualifiedName(IncludeParentClass.Yes(""))
            if (owned) {
                structName
            } else {
                "&$structName"
            }
        }
    }

    fun translateReturnExpr(returnType: SimpleType) {
        val returnTypeClassifier = returnType.classifier
        if (returnTypeClassifier == "kotlin.String") {
            builder.append(
                """
                |    let result_string = unsafe { std::ffi::CStr::from_ptr(result) };
                |    let result_string = result_string.to_string_lossy().to_string();
                |    unsafe { KSON_SYMBOLS.DisposeString.unwrap()(result) };
                |    result_string
                |
            """.trimMargin()
            )
        } else if (!this.metadata.externalTypes.contains(returnTypeClassifier)) {
            val structName = FullyQualifiedClassName(returnTypeClassifier).unqualifiedName(IncludeParentClass.Yes(""))
            builder.append(
                """
                |    $structName {
                |      kson_ref: KsonPtr {
                |        inner: std::sync::Arc::new(GcKsonPtr {
                |          inner: result.pinned
                |        })
                |      }
                |    }
                |
            """.trimMargin()
            )
        } else {
            builder.append("    result\n")
        }
    }

    fun bindgenStructName(className: FullyQualifiedClassName): String {
        return "kson_ffi::kson_kref_${className.fullyQualifiedName().replace('/', '_').replace('.', '_')}"
    }
}