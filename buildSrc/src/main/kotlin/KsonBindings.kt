class KsonBindings(val dir: String, val testCommand: String) {
    companion object {
        val ALL: List<KsonBindings> = listOf(KsonBindings("lib-python", "uv run pytest"))
    }
}