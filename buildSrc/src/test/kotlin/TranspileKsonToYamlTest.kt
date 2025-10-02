import org.eclipse.jgit.api.Git
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranspileKsonToYamlTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanity check task`() {
        val project = ProjectBuilder.builder().build()
        project.tasks.register("transpileKsonToYaml", TranspileKsonToYaml::class.java)
        val task = project.getTasksByName("transpileKsonToYaml", false)
            .iterator().next()
        assertTrue(task is TranspileKsonToYaml)
    }

    @Test
    fun `transpile simple KSON to YAML`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = tempFolder.newFile("test.kson")
        ksonFile.writeText("key: value\n")

        val yamlFile = File(tempFolder.root, "test.yml")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        task.transpile()

        assertTrue(yamlFile.exists())
        assertEquals("key: value", yamlFile.readText())
    }

    @Test
    fun `throws exception when KSON file does not exist`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(tempFolder.root, "nonexistent.kson")
        val yamlFile = File(tempFolder.root, "test.yml")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val exception = assertFailsWith<GradleException> {
            task.transpile()
        }
        assertTrue(exception.message!!.contains("does not exist"))
    }

    @Test
    fun `throws exception on invalid KSON`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = tempFolder.newFile("invalid.kson")
        ksonFile.writeText("invalid: [unclosed bracket\n")

        val yamlFile = File(tempFolder.root, "test.yml")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val exception = assertFailsWith<GradleException> {
            task.transpile()
        }
        assertTrue(exception.message!!.contains("Failed to transpile"))
    }

    @Test
    fun `findGitDirectory returns null when not in git repo`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val file = tempFolder.newFile("test.txt")

        assertNull(task.findGitDirectory(file))
    }

    @Test
    fun `findGitDirectory finds git directory`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val gitDir = tempFolder.newFolder(".git")
        val file = File(tempFolder.root, "test.txt")
        file.createNewFile()

        assertEquals(tempFolder.root, task.findGitDirectory(file))
    }

    @Test
    fun `checkForUncommittedChanges does nothing when not in git repo`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val yamlFile = tempFolder.newFile("test.yml")
        yamlFile.writeText("old content")

        // Should not throw
        task.checkForUncommittedChanges(yamlFile, "new content")
    }

    @Test
    fun `checkForUncommittedChanges allows overwrite when no uncommitted changes`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("initial content")

        // Add and commit the file
        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Should not throw - file has no uncommitted changes
        task.checkForUncommittedChanges(yamlFile, "new content")
    }

    @Test
    fun `checkForUncommittedChanges allows overwrite when uncommitted changes match new content`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("initial content")

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Modify the file
        yamlFile.writeText("modified content")

        // Should not throw - uncommitted changes match what we'd write
        task.checkForUncommittedChanges(yamlFile, "modified content")
    }

    @Test
    fun `checkForUncommittedChanges throws when uncommitted changes differ from new content`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("initial content")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Modify the YAML file manually
        yamlFile.writeText("manually modified content")

        // Should throw - uncommitted changes differ from what we'd generate
        val exception = assertFailsWith<GradleException> {
            task.checkForUncommittedChanges(yamlFile, "different content from kson")
        }
        assertTrue(exception.message!!.contains("uncommitted changes"))
        assertTrue(exception.message!!.contains("manually edited"))
    }

    @Test
    fun `transpile prevents overwriting uncommitted manual changes`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: newvalue")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("key: oldvalue\n")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Manually modify YAML
        yamlFile.writeText("key: manualchange\n")

        // Should throw when trying to transpile
        val exception = assertFailsWith<GradleException> {
            task.transpile()
        }
        assertTrue(exception.message!!.contains("uncommitted changes"))
    }

    @Test
    fun `transpile succeeds when YAML file does not exist`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Should succeed - YAML file doesn't exist yet
        task.transpile()

        assertTrue(yamlFile.exists())
        assertEquals("key: value", yamlFile.readText())
    }

    @Test
    fun `checkFilesAreInSync throws when KSON and YAML file are committed out of sync`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("key: value")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Modify KSON file
        ksonFile.writeText("key: newvalue")

        // Should throw - KSON file has uncommitted changes
        val exception = assertFailsWith<GradleException> {
            task.checkFilesAreInSync(yamlFile, "key: value")
        }
        assertTrue(exception.message!!.contains("verify sync"))
        assertTrue(exception.message!!.contains("test.kson"))
    }

    @Test
    fun `checkFilesAreInSync throws when committed out of sync`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("key: value")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Modify YAML file
        yamlFile.writeText("key: newvalue")

        // Should throw - YAML file has uncommitted changes
        val exception = assertFailsWith<GradleException> {
            task.checkFilesAreInSync(yamlFile, "key: value")
        }
        assertTrue(exception.message!!.contains("verify sync"))
        assertTrue(exception.message!!.contains("test.yml"))
    }

    @Test
    fun `checkFilesAreInSync succeeds when both files are committed and in sync`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("key: value")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Should not throw - both files are committed and in sync
        task.checkFilesAreInSync(yamlFile, "key: value")
    }

    @Test
    fun `checkFilesAreInSync throws when committed files are out of sync`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("key: wrongvalue")  // YAML doesn't match what KSON would generate

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit with out-of-sync files")
            .setAuthor("Test", "test@example.com")
            .call()

        // Should throw - committed files are out of sync
        val exception = assertFailsWith<GradleException> {
            task.checkFilesAreInSync(yamlFile, "key: value")
        }
        assertTrue(exception.message!!.contains("out of sync"))
    }

    @Test
    fun `transpile fails when files are not in sync`() {
        val gitRepo = tempFolder.newFolder("repo")
        Git.init().setDirectory(gitRepo).call()

        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("transpile", TranspileKsonToYaml::class.java)

        val ksonFile = File(gitRepo, "test.kson")
        ksonFile.writeText("key: value")
        val yamlFile = File(gitRepo, "test.yml")
        yamlFile.writeText("key: value")

        task.ksonFile.set(ksonFile)
        task.yamlFile.set(yamlFile)

        val git = Git.open(gitRepo)
        git.add().addFilepattern("test.yml").call()
        git.add().addFilepattern("test.kson").call()
        git.commit()
            .setMessage("Initial commit")
            .setAuthor("Test", "test@example.com")
            .call()

        // Modify KSON file
        ksonFile.writeText("key: newvalue")

        // Should throw when trying to transpile
        val exception = assertFailsWith<GradleException> {
            task.transpile()
        }
        assertTrue(exception.message!!.contains("verify sync"))
    }
}