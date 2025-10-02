import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.kson.Kson
import org.kson.Result
import java.io.File

/**
 * Gradle task that transpiles KSON files to YAML format.
 *
 * This task reads a KSON input file, transpiles it to YAML using the Kson library,
 * and writes the result to an output file. The task is configured with input/output
 * file tracking for Gradle's up-to-date checking.
 *
 * @property ksonFile The input KSON file to transpile
 * @property yamlFile The output YAML file to write the transpiled result to
 *
 * @throws GradleException if the input file does not exist or transpilation fails
 */
abstract class TranspileKsonToYaml : DefaultTask() {
    /**
     * The input KSON file to transpile.
     */
    @get:InputFile
    abstract val ksonFile: RegularFileProperty

    /**
     * The output YAML file to write the transpiled result to.
     */
    @get:OutputFile
    abstract val yamlFile: RegularFileProperty

    init {
        group = "kson-transpilation"
        description = "Transpile KSON to YAML file"
    }

    /**
     * Performs the transpilation from KSON to YAML.
     *
     * Reads the input KSON file, transpiles it using [Kson.toYaml] with embed tags
     * removed, and writes the result to the output YAML file.
     *
     * Before overwriting, checks if the YAML file has uncommitted changes in git that
     * don't match what the KSON would generate, preventing accidental data loss.
     *
     * @throws GradleException if the input file does not exist, transpilation fails,
     *         or the YAML file has uncommitted manual changes
     */
    @TaskAction
    fun transpile() {
        val ksonInput = ksonFile.get().asFile
        val yamlOutput = yamlFile.get().asFile

        if (!ksonInput.exists()) {
            throw GradleException("KSON input file does not exist: ${ksonInput.absolutePath}")
        }

        logger.lifecycle("Transpiling ${ksonInput.name} to ${yamlOutput.name}...")

        val ksonContent = ksonInput.readText()

        when (val result = Kson.toYaml(ksonContent, retainEmbedTags = false)) {
            is Result.Success -> {
                val newYamlContent = result.output

                // Check for uncommitted changes before overwriting
                if (yamlOutput.exists()) {
                    checkForUncommittedChanges(yamlOutput, newYamlContent)
                    checkFilesAreInSync(yamlOutput, newYamlContent)
                }

                yamlOutput.writeText(newYamlContent)
                logger.lifecycle("Successfully transpiled to ${yamlOutput.name}")
            }
            is Result.Failure -> {
                val errorMessages = result.errors.joinToString("\n") { error ->
                    "  [${error.severity}] Line ${error.start.line + 1}, Column ${error.start.column + 1}: ${error.message}"
                }
                throw GradleException("Failed to transpile KSON to YAML:\n$errorMessages")
            }
        }
    }

    /**
     * Checks if the YAML file has uncommitted changes that would be lost.
     *
     * Uses jgit to check if:
     * 1. The file has uncommitted changes (modified or staged)
     * 2. The current content differs from what we're about to write
     *
     * If both conditions are true, throws an exception to prevent data loss.
     *
     * @param yamlFile The YAML output file to check
     * @param newContent The new content that would be written
     * @throws GradleException if uncommitted changes would be lost
     */
    internal fun checkForUncommittedChanges(yamlFile: File, newContent: String) {
        val gitDir = findGitDirectory(yamlFile)
        if (gitDir == null) {
            logger.debug("Not in a git repository, skipping uncommitted changes check")
            return
        }

        try {
            val git = Git.open(gitDir)
            val status = git.status().call()

            // Get relative path from git root
            val relativePath = yamlFile.absolutePath.removePrefix(gitDir.absolutePath + File.separator)

            // Check if file has uncommitted changes
            // - modified: working tree changes not staged
            // - changed: changes staged for commit
            // - added: new file staged for commit
            // - removed: file staged for removal
            val hasUncommittedChanges = status.modified.contains(relativePath) ||
                    status.added.contains(relativePath) ||
                    status.changed.contains(relativePath) ||
                    status.removed.contains(relativePath)

            if (!hasUncommittedChanges) {
                logger.debug("No uncommitted changes in ${yamlFile.name}")
                return
            }

            // File has uncommitted changes - check if current content matches what we'd generate
            val currentContent = yamlFile.readText()
            if (currentContent != newContent) {
                throw GradleException(
                    """
                    |The YAML file ${yamlFile.absolutePath} has uncommitted changes that differ from
                    |what would be generated from ${ksonFile.get().asFile.name}.
                    |
                    |This suggests the YAML file was manually edited. To prevent accidental data loss:
                    |1. Review the changes: git diff ${relativePath}
                    |2. If you want to keep the manual changes:
                    |   - Commit them first, OR
                    |   - Port them to the .kson file, OR
                    |   - Stash them: git stash
                    |3. If you want to discard the manual changes and use KSON:
                    |   - Revert them: git checkout -- ${relativePath}
                    |   - Then run this task again
                    """.trimMargin()
                )
            }
        } catch (e: GradleException) {
            // Re-throw our own exceptions
            throw e
        } catch (e: Exception) {
            // If jgit fails for any other reason, log and continue
            logger.debug("Failed to check git status: ${e.message}")
        }
    }

