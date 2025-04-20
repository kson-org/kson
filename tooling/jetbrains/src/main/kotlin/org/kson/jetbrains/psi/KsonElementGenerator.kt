package org.kson.jetbrains.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.kson.jetbrains.file.KsonFileType

/**
 * Generator for creating KSON PSI elements.
 *
 * This class provides utilities for generating KSON PSI elements programmatically,
 * primarily for use in code manipulation scenarios like content updates and testing.
 *
 * The generator creates lightweight in-memory PSI elements and files that can be used
 * as replacements or for comparison without affecting the actual project files.
 *
 * @param project The project context in which the elements will be generated
 */

class KsonElementGenerator(project: Project) {
    private var myProject: Project = project

    /**
     * Create a lightweight in-memory [KsonEmbedContent] filled with `content`.
     *
     * @param content content of the embed content to be created
     * @return created embed content
     */
    fun createEmbedContent(content: String): KsonEmbedContent? {
        val fileContent =
            """
            |$$
            |$content$$
            """.trimMargin()
        val file = createDummyFile(fileContent)
        val embedBlock = file.firstChild as KsonEmbedBlock
        return embedBlock.embedContent
    }

    /**
     * Create lightweight in-memory [KsonPsiFile] filled with `content`.
     * By creating the file we get a PsiFile instance that we can navigate to get the PSI elements.
     *
     * @param content content of the file to be created
     * @return created file
     */
    private fun createDummyFile(content: String): PsiFile {
        val psiFileFactory = PsiFileFactory.getInstance(myProject)
        return psiFileFactory.createFileFromText(
            "dummy." + KsonFileType.defaultExtension,
            KsonFileType,
            content
        )
    }
}