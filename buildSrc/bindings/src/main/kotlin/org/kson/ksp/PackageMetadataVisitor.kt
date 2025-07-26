package org.kson.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility
import org.kson.metadata.FullyQualifiedClassName
import org.kson.metadata.FunctionKind
import org.kson.metadata.SimpleClassKind
import org.kson.metadata.SimpleClassMetadata
import org.kson.metadata.SimpleConstructorMetadata
import org.kson.metadata.SimpleEnumMetadata
import org.kson.metadata.SimpleFunctionMetadata
import org.kson.metadata.SimplePackageMetadata
import org.kson.metadata.SimpleParamMetadata
import org.kson.metadata.SimpleType
import java.util.LinkedList
import java.util.Queue

class PackageMetadataVisitor {
    val pendingClassDecls: Queue<KSClassDeclaration> = LinkedList()
    val classes: HashMap<String, SimpleClassMetadata> = HashMap()
    val enums: HashMap<String, SimpleEnumMetadata> = HashMap()
    val nestedClasses: HashMap<String, ArrayList<String>> = HashMap()
    val seenExternalTypes: HashSet<SimpleType> = HashSet()
    val ignoredFunctions: HashSet<String> = HashSet(listOf(
        "component1",
        "component2",
        "component3",
        "component4",
        "component5",
        "component6",
        "component7",
        "component8",
        "component9",
        "copy",
        "equals",
        "hashCode",
    ))

    val stackAllocatedTypes: HashSet<String> = HashSet(listOf(
        "kotlin.Int",
        "kotlin.Boolean",
    ))

    constructor(topLevelClassDecl: List<KSClassDeclaration>) {
        pendingClassDecls.addAll(topLevelClassDecl)
    }

    fun getPackageMetadata(): SimplePackageMetadata {
        return SimplePackageMetadata(classes, enums, nestedClasses, seenExternalTypes.toList())
    }

