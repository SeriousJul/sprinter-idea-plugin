package com.github.seriousjul.sprinter.integration

import com.github.seriousjul.sprinter.SharedJvmExecutorService
import org.junit.jupiter.api.Test

class SharedJvmServiceTest : BaseIntegrationTest() {

    @Test
    fun testServiceIsAvailableInProject() {
        // This is a smoke test to ensure the service can be retrieved from the project instance.
        val service = project.getService(SharedJvmExecutorService::class.java)
        assertNotNull(service, "SharedJvmExecutorService should be available in the project")
    }
    
    private fun assertNotNull(obj: Any?, message: String) {
        if (obj == null) {
            throw AssertionError(message)
        }
    }
}