package com.classicchess.api

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class SharedContractTest {
    private val fixtures = Json.parseToJsonElement(File(System.getProperty("classicchess.fixtures")).readText()).jsonObject
    private lateinit var server: MockWebServer
    private lateinit var api: ClassicChessClient
    @BeforeTest fun start() {
        server = MockWebServer().apply { start() }
        api = ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { api.close(); server.shutdown() }
    private fun cases(key: String) = fixtures.getValue(key).jsonArray.map { it.jsonObject }

    @Test fun growingPaginationSequencesReachEveryPage() = runBlocking {
        for (case in cases("page_sequences")) {
            val before = server.requestCount
            val pages = case.getValue("pages").jsonArray
            pages.forEach { server.enqueue(MockResponse().setBody(it.toString())) }
            val tokens = api.iterateMasterGames(MasterGameFilters("Tal", pageSize = 1)).toList().map { it.token }
            assertEquals(case.getValue("expected_tokens").jsonArray.map { it.jsonPrimitive.content }, tokens)
            assertEquals(pages.size, server.requestCount - before)
        }
    }

    @Test fun missingDataAndMalformedSuccessResponsesFailConsistently() = runBlocking {
        for (case in cases("invalid_pages")) {
            server.enqueue(MockResponse().setBody(case.getValue("body").jsonPrimitive.content))
            val error = assertFailsWith<ApiException> { api.iterateMasterGames(MasterGameFilters("Tal")).toList() }
            assertEquals(case.getValue("code").jsonPrimitive.content, error.code)
        }
    }

    @Test fun httpErrorsRetainStatusAndRetryAfter() = runBlocking {
        for (case in cases("http_errors")) {
            val status = case.getValue("status").jsonPrimitive.int
            val retry = case.getValue("retry_after").jsonPrimitive.contentOrNull
            val response = MockResponse().setResponseCode(status).setBody(case.getValue("body").jsonPrimitive.content)
            if (retry != null) response.setHeader("Retry-After", retry)
            server.enqueue(response)
            val error = assertFailsWith<ApiException> { api.masterGames(MasterGameFilters("Tal")) }
            assertEquals(case.getValue("code").jsonPrimitive.content, error.code)
            assertEquals(status, error.status)
            assertEquals(retry, error.retryAfter)
        }
    }

    @Test fun stoppingOrCancellingPreventsTheNextPage() = runBlocking {
        val stop = fixtures.getValue("stop_after_first").jsonObject
        server.enqueue(MockResponse().setBody(cases("page_sequences")[0].getValue("pages").jsonArray[0].toString()))
        val games = api.iterateMasterGames(MasterGameFilters("Tal")).take(stop.getValue("items").jsonPrimitive.int).toList()
        assertEquals(1, games.size)
        assertEquals(stop.getValue("requests").jsonPrimitive.int, server.requestCount)
        server.takeRequest(2, TimeUnit.SECONDS)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val observed = CompletableDeferred<Throwable>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            try { api.masterGames(MasterGameFilters("Tal")) }
            catch (error: Throwable) { observed.complete(error); throw error }
        }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        assertIs<CancellationException>(observed.await())
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(2, server.requestCount)
    }
}
