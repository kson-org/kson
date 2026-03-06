import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kson.Kson
import java.io.File
import java.net.Socket

/**
 * A JUnit extension that makes sure the Python KSON server is running before all tests and gets
 * shut down at the end
 */
class ServerExtension : BeforeAllCallback, AutoCloseable {

    companion object {
        private var started = false
        private var process: Process? = null
    }

    override fun beforeAll(context: ExtensionContext) {
        if (!started) {
            started = true

            // Register for cleanup when the root context closes (after all tests)
            context.root.getStore(ExtensionContext.Namespace.GLOBAL)
                .put("serverExtension", this)

            val libPythonDir = File(System.getProperty("libPythonDir"))
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val uvwCommand = if (isWindows) listOf("cmd", "/c", "uvw.bat") else listOf("./uvw")
            val port = 8082

            Kson.setPort(port)

            process = ProcessBuilder(uvwCommand + listOf("run", "python", "tests/api_server.py", port.toString()))
                .directory(libPythonDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true)
                .start()

            // Wait for readiness
            repeat(30) {
                try {
                    Socket("localhost", port).close()
                    return
                } catch (_: Exception) {
                    Thread.sleep(1000)
                }
            }
            throw RuntimeException("Server did not start in time")
        }
    }

    override fun close() {
        process?.destroy()
        process?.waitFor()
    }
}
