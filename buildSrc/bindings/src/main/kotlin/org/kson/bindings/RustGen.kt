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

val ENUM_HELPER_NAME: FullyQualifiedClassName = FullyQualifiedClassName("org.kson.EnumHelper")
val ENUM_TYPE: FullyQualifiedClassName = FullyQualifiedClassName("kotlin.Enum")

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
            |use crate::util::{FromKotlinObject, ToKotlinObject};
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

        metadata.enums.forEach {
            generateEnum(it.value)
        }

        downcasts.forEach {
            generateDowncastFunction(it.from, it.to)
        }

        return builder.toString()
    }

    private fun generateEnum(metadata: SimpleEnumMetadata) {
        val unqualifiedName = metadata.name.unqualifiedName()
        val docString = RustDocStringFormatter.format("", metadata.docString)
        builder.append("""
            |#[derive(Copy, Clone)]
            |${docString}pub enum $unqualifiedName {
            |
        """.trimMargin())

        for (entry in metadata.entries) {
            builder.append("  ${entry.unqualifiedName()},\n")
        }

        builder.append("}\n")

        builder.append("""
            |impl $unqualifiedName {
            |  pub fn name(self) -> String {
            |    util::enum_name(self)
            |  }
            |}
            |
            """.trimMargin())

        builder.append("""
            |impl ToKotlinObject for $unqualifiedName {
            |  fn to_kotlin_object(&self) -> kson_ffi::kson_KNativePtr {
            |    match self {
            |
            """.trimMargin()
        )
        for (entry in metadata.entries) {
            val entryUnqualifiedName = entry.unqualifiedName()
            val fnName = "kotlin.root.${metadata.name.javaClassName()}.${entryUnqualifiedName}.get"
            builder.append("""
                |      $unqualifiedName::${entryUnqualifiedName} => unsafe { KSON_SYMBOLS.$fnName.unwrap()() }.pinned,
                |
            """.trimMargin())
        }
        builder.append("""
            |    }
            |  }
            |}
            |
            """.trimMargin()
        )

        // Conversion from kotlin
        builder.append("""|
            |impl util::FromKotlinObject for $unqualifiedName {
            |  fn from_kotlin_object(ptr: kson_ffi::kson_KNativePtr) -> Self {
            |    let helper_instance = unsafe { KSON_SYMBOLS.kotlin.root.${ENUM_HELPER_NAME.fullyQualifiedName()}._instance.unwrap()() };
            |    let ordinal = unsafe { KSON_SYMBOLS.kotlin.root.${ENUM_HELPER_NAME.fullyQualifiedName()}.ordinal.unwrap()(helper_instance, ${bindgenStructName(ENUM_TYPE)} { pinned: ptr }) };
            |    let variant = match ordinal {
            |
            """.trimMargin())
        metadata.entries.forEachIndexed { index, entry ->
            builder.append("      $index => ${metadata.name.unqualifiedName()}::${entry.unqualifiedName()},\n")
        }
        builder.append("""
            |      _ => unreachable!(),
            |    };
            |
            |    unsafe { KSON_SYMBOLS.DisposeStablePointer.unwrap()(ptr) };
            |    variant
            |  }
            |}
            |
        """.trimMargin())
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
            |impl FromKotlinObject for $unqualifiedClassName {
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
            |impl ToKotlinObject for $unqualifiedClassName {
            |  fn to_kotlin_object(&self) -> kson_ffi::kson_KNativePtr {
            |    self.kson_ref.inner.inner
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
        if (metadata.kind == SimpleClassKind.OBJECT) {
            generateInstanceFunction(metadata.name)
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

    private fun generateInstanceFunction(className: FullyQualifiedClassName) {
        builder.append("  pub fn get() -> Self {\n")
        generateFunctionCall("kotlin.root.${className.javaClassName()}._instance", "", emptyList(), SimpleType(className.fullyQualifiedName()))
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
            |      if unsafe { KSON_SYMBOLS.IsInstance.unwrap()(self.to_kotlin_object(), child_type) } {
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
            // Heap-allocated objects are passed as their kson ref
            builder.append("    let p$paramIndex = ${bindgenStructName(FullyQualifiedClassName(param.type.classifier))} { pinned: ${param.name}.to_kotlin_object() };\n")
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
        } else if (this.metadata.enums.containsKey(returnType.classifier)) {
            val name = FullyQualifiedClassName(returnType.classifier)
            builder.append("""
                |    ${name.unqualifiedName()}::from_kotlin_object(result.pinned)
                |
            """.trimMargin())
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