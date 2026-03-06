plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

repositories {
    mavenCentral()
}

group = "org.kson"
// [[kson-version-num]] - base version defined in buildSrc/src/main/kotlin/org/kson/KsonVersion.kt
val isRelease = project.findProperty("release") == "true"
version = org.kson.KsonVersion.getVersion(isRelease = isRelease)

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()

    coordinates("org.kson", "kson-service-api", org.kson.KsonVersion.getPublishVersion(rootProject.projectDir, isRelease = isRelease))

    pom {
        name.set("KSON API")
        description.set("A 💌 to the humans maintaining computer configurations")
        url.set("https://kson.org")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("dmarcotte")
                name.set("Daniel Marcotte")
                email.set("kson@kson.org")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/kson-org/kson.git")
            developerConnection.set("scm:git:git@github.com:kson-org/kson.git")
            url.set("https://github.com/kson-org/kson")
        }
    }
}
