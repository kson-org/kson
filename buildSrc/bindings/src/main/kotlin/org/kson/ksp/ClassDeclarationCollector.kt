package org.kson.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSTopDownVisitor
import kotlinx.serialization.json.Json

class ClassDeclarationCollectorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ClassDeclarationCollector(environment.codeGenerator)
    }
}

class ClassDeclarationCollector(val codeGenerator: CodeGenerator) : SymbolProcessor {
    var done: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done) {
            return emptyList()
        }

        // Gather class declarations
        val visitor = TopLevelClassDeclarationVisitor()
        resolver.getAllFiles().forEach { file ->
            file.accept(visitor, Unit)
        }


        // Gather public API metadata
        val kotlinVisitor =
            PackageMetadataVisitor(visitor.topLevelClassDeclarations)
        while (kotlinVisitor.visitNextClass()) {
            // Iterate until all relevant classes have been visited
        }

        // Write the metadata
        val stream = codeGenerator.createNewFile(Dependencies.ALL_FILES, "org.kson", "public-api", "json")
        val writer = stream.writer()
        val metadata = kotlinVisitor.getPackageMetadata()
        val json = Json.encodeToString(metadata)
        writer.write(json)
        writer.close()

        done = true
        return arrayListOf()
    }
}

class TopLevelClassDeclarationVisitor() : KSTopDownVisitor<Unit, Unit>() {
    companion object {
        val allowedTopLevelClasses: HashSet<String> = HashSet(listOf(
            "org.kson.Kson",
            "org.kson.ast.AstNode"
        ))
    }

    val topLevelClassDeclarations: ArrayList<KSClassDeclaration> = arrayListOf()

    override fun defaultHandler(node: KSNode, data: Unit) {
        // Nothing to do here
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        val name = classDeclaration.qualifiedName?.asString() ?: classDeclaration.simpleName.asString()
        if (allowedTopLevelClasses.contains(name)) {
            topLevelClassDeclarations.add(classDeclaration)
        }
    }

}
