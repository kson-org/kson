import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

plugins {
    kotlin("jvm") version "1.6.21"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.6.0.202305301015-r")
    }
}

repositories {
    mavenCentral()
}


val cloneJsonTestSuiteProjectTask = "cloneJsonTestSuitProject"
val jsonTestSuiteRepoUrl = "ssh://git@github.com:nst/JSONTestSuite.git"
val jsonTestSuiteSHA = "d64aefb55228d9584d3e5b2433f720ea8fd00c82"
val cloneDir = file("${rootDir}/support/jsonsuite/JSONTestSuite")

tasks {
    register(cloneJsonTestSuiteProjectTask) {
        // This makes us re-clone if the SHA changes
        inputs.property("sha", jsonTestSuiteSHA)

        doLast {
            if (cloneDir.exists()) {
                cloneDir.deleteRecursively()
            }
            cloneRepository(jsonTestSuiteRepoUrl, cloneDir)
            checkoutCommit(cloneDir, jsonTestSuiteSHA)
        }
    }

    named("compileKotlin") {
        dependsOn(cloneJsonTestSuiteProjectTask)
    }

    withType<Test> {
        testLogging.showStandardStreams = true
        testLogging.events = setOf(PASSED, SKIPPED, FAILED, STANDARD_OUT, STANDARD_ERROR)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}

dependencies {
    implementation(gradleApi())
    testImplementation(kotlin("test"))
}

fun cloneRepository(url: String, dir: File) {
    try {
        Git.cloneRepository()
            .setURI(url)
            .setDirectory(dir)
            .call()
    } catch (e: GitAPIException) {
        e.printStackTrace()
    }
}

fun checkoutCommit(dir: File, commit: String) {
    val git = Git.open(dir)
    git.checkout().setName(commit).call()
}