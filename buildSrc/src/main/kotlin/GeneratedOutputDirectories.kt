import java.io.File
import java.nio.file.Files

/**
 * [GeneratedOutputDirectories] is used to [locate] the directories under [searchRoot] containing generated output
 * rather than source: build output, installed dependencies, and tool-managed environments.
 *
 * These are found by directory name rather than a specific path so that conventional excludes may be found
 * wherever they appear (i.e. `.pixi/` anywhere Pixi is used, `node_modules/` for every `package.json`),
 * emulating how such directories are ignored in a `.gitignore`.
 *
 * Note that we don't dive into a generated output directory once it is found since the whole thing is
 * generated. Directories listed in [doNotSearch] are totally skipped by our search.
 */
class GeneratedOutputDirectories(
    private val searchRoot: File,
    private val doNotSearch: Set<File> = emptySet()
) {
    /**
     * The generated output directories beneath [searchRoot], ordered depth-first and alphabetically
     * so that a given tree always yields the same list.
     */
    fun locate(): List<File> = mutableListOf<File>().also { collectInto(it, searchRoot) }

    private fun collectInto(found: MutableList<File>, directory: File) {
        val children = directory.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            if (!child.isDirectory || child in doNotSearch) {
                continue
            }

            // symlinks are not followed: a link may point outside [searchRoot] entirely, and
            // reporting a directory that lives outside the tree we were asked about would attribute
            // generated output to the wrong project (and a link cycle would never terminate)
            if (Files.isSymbolicLink(child.toPath())) {
                continue
            }

            if (child.name in GENERATED_DIRECTORY_NAMES) {
                found.add(child)
            } else {
                collectInto(found, child)
            }
        }
    }

    /**
     * The names of generated directories we exclude
     */
    companion object {
        // Gradle
        const val GRADLE_BUILD = "build"
        const val GRADLE_CACHE = ".gradle"
        const val KOTLIN_CACHE = ".kotlin"

        // IntelliJ Platform Gradle Plugin, which caches the IDE distributions it builds against
        const val INTELLIJ_PLATFORM_CACHE = ".intellijPlatform"

        // Cargo
        const val CARGO_TARGET = "target"

        // npm, and the compiler and bundler output of our LSP clients
        const val NODE_MODULES = "node_modules"
        const val TYPESCRIPT_OUT = "out"
        const val BUNDLER_DIST = "dist"

        // Pixi, and the Python environment it manages
        const val PIXI_ENVIRONMENTS = ".pixi"
        const val PYTHON_VIRTUALENV = ".venv"

        // the VS Code installs `vscode-test` downloads to run the extension's tests against
        const val VSCODE_TEST = ".vscode-test"
        const val VSCODE_TEST_WEB = ".vscode-test-web"

        /** Every directory name above: the whole of what [locate] treats as generated output */
        val GENERATED_DIRECTORY_NAMES = setOf(
            GRADLE_BUILD, GRADLE_CACHE, KOTLIN_CACHE,
            INTELLIJ_PLATFORM_CACHE,
            CARGO_TARGET,
            NODE_MODULES, TYPESCRIPT_OUT, BUNDLER_DIST,
            PIXI_ENVIRONMENTS, PYTHON_VIRTUALENV,
            VSCODE_TEST, VSCODE_TEST_WEB
        )
    }
}
