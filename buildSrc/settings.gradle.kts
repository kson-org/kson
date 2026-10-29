buildCache {
    local {
        // TODO buildSrc should inherit the root settings' cache switch per Gradle's docs, but does not
        isEnabled = providers.gradleProperty("release").orNull != "true"
    }
}
