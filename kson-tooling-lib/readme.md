# Tooling API for KSON

This subproject defines [KsonTooling.kt](src/commonMain/kotlin/org/kson/KsonTooling.kt), the public Kotlin Multiplatform interface for building tooling support for KSON.

## Available Tools

The tooling API provides schema-aware information extraction at any location in a KSON document:

- **Schema Information Retrieval** - Extract schema documentation, type information, constraints, and metadata for any position in a document
- **Completion Suggestions** - Get valid property names, enum values, and type-appropriate suggestions based on the schema
- **Document Path Navigation** - Navigate through KSON documents using schema-aware path building

These capabilities can be used to build IDEs, linters, documentation generators, CLI tools, web editors, or any other tooling that needs schema-aware document analysis.