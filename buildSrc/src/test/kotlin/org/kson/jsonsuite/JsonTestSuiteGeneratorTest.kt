package org.kson.jsonsuite

import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.Files.createTempDirectory
import java.nio.file.Paths
import java.util.zip.ZipFile
import kotlin.test.*

/**
 * Unzip the git directory test fixture we prepared for these tests into a temp dir
 */
private val gitTestFixturePath = run {
    val gitTestFixtureURI =
        ({}.javaClass.getResource("/GitTestFixture.zip")?.file)
            ?: throw RuntimeException("Expected to find this test resource!")

    val tmpDir = createTempDirectory("GitTestFixtureUnzipped").toString()

    ZipFile(gitTestFixtureURI).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                val outputFile = File(tmpDir, entry.name)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    File(tmpDir, "GitTestFixture").absolutePath
}

class JsonTestSuiteGeneratorTest {

    @Test
    fun testEnsureCleanGitCheckoutOnEmptyDir() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString(), "JSONTestSuite")
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        ensureCleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir
        )

        val repository = Git.open(testCheckoutDir.toFile()).repository
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(desiredCheckoutSHA, actualCheckoutSHA, "should have made new dir and checked out the desired SHA")
    }

    @Test
    fun testEnsureCleanGitCheckoutOnCleanDir() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString(), "JSONTestSuite")
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        /**
         * pre-clone the git repo so we can run [ensureCleanGitCheckout] on it
         */
        Git.cloneRepository()
            .setURI(gitTestFixturePath)
            .setDirectory(testCheckoutDir.toFile())
            .call()

        val repository = Git.open(testCheckoutDir.toFile()).repository
        val currentRepoSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertNotEquals(
            currentRepoSHA,
            desiredCheckoutSHA,
            "should not currently be pointed to our desired SHA," +
                    "because this test verifies we're able to check out our desired SHA"
        )

        ensureCleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir
        )

        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(
            desiredCheckoutSHA,
            actualCheckoutSHA,
            "should have ensured our desired SHA is checked out")
    }

    @Test
    fun testEnsureCleanGitCheckoutOnDirtyDir() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString(), "JSONTestSuite")
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        /**
         * pre-clone the git repo so we can dirty it before running [ensureCleanGitCheckout]
         */
        Git.cloneRepository()
            .setURI(gitTestFixturePath)
            .setDirectory(testCheckoutDir.toFile())
            .call()

        Paths.get(testCheckoutDir.toString(), "dirty.txt").toFile().createNewFile()

        assertFailsWith<DirtyRepoException>("should error on a dirty git dir") {
            ensureCleanGitCheckout(
                gitTestFixturePath,
                desiredCheckoutSHA,
                testCheckoutDir
            )
        }
    }

    /**
     * Integration test for [JsonTestSuiteGenerator.generate] that runs against our actual checkout of [JSONTestSuite](https://github.com/nst/JSONTestSuite)
     * that lives in `kson/buildSrc/support/jsonsuite/JSONTestSuite`
     */
    @Test
    fun testGenerate() {
        val tempDirectory = createTempDirectory("JsonTestSuiteGeneratorTest")

        val testPackageName = "org.json.parser.json.generated.TEST"

        // the directory this test is currently running in
        val runningPath = Paths.get("").toAbsolutePath()

        // this test is sometimes run from the project root (as part of the root project),
        // and sometimes run in buildSrc/ (as part of the buildSrc subproject), so this
        // finds the actual project root for us
        val projectRoot = if (runningPath.fileName.toString() == "buildSrc") {
            runningPath.parent
        } else {
            runningPath
        }

        val jsonTestSuiteGenerator = JsonTestSuiteGenerator(
            "d64aefb55228d9584d3e5b2433f720ea8fd00c82",
            projectRoot,
            tempDirectory,
            testPackageName
        )
        jsonTestSuiteGenerator.generate()

        assertTrue(
            jsonTestSuiteGenerator.testDefinitionFilesDir.toFile().isDirectory,
            "test suite directory should exist and be a directory"
        )

        val generatedTestContents = jsonTestSuiteGenerator.generatedTestPath.toFile().readText()

        assertTrue(
            generatedTestContents
                // sanity check this is the test class we expect to be generated by checking its classname
                .contains("class JsonSuiteTest"),
            "test class should be generated"
        )

        val packageNameStartIndex = "package ".length
        assertEquals(
            testPackageName,
            generatedTestContents.substring(packageNameStartIndex, packageNameStartIndex + testPackageName.length),
            "generated test file should start with configured package declaration"
        )
    }
}