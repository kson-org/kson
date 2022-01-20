package org.kson.jetbrains.services

import com.intellij.openapi.project.Project
import org.kson.jetbrains.KsonBundle

/**
 * todo jetbrains implement this
 */
class MyProjectService(project: Project) {

    init {
        println(KsonBundle.message("projectService", project.name))
    }
}
