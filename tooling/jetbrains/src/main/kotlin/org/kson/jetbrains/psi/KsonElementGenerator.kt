package org.kson.jetbrains.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.kson.jetbrains.file.KsonFileType

class KsonElementGenerator(project: Project) {
    private var myProject: Project = project

    /**
     * Create lightweight in-memory [KsonPsiFile] filled with `content`.
     *
     * @param content content of the file to be created
     * @return created file
     */
    fun createDummyFile(content: String): PsiFile {
        val psiFileFactory = PsiFileFactory.getInstance(myProject)
        return psiFileFactory.createFileFromText(
            "dummy." + KsonFileType.defaultExtension,
            KsonFileType,
            content
        )
    }

    fun createEmbedContent(content: String): KsonEmbedContent? {
        val file_content =
            """
            |$$
            |$content$$
            """.trimMargin()
        val file = createDummyFile(file_content)
        val embedBlock = file.firstChild as KsonEmbedBlock
        return embedBlock.embedContent
    }
}