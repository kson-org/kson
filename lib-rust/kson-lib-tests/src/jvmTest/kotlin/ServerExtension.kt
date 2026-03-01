import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kson.Kson
import java.io.File
import java.net.Socket

/**
 * A JUnit extension that makes sure a KSON server is running before all tests and gets
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

            val releaseBuildDir = File(System.getProperty("releaseBuildDir"))
            val port = 8081
            Kson.setPort(port)

            val processBuilder = ProcessBuilder(listOf("./kson-test-server", port.toString()))
                .directory(releaseBuildDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true);

            processBuilder.environment()["LD_LIBRARY_PATH"] = releaseBuildDir.absolutePath

            process = processBuilder.start()

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
