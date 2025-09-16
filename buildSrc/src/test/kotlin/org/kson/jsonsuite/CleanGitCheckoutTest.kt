package org.kson.jsonsuite

import org.eclipse.jgit.api.Git
import org.kson.CleanGitCheckout
import org.kson.DirtyRepoException
import org.kson.NoRepoException
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

class CleanGitCheckoutTest {

    @Test
    fun testCheckoutOnNonExistentDir() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        )

        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(desiredCheckoutSHA, actualCheckoutSHA, "should have made new dir and checked out the desired SHA")
    }

    @Test
    fun testEnsureCleanGitCheckoutOnEmptyDir() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val cloneName = "GitFixture"

        // create an empty dir where the repository would be cloned...
        testDestinationDir.resolve(cloneName).toFile().mkdir()

        // and try to check out the repository...
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"
        assertFailsWith<NoRepoException>("should error on non-git dir") {
            CleanGitCheckout(
                gitTestFixturePath,
                desiredCheckoutSHA,
                testDestinationDir,
                "GitFixture"
            )
        }
    }

    @Test
    fun testEnsureCleanGitCheckoutOnCleanDir() {
        val testDestinationDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        )

        // reach into the checkout we just created and point it another SHA
        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val git = Git(repository)
        git.checkout().setName("296892b3392df3adeb7fb14e6b74140a311a1695").call()
        val currentRepoSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertNotEquals(
            currentRepoSHA,
            desiredCheckoutSHA,
            "should not currently be pointed to our desired SHA, " +
                    "because this test is trying verify we're able to check out our desired SHA from a " +
                    "clean git repo pointed to another SHA"
        )

        /**
         * create a new [CleanGitCheckout] in the same directory demanding our `desiredCheckoutSHA`
         */
        CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testDestinationDir,
            "GitFixture"
        )

        // this incantation gets us the currently checked out SHA
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(
            desiredCheckoutSHA,
            actualCheckoutSHA,
            "should have ensured our desired SHA is checked out")
    }

    @Test
    fun testEnsureCleanGitCheckoutOnDirtyDir() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir,
            "GitFixture"
        )

        val dirtyFileName = "dirty.txt"
        // reach into the checkout we just ensured and dirty it up
        Paths.get(cleanGitCheckout.checkoutDir.toString(), dirtyFileName).toFile().createNewFile()

        val dirtyMessage = "this message should be included in our exception"
        val exception = assertFailsWith<DirtyRepoException>("should error on a dirty git dir") {
            CleanGitCheckout(
                gitTestFixturePath,
                desiredCheckoutSHA,
                testCheckoutDir,
                "GitFixture",
                dirtyMessage
            )
        }

        assertTrue(
            exception.message!!.contains(cleanGitCheckout.checkoutDir.absolutePath),
            "Exception message should contain the absolute path of the dirty directory"
        )

        assertTrue(
            exception.message!!.contains(dirtyFileName),
            "Exception message should name any file dirtying up the directory"
        )

        assertTrue(
            exception.message!!.contains(dirtyMessage),
            "Exception message should contain the given additional message"
        )
    }

    @Test
    fun testEnsureCleanGitCheckoutIgnoresDSStore() {
        val testCheckoutDir = Paths.get(createTempDirectory("EnsureSuiteSourceFiles").toString())
        val desiredCheckoutSHA = "3a7625fe9e30a63102afbe74b078851ba7b185e7"

        val cleanGitCheckout = CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir,
            "GitFixture"
        )

        // Create a .DS_Store file in the checkout directory
        Paths.get(cleanGitCheckout.checkoutDir.toString(), ".DS_Store").toFile().createNewFile()

        // This should NOT throw an exception, since .DS_Store should be ignored
        CleanGitCheckout(
            gitTestFixturePath,
            desiredCheckoutSHA,
            testCheckoutDir,
            "GitFixture"
        )

        // Verify we're still at the correct SHA
        val repository = Git.open(cleanGitCheckout.checkoutDir).repository
        val actualCheckoutSHA = repository.refDatabase.firstExactRef("HEAD").objectId.name

        assertEquals(
            desiredCheckoutSHA,
            actualCheckoutSHA,
            "Should maintain desired behavior even with .DS_Store present"
        )
    }
}