    /**
     * Checks if the committed KSON and YAML files are in sync.
     *
     * Verifies that the committed version of the KSON file, when transpiled, produces
     * the committed version of the YAML file. This ensures the files are synchronized
     * in the git repository before allowing any transpilation.
     *
     * This check runs on committed (clean) files only and will fail even if both files
     * have no uncommitted changes, if the committed versions are out of sync.
     *
     * @param yamlFile The YAML output file to check
     * @param newContent The content that would be generated from the current KSON file
     * @throws GradleException if committed files are not in sync
     */
    internal fun checkFilesAreInSync(yamlFile: File, newContent: String) {
        val gitDir = findGitDirectory(yamlFile)
        if (gitDir == null) {
            logger.debug("Not in a git repository, skipping sync check")
            return
        }

        try {
            val git = Git.open(gitDir)
            val repository = git.repository
            val status = git.status().call()

            // Get relative paths from git root
            val yamlRelativePath = yamlFile.absolutePath.removePrefix(gitDir.absolutePath + File.separator)
            val ksonRelativePath = ksonFile.get().asFile.absolutePath.removePrefix(gitDir.absolutePath + File.separator)

            // Check if files have uncommitted changes
            val ksonHasChanges = status.modified.contains(ksonRelativePath) ||
                    status.added.contains(ksonRelativePath) ||
                    status.changed.contains(ksonRelativePath)
            val yamlHasChanges = status.modified.contains(yamlRelativePath) ||
                    status.added.contains(yamlRelativePath) ||
                    status.changed.contains(yamlRelativePath)

            // If either file has uncommitted changes, we can't check sync of committed versions
            if (ksonHasChanges || yamlHasChanges) {
                val changedFiles = mutableListOf<String>()
                if (ksonHasChanges) changedFiles.add(ksonRelativePath)
                if (yamlHasChanges) changedFiles.add(yamlRelativePath)

                throw GradleException(
                    """
                    |Cannot verify sync: Files must be committed before checking if KSON and YAML are in sync.
                    |
                    |Files with uncommitted changes: ${changedFiles.joinToString(", ")}
                    |
                    |This task requires both files to be committed so it can verify that:
                    |  - The committed KSON file generates the committed YAML file
                    |
                    |To fix this:
                    |1. Review your changes: git diff
                    |2. If the changes are intentional:
                    |   - Commit both files: git add ${changedFiles.joinToString(" ")} && git commit -m "Update config"
                    |3. If you want to discard changes:
                    |   - Revert changes: git checkout -- ${changedFiles.joinToString(" ")}
                    """.trimMargin()
                )
            }

            // Both files are clean - now check if committed versions are in sync
            // Get committed content of KSON file
            val headId = repository.resolve("HEAD")
            if (headId == null) {
                logger.debug("No HEAD commit found, skipping sync check")
                return
            }

            val treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, ksonRelativePath,
                repository.parseCommit(headId).tree)
            if (treeWalk == null) {
                logger.debug("KSON file not found in HEAD commit, skipping sync check")
                return
            }

            val ksonBlobId = treeWalk.getObjectId(0)
            val ksonCommittedContent = String(repository.open(ksonBlobId).bytes, Charsets.UTF_8)

            // Transpile committed KSON content
            val committedYamlResult = Kson.toYaml(ksonCommittedContent, retainEmbedTags = false)
            if (committedYamlResult !is Result.Success) {
                logger.debug("Failed to transpile committed KSON, skipping sync check")
                return
            }

            // Get committed content of YAML file
            val yamlTreeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, yamlRelativePath,
                repository.parseCommit(headId).tree)
            if (yamlTreeWalk == null) {
                logger.debug("YAML file not found in HEAD commit, skipping sync check")
                return
            }

            val yamlBlobId = yamlTreeWalk.getObjectId(0)
            val yamlCommittedContent = String(repository.open(yamlBlobId).bytes, Charsets.UTF_8)

            // Compare committed YAML with what committed KSON would generate
            if (committedYamlResult.output != yamlCommittedContent) {
                throw GradleException(
                    """
                    |Cannot transpile: Committed KSON and YAML files are out of sync!
                    |
                    |The committed version of ${ksonRelativePath} does NOT generate the
                    |committed version of ${yamlRelativePath}.
                    |
                    |This means the files were committed in an inconsistent state. The task cannot
                    |proceed because it cannot determine which file represents the source of truth.
                    |
                    |To fix this:
                    |1. Decide which file is correct:
                    |   - If KSON is correct: Run this task to regenerate YAML, then commit both
                    |   - If YAML is correct: Update KSON to match, then commit both
                    |2. OR manually synchronize the files and commit both together
                    """.trimMargin()
                )
            }

            logger.debug("Committed KSON and YAML files are in sync")
        } catch (e: GradleException) {
            // Re-throw our own exceptions
            throw e
        } catch (e: Exception) {
            // If jgit fails for any other reason, log and continue
            logger.debug("Failed to check file sync status: ${e.message}")
        }
    }

    /**
     * Finds the git directory for the given file by walking up the directory tree.
     *
     * @param file The file to find the git directory for
     * @return The git directory, or null if not in a git repository
     */
    internal fun findGitDirectory(file: File): File? {
        var current = file.absoluteFile.parentFile
        while (current != null) {
            val gitDir = File(current, ".git")
            if (gitDir.exists() && gitDir.isDirectory) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}