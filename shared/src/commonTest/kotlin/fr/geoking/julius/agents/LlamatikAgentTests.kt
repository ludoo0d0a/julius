package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LlamatikAgentTests {

    @Test
    fun process_usesFakeBackend_returnsBackendResponse() = runBlocking {
        val backend = FakeLlamaBackend(response = "Hello from Llamatik model.")
        val agent = LlamatikAgent(modelPath = "models/test.gguf", backend = backend)
        val response = agent.process("Hi")
        assertEquals("Hello from Llamatik model.", response.text)
        assertTrue(response.audio == null)
        assertEquals(listOf("models/test.gguf"), backend.getModelPathCalls)
        assertEquals(1, backend.initGenerateModelCalls.size)
        assertEquals(1, backend.generateWithContextCalls.size)
        val (system, context, user) = backend.generateWithContextCalls.single()
        assertTrue(system.contains("voice assistant"))
        assertEquals("", context)
        assertEquals("Hi", user)
    }

    @Test
    fun process_initFails_throwsNetworkException() = runBlocking {
        val backend = FakeLlamaBackend(initResult = false)
        val agent = LlamatikAgent(backend = backend)
        try {
            agent.process("Hi")
            throw AssertionError("Expected NetworkException")
        } catch (e: NetworkException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("Failed to load") || e.message!!.contains("initialize"))
        }
    }

    @Test
    fun process_initThrows_throwsNetworkExceptionWithCause() = runBlocking {
        val backend = FakeLlamaBackend(initThrow = RuntimeException("No model file"))
        val agent = LlamatikAgent(backend = backend)
        try {
            agent.process("Hi")
            throw AssertionError("Expected NetworkException")
        } catch (e: NetworkException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("Failed to load") || e.message!!.contains("No model file"))
        }
    }

    @Test
    fun process_generateThrows_throwsNetworkException() = runBlocking {
        val backend = FakeLlamaBackend(generateThrow = IllegalStateException("Inference error"))
        val agent = LlamatikAgent(backend = backend)
        try {
            agent.process("Hi")
            throw AssertionError("Expected NetworkException")
        } catch (e: NetworkException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("Error with Llamatik") || e.message!!.contains("Inference error"))
        }
    }

    @Test
    fun process_usesDefaultModelPath() = runBlocking {
        val backend = FakeLlamaBackend(response = "Ok")
        val agent = LlamatikAgent(backend = backend)
        agent.process("test")
        assertEquals(listOf("models/phi-2.Q4_0.gguf"), backend.getModelPathCalls)
    }

    @Test
    fun shutdown_callsBackendShutdown() {
        val backend = FakeLlamaBackend(response = "Ok")
        val agent = LlamatikAgent(backend = backend)
        runBlocking { agent.process("Hi") }
        agent.shutdown()
        assertEquals(1, backend.shutdownCalls)
    }

    @Test
    fun listModels_throwsUnsupportedOperation() = runBlocking {
        val agent = LlamatikAgent(backend = FakeLlamaBackend())
        try {
            agent.listModels()
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("does not support listing models"))
        }
    }
}
