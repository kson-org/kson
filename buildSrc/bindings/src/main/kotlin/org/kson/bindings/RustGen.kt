package org.kson.bindings

import org.kson.metadata.FullyQualifiedClassName
import org.kson.metadata.FunctionKind
import org.kson.metadata.IncludeParentClass
import org.kson.metadata.SimpleClassKind
import org.kson.metadata.SimpleClassMetadata
import org.kson.metadata.SimpleConstructorMetadata
import org.kson.metadata.SimpleFunctionMetadata
import org.kson.metadata.SimplePackageMetadata
import org.kson.metadata.SimpleParamMetadata
import org.kson.metadata.SimpleType

class Downcast(val from: FullyQualifiedClassName, val to: FullyQualifiedClassName)

class RustGen : LanguageSpecificBindingsGenerator {
    val builder = StringBuilder()
    val downcasts = arrayListOf<Downcast>()
    var metadata: SimplePackageMetadata = SimplePackageMetadata.empty()

    override fun generate(packageMetadata: SimplePackageMetadata): String {
        builder.clear()
        downcasts.clear()
        metadata = packageMetadata

        builder.append(
            """
            |#![allow(non_snake_case)]
            |
            |use crate::{kson_ffi, util};
            |
            |static KSON_LIB: std::sync::LazyLock<libloading::Library> = std::sync::LazyLock::new(|| unsafe { libloading::Library::new("${Platform.sharedLibraryName}").unwrap() });
            |
            |pub(crate) static KSON_SYMBOLS: std::sync::LazyLock<kson_ffi::kson_ExportedSymbols> = std::sync::LazyLock::new(||
            |  unsafe {
            |    let func: libloading::Symbol<unsafe extern "C" fn() -> *mut kson_ffi::kson_ExportedSymbols> = KSON_LIB.get(b"${Platform.symbolPrefix}kson_symbols").unwrap();
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

        downcasts.forEach {
            generateDowncastFunction(it.from, it.to)
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
            |impl util::FromKotlinObject for $unqualifiedClassName {
            |  fn from_kotlin_object(obj: kson_ffi::kson_KNativePtr) -> Self {
            |    let kson_ref = KsonPtr {
            |      inner: std::sync::Arc::new(GcKsonPtr {
            |        inner: obj
            |      })
            |    };
            |
            |    Self {
            |      kson_ref
            |    }
            |  }
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

        // Instance functions for singleton objects
        when (metadata.kind) {
            SimpleClassKind.OBJECT -> generateInstanceFunction(metadata.name, "_instance")
            SimpleClassKind.ENUM_ENTRY -> {
                generateInstanceFunction(metadata.name, "get")

                val parent = this.metadata.nestedClasses.entries.find { it.value.contains(metadata.name.fullyQualifiedName().removePrefix(it.key).removePrefix(".")) }?.key!!
                generateUpcastFunction(FullyQualifiedClassName(parent))
            }
            else -> {}
        }

        // Function wrappers
        metadata.functions.forEach {
            generateWrapperFunction(metadata.name, it)
        }

        // Upcasting
        metadata.supertypes.forEach {
            if (this.metadata.classes.contains(it.fullyQualifiedName())) {
                generateUpcastFunction(it)
                downcasts.add(Downcast(it, metadata.name))
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
        builder.append("$docString  pub fn new$fnNameSuffix(")
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
        builder.append("$docString  pub fn ${metadata.name}(")
        if (!metadata.isStatic) {
            builder.append("&self, ")
        }
        metadata.params.forEach {
            builder.append("${it.name}: ${translateType(it.type, false)}, ")
        }
        builder.append(")")

        if (metadata.returnType != null && metadata.returnType.classifier != "kotlin.Unit") {
            builder.append(" -> ${translateType(metadata.returnType, true)}")
        }

        builder.append(" {\n")

        val maybeCompanion = if (metadata.kind == FunctionKind.StaticCompanion) {
            ".Companion"
        } else {
            ""
        }

        // FFI call
        val self = if (metadata.isStatic) {
            "KSON_SYMBOLS.kotlin.root.${className.javaClassName()}${maybeCompanion}._instance.unwrap()(), "
        } else {
            "${bindgenStructName(className)} { pinned: self.kson_ref.inner.inner }, "
        }
        val fnName = "kotlin.root.${className.javaClassName()}${maybeCompanion}.${metadata.name}"
        generateFunctionCall(fnName, self, metadata.params, metadata.returnType)
        builder.append("  }\n")
    }

    private fun generateInstanceFunction(className: FullyQualifiedClassName, functionName: String) {
        builder.append("  pub fn get() -> Self {\n")
        val fnName = "kotlin.root.${className.javaClassName()}.$functionName"
        generateFunctionCall(fnName, "", emptyList(), SimpleType(className.fullyQualifiedName(), emptyList(), false))
        builder.append("  }\n")
    }

    fun generateUpcastFunction(parent: FullyQualifiedClassName) {
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

    fun generateDowncastFunction(parent: FullyQualifiedClassName, child: FullyQualifiedClassName) {
        val structName = parent.unqualifiedName(IncludeParentClass.Yes(""))
        val childType = translateType(SimpleType.fromClassName(child), true)
        val getChildType = "kotlin.root.${child.javaClassName()}._type"
        builder.append(
            """
            |  impl $structName {
            |    pub fn downcastTo${child.unqualifiedName()}(self) -> std::result::Result<$childType, Self> {
            |      let child_type = unsafe { KSON_SYMBOLS.$getChildType.unwrap()() };
            |      if unsafe { KSON_SYMBOLS.IsInstance.unwrap()(self.kson_ref.inner.inner, child_type) } {
            |        Ok($childType {
            |          kson_ref: self.kson_ref
            |        })
            |      } else {
            |        Err(self)
            |      }
            |    }
            |  }
            |
        """.trimMargin()
        )
    }

    fun generateFunctionCall(fnName: String, self: String, params: List<SimpleParamMetadata>, returnType: SimpleType?) {
        builder.append("    let f = KSON_SYMBOLS.$fnName.unwrap();\n")
        params.forEachIndexed { i, param ->
            translateFuncArgument(i, param)
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
        return if (this.metadata.externalTypes.contains(simpleType)) {
            when (classifier) {
                "kotlin.Int" -> "i32"
                "kotlin.Boolean" -> "bool"
                "kotlin.String" -> {
                    if (owned) {
                        "String"
                    } else {
                        "&str"
                    }
                }
                "kotlin.collections.List" -> {
                    val innerType = translateType(simpleType.params[0], owned)
                    if (owned) {
                        "Vec<$innerType>"
                    } else {
                        "&[$innerType]"
                    }
                }
                else -> "UnknownExternalType<$classifier>"
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

    fun translateFuncArgument(paramIndex: Int, param: SimpleParamMetadata) {
        val translateFn = when (param.type.classifier) {
            "kotlin.String" -> "to_kotlin_string"
            "kotlin.collections.List" -> "to_kotlin_list"
            else -> null
        }

        if (translateFn != null) {
            builder.append("    let p$paramIndex = util::$translateFn(${param.name});\n")

            // Strings need to be passed as pointers
            if (param.type.classifier == "kotlin.String") {
                builder.append("    let p$paramIndex = p$paramIndex.as_ptr();\n")
            }
        } else if (param.type.isStackAllocated) {
            // Stack-allocated types need no translation
            builder.append("    let p$paramIndex = ${param.name};\n")
        } else {
            // Heap-allocated objects need to be passed as the kson ref
            builder.append("    let p$paramIndex = ${bindgenStructName(FullyQualifiedClassName(param.type.classifier))} { pinned: ${param.name}.kson_ref.inner.inner };\n")
        }
    }

    fun translateReturnExpr(returnType: SimpleType) {
        val returnTypeClassifier = returnType.classifier
        val translateFn = when (returnTypeClassifier) {
            "kotlin.String" -> "from_kotlin_string"
            "kotlin.collections.List" -> "from_kotlin_list"
            else -> null
        }

        if (translateFn != null) {
            builder.append(
                """
                |    util::$translateFn(result)
                |
            """.trimMargin()
            )
        } else if (!this.metadata.externalTypes.contains(returnType)) {
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