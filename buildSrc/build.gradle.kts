import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.internal.impldep.com.jcraft.jsch.Session
import org.gradle.internal.impldep.org.eclipse.jgit.api.Git
import org.gradle.internal.impldep.org.eclipse.jgit.transport.JschConfigSessionFactory
import org.gradle.internal.impldep.org.eclipse.jgit.transport.OpenSshConfig
import org.gradle.internal.impldep.org.eclipse.jgit.transport.SshSessionFactory
import org.gradle.internal.impldep.org.eclipse.jgit.transport.SshTransport
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
val jsonTestSuiteRepoUrl = "https://github.com/nst/JSONTestSuite.git"
val jsonTestSuiteSHA = "d64aefb55228d9584d3e5b2433f720ea8fd00c82"
val cloneDir = file("${rootDir}/support/jsonsuite/JSONTestSuite")

tasks {
    register<JavaExec>(cloneJsonTestSuiteProjectTask) {
        // This makes us re-clone if the SHA changes
        inputs.property("sha", jsonTestSuiteSHA)

        environment("GIT_SSH_COMMAND", "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no")

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
    val sessionFactory = object : JschConfigSessionFactory() {
        override fun configure(hc: OpenSshConfig.Host, session: Session) {
            session.setConfig("StrictHostKeyChecking", "no")
        }
    }

    val sshSessionFactory: SshSessionFactory = sessionFactory

    SshSessionFactory.setInstance(sshSessionFactory)

    Git.cloneRepository()
        .setURI(url)
        .setDirectory(dir)
        .setTransportConfigCallback { transport ->
            val sshTransport = transport as SshTransport
            sshTransport.sshSessionFactory = sshSessionFactory
        }
        .call()
}

fun checkoutCommit(dir: File, commit: String) {
    val git = Git.open(dir)
    git.checkout().setName(commit).call()
}