    // Returns false when there are no more classes to visit
    fun visitNextClass(): Boolean {
        val classDecl = pendingClassDecls.poll()
        if (classDecl == null) {
            return false
        }

        // Local declarations cannot be public
        if (classDecl.isLocal()) {
            return true
        }

        val className = classDecl.qualifiedName!!.asString()
        if (this.classes.containsKey(className) || this.enums.containsKey(className)) {
            // Already handled, move on to the next one
            return true
        }

        // Track the supertypes of the class
        val supertypes = arrayListOf<FullyQualifiedClassName>()
        classDecl.getAllSuperTypes().forEach {
            if (it.declaration.qualifiedName?.asString() == "kotlin.Any") {
                return@forEach
            }

            supertypes.add(FullyQualifiedClassName(simplifyType(it).classifier))
            this.visitType(it)
        }

        val constructors = arrayListOf<SimpleConstructorMetadata>()
        val functions = arrayListOf<SimpleFunctionMetadata>()
        classDecl.declarations.forEach {
            if (it is KSClassDeclaration) {
                if (it.isCompanionObject) {
                    // Static functions
                    it.getDeclaredFunctions().forEach { fnDecl ->
                        if (fnDecl.simpleName.asString() != "<init>") {
                            visitFunction(functions, fnDecl, FunctionKind.StaticCompanion)
                        }
                    }
                } else {
                    // Nested class declarations
                    this.pendingClassDecls.add(it)
                }
            }

            // Public functions
            if (it is KSFunctionDeclaration && it.getVisibility() == Visibility.PUBLIC) {
                if (it.isConstructor()) {
                    // Objects also have constructors, but those aren't exposed in the FFI (objects are singletons, so
                    // creation is handled by kotlin itself)
                    if (classDecl.classKind != ClassKind.OBJECT) {
                        constructors.add(SimpleConstructorMetadata(it.parameters.map { parameter ->
                            SimpleParamMetadata(
                                parameter.name!!.asString(),
                                simplifyType(parameter.type.resolve())
                            )
                        }, it.docString))
                        it.parameters.forEach { param ->
                            this.visitType(param.type.resolve())
                        }
                    }
                } else {
                    val kind = if (classDecl.classKind == ClassKind.OBJECT) {
                        FunctionKind.StaticTopLevel
                    } else {
                        FunctionKind.NonStatic
                    }
                    this.visitFunction(functions, it, kind)
                }
            }

            // Properties
            if (it is KSPropertyDeclaration && it.getVisibility() == Visibility.PUBLIC && it.origin != Origin.SYNTHETIC) {
                functions.add(
                    SimpleFunctionMetadata(
                        "get_${it.simpleName.asString()}", FunctionKind.NonStatic, arrayListOf(),
                        simplifyType(it.type.resolve()), it.docString
                    )
                )
                this.visitType(it.type.resolve())
            }
        }

        val name = FullyQualifiedClassName(className)
        when (classDecl.classKind) {
            ClassKind.ENUM_CLASS -> {
                // An enum should have none of these
                assert(constructors.isEmpty())
                assert(functions.isEmpty())

                // Entries will be populated later (see `ClassKind.ENUM_ENTRY` branch of this `when`)
                val enumMetadata = SimpleEnumMetadata(
                    name,
                    entries = mutableListOf(),
                    classDecl.docString
                )
                this.enums[className] = enumMetadata
            }
            ClassKind.ENUM_ENTRY -> {
                // An enum entry should have none of these
                assert(constructors.isEmpty())
                assert(functions.isEmpty())

                val classParent = classDecl.parentDeclaration
                if (classParent !is KSClassDeclaration) {
                    throw RuntimeException("parent of enum entry should always be a class declaration")
                }

                val enumName = classParent.qualifiedName!!.asString()
                val enumMetadata = this.enums[enumName]!!

                val enumEntryName = FullyQualifiedClassName(classDecl.qualifiedName!!.asString())
                enumMetadata.entries.add(enumEntryName)
            }
            else -> {
                val classMetadata = SimpleClassMetadata(
                    if (classDecl.classKind == ClassKind.OBJECT) {
                        SimpleClassKind.OBJECT
                    } else {
                        SimpleClassKind.OTHER
                    },
                    name,
                    supertypes.toTypedArray(),
                    constructors.toTypedArray(),
                    functions.toTypedArray(),
                    classDecl.docString,
                )
                val classParent = classDecl.parentDeclaration
                if (classParent is KSClassDeclaration) {
                    val list = this.nestedClasses.getOrPut(classParent.qualifiedName!!.asString()) { arrayListOf() }
                    list.add(classDecl.simpleName.asString())
                }
                this.classes[className] = classMetadata
            }
        }

        return true
    }

    fun visitFunction(functions: ArrayList<SimpleFunctionMetadata>, function: KSFunctionDeclaration, kind: FunctionKind) {
        if (function.getVisibility() == Visibility.PUBLIC
            && function.typeParameters.isEmpty()
            && function.origin != Origin.SYNTHETIC
            && !this.ignoredFunctions.contains(function.simpleName.asString())) {
            functions.add(
                SimpleFunctionMetadata(
                    function.simpleName.asString(),
                    kind,
                    function.parameters.map {
                        SimpleParamMetadata(
                            it.name!!.asString(),
                            simplifyType(it.type.resolve())
                        )
                    },
                    function.returnType?.resolve()?.let { simplifyType(it) },
                    function.docString
                )
            )

            function.parameters.forEach {
                this.visitType(it.type.resolve())
            }

            function.returnType?.resolve()?.let { this.visitType(it) }
        }
    }

    private fun visitType(it: KSType) {
        if (it.declaration.packageName.asString().startsWith("org.kson")) {
            val typeDecl = it.declaration
            if (typeDecl is KSClassDeclaration) {
                this.pendingClassDecls.add(typeDecl)
            }
        } else {
            this.seenExternalTypes.add(simplifyType(it))
        }

        // Also visit concrete types that instantiate generics
        it.arguments.forEach {
            visitType(it.type!!.resolve())
        }
    }

    fun simplifyType(type: KSType?): SimpleType {
        val name = type!!.declaration.qualifiedName!!.asString()

        // Generics
        val params = mutableListOf<SimpleType>()
        type.arguments.forEach {
            params.add(simplifyType(it.type?.resolve()))
        }

        val isStackAllocated = stackAllocatedTypes.contains(name)
        return SimpleType(name, params.toList(), isStackAllocated)
    }
}
