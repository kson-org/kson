package org.kson.public_api_metadata_collector

import kotlinx.serialization.Serializable

@Serializable
class SimplePackageMetadata(val classes: HashMap<String, SimpleClassMetadata>, val nestedClasses: HashMap<String, ArrayList<String>>, val externalTypes: List<String>)

@Serializable
class SimpleClassMetadata(val name: FullyQualifiedClassName, val supertypes: Array<FullyQualifiedClassName>, val constructors: Array<SimpleConstructorMetadata>, val functions: Array<SimpleFunctionMetadata>, val enumConstants: Array<String>, val docString: String?)

@Serializable
class SimpleConstructorMetadata(val params: List<SimpleParamMetadata>, val docString: String?)

@Serializable
class SimpleFunctionMetadata(val name: String, val static: Boolean, val params: List<SimpleParamMetadata>, val returnType: SimpleType?, val docString: String?)

@Serializable
class SimpleParamMetadata(val name: String, val type: SimpleType)

@Serializable
class FullyQualifiedClassName(val name: String)

@Serializable
class SimpleType(val classifier: String)
