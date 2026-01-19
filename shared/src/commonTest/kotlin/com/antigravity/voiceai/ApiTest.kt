package com.antigravity.voiceai

import com.antigravity.voiceai.agents.RealApiTestBase
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ApiTest : RealApiTestBase() {

    @Test
    fun `test basic http request`() = runBlocking {
        withHttpClient { client ->
            testHttpRequest(
                client = client,
                url = "https://www.google.com",
                testName = "Basic HTTP"
            ) { response, responseBody ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(responseBody.isNotEmpty(), "Response body should not be empty")
            }
        }
    }
}
