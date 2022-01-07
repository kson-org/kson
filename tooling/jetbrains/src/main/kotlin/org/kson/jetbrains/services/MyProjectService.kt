package org.kson.jetbrains.services

import com.intellij.openapi.project.Project
import org.kson.jetbrains.MyBundle

/**
 * todo jetbrains implement this
 */
class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
