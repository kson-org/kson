import org.kson.Kson
import org.kson.api.KsonService
import org.kson.api.KsonServiceSmokeTest

class SmokeTest : KsonServiceSmokeTest() {
    override fun createService(): KsonService {
        return Kson
    }
}