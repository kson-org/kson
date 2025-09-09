import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Convenience function to create a PixiExecTask with less boilerplate
 */
fun TaskContainer.pixiExec(
    name: String,
    vararg command: String,
    configure: PixiExecTask.() -> Unit = {}
): TaskProvider<PixiExecTask> {
    return register(name, PixiExecTask::class.java) { task ->
        task.command.set(command.toList())
        task.pixiInstallDir.convention("../.pixi")
        task.autoGenerateWrapper.convention(true)
        task.captureOutput.convention(false)
        task.configure()
    }
}

/**
 * Convenience function to create a PixiWrapperTask
 */
fun TaskContainer.pixiWrapper(
    name: String = "generatePixiWrapper",
    configure: PixiWrapperTask.() -> Unit = {}
): TaskProvider<PixiWrapperTask> {
    return register(name, PixiWrapperTask::class.java) { task ->
        task.outputDir.set(task.project.layout.projectDirectory)
        task.pixiInstallDir.set("../.pixi")
        task.configure()
    }
}