package com.classicchess.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudyToolsTest {
    @Test fun offlineReviewAndAnnotationRequestsKeepEnvelopeAndAccount() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                suspend fun call(action: suspend () -> Unit): okhttp3.mockwebserver.RecordedRequest {
                    server.enqueue(MockResponse().setBody("{}")); action(); return server.takeRequest()
                }
                val batch = call { api.accountPuzzleBatch("fixture", 50, "missed") }
                assertEquals("50", batch.requestUrl!!.queryParameter("limit"))
                assertEquals("missed", batch.requestUrl!!.queryParameter("result"))
                assertEquals("missed", call { api.accountPuzzleNext("fixture", result = "missed") }.requestUrl!!.queryParameter("result"))
                assertEquals("missed", call { api.accountPuzzleHistory("fixture", result = "missed") }.requestUrl!!.queryParameter("result"))
                val event = "f39112c7-f108-475f-9d44-98e1765bf538"
                val attempt = call { api.accountPuzzleResult("p-00000001", "missed", "fixture", event, 3, "a".repeat(64)) }
                val body = Json.parseToJsonElement(attempt.body.readUtf8()).jsonObject
                assertEquals(event, body["event_id"]!!.jsonPrimitive.content)
                assertEquals("3", body["generation"]!!.jsonPrimitive.content)
                assertEquals("Bearer fixture", attempt.getHeader("Authorization"))
                val search = call { api.accountNotebookAnnotationSearch("fixture", q = "king & pawn", cursor = "a:b") }
                assertEquals("king & pawn", search.requestUrl!!.queryParameter("q"))
                assertEquals("a:b", search.requestUrl!!.queryParameter("cursor"))
                assertEquals("Bearer fixture", search.getHeader("Authorization"))
                assertFailsWith<ApiException> { api.accountPuzzleBatch("fixture", 101) }
                assertFailsWith<ApiException> { api.accountPuzzleResult("p-00000001", "missed", "fixture", generation = 3) }
            }
        }
        Unit
    }
}
