package org.kson

import org.kson.api.*
import kotlin.test.*

class KsonSmokeTest : KsonServiceSmokeTest() {
    override fun createService(): KsonService {
        return Kson
    }

}
