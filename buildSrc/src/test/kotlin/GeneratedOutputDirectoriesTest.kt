import GeneratedOutputDirectories.Companion.CARGO_TARGET
import GeneratedOutputDirectories.Companion.GENERATED_DIRECTORY_NAMES
import GeneratedOutputDirectories.Companion.GRADLE_BUILD
import GeneratedOutputDirectories.Companion.NODE_MODULES
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.internal.os.OperatingSystem
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedOutputDirectoriesTest {

    /**
     * Create each of [relativeDirectories] under a fresh temp directory, and return that directory
     */
    private fun treeOf(vararg relativeDirectories: String): File {
        val root = createTempDirectory("GeneratedOutputDirectoriesTest").toFile()
        relativeDirectories.forEach { File(root, it).mkdirs() }
        return root
    }

    /**
     * What [GeneratedOutputDirectories] finds under [root], as paths relative to it
     */
    private fun locateUnder(root: File, doNotSearch: Set<File> = emptySet()): List<String> =
        GeneratedOutputDirectories(root, doNotSearch).locate()
            .map { it.relativeTo(root).invariantSeparatorsPath }

    /**
     * Every path in [repository]'s index, relative to the repo root and `/`-separated
     */
    private fun trackedPathsIn(repository: Repository): List<String> {
        val index = repository.readDirCache()
        return (0 until index.entryCount).map { index.getEntry(it).pathString }
    }

    /**
     * The git checkout this test class was compiled into, or `null` if it was not compiled inside one
     */
    private fun enclosingCheckout(): Repository? {
        val compiledInto = File(javaClass.protectionDomain.codeSource.location.toURI())
        val builder = FileRepositoryBuilder().findGitDir(compiledInto)
        return if (builder.gitDir == null) null else builder.build()
    }

    @Test
    fun everyGeneratedDirectoryNameIsRecognized() {
        val root = treeOf(*GENERATED_DIRECTORY_NAMES.toTypedArray())

        assertEquals(
            GENERATED_DIRECTORY_NAMES.sorted(),
            locateUnder(root),
            "Every name we publish as generated output should be reported when found"
        )
    }

    @Test
    fun generatedOutputIsFoundAtAnyDepth() {
        val root = treeOf(GRADLE_BUILD, "crates/parser/$CARGO_TARGET", "clients/web/$NODE_MODULES")

        assertEquals(
            listOf(GRADLE_BUILD, "clients/web/$NODE_MODULES", "crates/parser/$CARGO_TARGET"),
            locateUnder(root)
        )
    }

    @Test
    fun generatedOutputIsNotSearchedForMoreGeneratedOutput() {
        val root = treeOf("crates/parser/$CARGO_TARGET/debug/$GRADLE_BUILD", "crates/parser/$CARGO_TARGET/debug/deps")

        assertEquals(
            listOf("crates/parser/$CARGO_TARGET"),
            locateUnder(root),
            "Should report the outermost generated directory and stop, not walk what is inside it"
        )
    }

    @Test
    fun generatedOutputWeAreToldNotToSearchIsLeftToItsOwner() {
        val root = treeOf(GRADLE_BUILD, "nested/$GRADLE_BUILD", "nested/deeper/$CARGO_TARGET")

        assertEquals(
            listOf(GRADLE_BUILD),
            locateUnder(root, doNotSearch = setOf(File(root, "nested"))),
            "Everything under `nested` belongs to whoever searches from `nested`"
        )
    }

    @Test
    fun authoredDirectoriesAreLeftAlone() {
        val root = treeOf("src/commonMain/kotlin", "docs", "assets/images")

        assertEquals(emptyList(), locateUnder(root))
    }

    @Test
    fun filesNamedLikeGeneratedDirectoriesAreNotReported() {
        val root = treeOf("src")
        File(root, GRADLE_BUILD).writeText("a file, not a directory")

        assertEquals(emptyList(), locateUnder(root))
    }

    @Test
    fun symlinkedGeneratedOutputIsLeftToTheTreeThatOwnsIt() {
        assumeFalse("creating symlinks needs elevated privileges on Windows", OperatingSystem.current().isWindows)
        val elsewhere = treeOf(CARGO_TARGET)
        val root = treeOf("src")
        Files.createSymbolicLink(File(root, "linked").toPath(), elsewhere.toPath())

        assertEquals(
            emptyList(),
            locateUnder(root),
            "Following the link would report a directory outside the tree we were asked about"
        )
    }

    /**
     * Guards the assumption the whole design rests on: that [GENERATED_DIRECTORY_NAMES] only ever
     * names generated output. We do this by consulting Git's index: if a file we think is generated
     * is ever reported by git as tracked, we want to raise that as an issue.
     */
    @Test
    fun trackedContentIsNeverTreatedAsGeneratedOutput() {
        val checkout = enclosingCheckout()
        assumeTrue("reading the git index needs a git checkout to read it from", checkout != null)

        checkout!!.use { repository ->
            val trackedPaths = trackedPathsIn(repository)

            val locatedHoldingTrackedFiles =
                GeneratedOutputDirectories(repository.workTree).locate().mapNotNull { directory ->
                    val relativePath = directory.relativeTo(repository.workTree).invariantSeparatorsPath
                    trackedPaths.firstOrNull { it.startsWith("$relativePath/") }
                        ?.let { "$relativePath (tracks $it)" }
                }

            assertEquals(
                emptyList(),
                locatedHoldingTrackedFiles,
                "These directories are named like generated output but hold files tracked in git. " +
                    "Either the content belongs somewhere else, or the name should leave " +
                    GeneratedOutputDirectories.Companion::GENERATED_DIRECTORY_NAMES.name
            )
        }
    }
}
