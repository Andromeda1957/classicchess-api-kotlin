package com.classicchess.api

import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

data class PageOptions(val page: Int? = null, val pageSize: Int? = null)
data class MasterGameFilters(val query: String, val page: Int? = null, val pageSize: Int? = null)
data class PublicGameFilters(
    val query: String? = null, val archivePlayer: String? = null, val archiveEvent: String? = null,
    val since: Int? = null, val until: Int? = null, val sort: String? = null,
    val page: Int? = null, val pageSize: Int? = null,
)
data class IterationOptions(val limit: Long? = null, val maxPages: Int = 100_000)

private fun pageQuery(options: PageOptions): Map<String, Any?> {
    if (options.page != null && options.page < 1 || options.pageSize != null && options.pageSize !in 1..100) {
        throw ApiException("page must be positive and pageSize must be 1–100.", "invalid_pagination")
    }
    return mapOf("page" to options.page, "page_size" to options.pageSize)
}
private fun gameQuery(filters: PublicGameFilters): Map<String, Any?> {
    if (filters.sort != null && filters.sort !in listOf("asc", "desc", "event")) throw ApiException("sort must be asc, desc or event.", "invalid_query")
    if (filters.sort == "event" && filters.archiveEvent.isNullOrBlank()) throw ApiException("sort=event requires archiveEvent.", "invalid_query")
    return pageQuery(PageOptions(filters.page, filters.pageSize)) + mapOf(
        "q" to filters.query, "archive_player" to filters.archivePlayer, "archive_event" to filters.archiveEvent,
        "since" to filters.since, "until" to filters.until, "sort" to filters.sort,
    )
}

private fun masterQuery(filters: MasterGameFilters): Map<String, Any?> {
    val query = filters.query
    if (query.isBlank() || query.length > 240 || query.codePointCount(0, query.length) > 120) {
        throw ApiException("Use a nonempty MasterDB query of at most 120 characters.", "invalid_query")
    }
    return pageQuery(PageOptions(filters.page, filters.pageSize)) + ("q" to query)
}

private fun masterToken(token: String): String {
    if (!Regex("g[0-9a-z]{1,66}-[0-9a-f]{12}").matches(token)) {
        throw ApiException("Use an exact MasterDB game token returned by search.", "invalid_identifier")
    }
    return token
}

/** Public archive reads for Kotlin JVM and Android. Reuse a client, then close it when its owner ends. */
class ClassicChessClient(options: ClientOptions = ClientOptions()) : Closeable {
    private val transport = Transport(options)
    private val json = Json { ignoreUnknownKeys = true }
    val baseUrl: String get() = transport.base.toString().removeSuffix("/")

    suspend fun discovery(): ApiDiscovery = read("/api/v1/", ApiDiscovery.serializer())
    suspend fun masterGames(filters: MasterGameFilters): MasterGamePage =
        read("/api/v1/games/", MasterGamePage.serializer(), masterQuery(filters))
    suspend fun masterGame(token: String): MasterGameDetail =
        read("/api/v1/games/${masterToken(token)}/", MasterGameDetail.serializer())
    suspend fun masterPgn(token: String): String = pgn("/api/v1/games/${masterToken(token)}/pgn/")
    /** Lower-bound counts do not determine completion: follow next through the server's search cap. */
    fun iterateMasterGames(filters: MasterGameFilters, options: IterationOptions = IterationOptions()): Flow<MasterGame> =
        iterate(transport.url("/api/v1/games/", masterQuery(filters)), options) { url ->
            val page = decode(url, MasterGamePage.serializer())
            Page(page.results, page.next)
        }
    suspend fun publicPlayers(query: String? = null): PublicPlayers =
        read("/api/v1/public/players/", PublicPlayers.serializer(), mapOf("q" to query))
    suspend fun playerNames(query: String? = null): List<String> = publicPlayers(query).results.map { it.name }
    suspend fun publicPlayer(slug: String): PublicPlayerDetail =
        read("/api/v1/public/players/${segment(slug)}/", PublicPlayerDetail.serializer())
    suspend fun publicPlayerBio(slug: String): PublicPlayerBiography =
        read("/api/v1/public/players/${segment(slug)}/bio/", PublicPlayerBiography.serializer())
    suspend fun publicNotableGames(slug: String): PublicNotableGames =
        read("/api/v1/public/players/${segment(slug)}/notable-games/", PublicNotableGames.serializer())
    suspend fun eventSeries(query: String? = null, series: String? = null): PublicEventIndex {
        if (query != null && (query.length > 240 || query.codePointCount(0, query.length) > 120) ||
            !series.isNullOrEmpty() && !Regex("[a-z0-9-]{1,160}").matches(series) || !query.isNullOrEmpty() && !series.isNullOrEmpty()) {
            throw ApiException("Use either an event query of at most 120 characters or an exact series slug.", "invalid_query")
        }
        return read("/api/v1/public/event-series/", PublicEventIndex.serializer(), mapOf("q" to query, "series" to series))
    }
    suspend fun publicEvents(query: String? = null): PublicEvents =
        read("/api/v1/public/events/", PublicEvents.serializer(), mapOf("q" to query))
    suspend fun eventNames(query: String? = null): List<String> = publicEvents(query).results.map { it.name }
    suspend fun annotatedBooks(): AnnotatedBooks = read("/api/v1/annotated/books/", AnnotatedBooks.serializer())
    suspend fun bookTitles(): List<String> = annotatedBooks().results.map { it.label }
    suspend fun publicGames(filters: PublicGameFilters = PublicGameFilters()): PublicGamePage =
        read("/api/v1/public/games/", PublicGamePage.serializer(), gameQuery(filters))
    suspend fun publicGame(token: String): PublicGame =
        read("/api/v1/public/games/${gameToken(token)}/", PublicGame.serializer())
    suspend fun publicPgn(token: String): String = pgn("/api/v1/public/games/${gameToken(token)}/pgn/")

    /** The service caps this export at 300 games. Iterate games and batch tokens to export larger archives. */
    suspend fun exportPublicGames(filters: PublicGameFilters = PublicGameFilters(), tokens: List<String>? = null): String {
        if (tokens != null && tokens.size !in 1..300) throw ApiException("Export between 1 and 300 tokens.", "invalid_export")
        return pgn("/api/v1/public/games/export/", gameQuery(filters.copy(page = null, pageSize = null))
            + mapOf("tokens" to tokens?.joinToString(",") { gameToken(it) }))
    }
    suspend fun annotatedGames(bookSlug: String, page: PageOptions = PageOptions()): AnnotatedGamePage =
        read("/api/v1/annotated/books/${segment(bookSlug)}/games/", AnnotatedGamePage.serializer(), pageQuery(page))
    suspend fun annotatedGame(bookSlug: String, gameSlug: String): AnnotatedGame =
        read("/api/v1/annotated/books/${segment(bookSlug)}/games/${segment(gameSlug)}/", AnnotatedGame.serializer())
    suspend fun annotatedPgn(bookSlug: String, gameSlug: String? = null): String {
        val suffix = if (gameSlug == null) "pgn/" else "games/${segment(gameSlug)}/pgn/"
        return pgn("/api/v1/annotated/books/${segment(bookSlug)}/$suffix")
    }
    fun iteratePublicGames(filters: PublicGameFilters = PublicGameFilters(), options: IterationOptions = IterationOptions()): Flow<PublicGame> =
        iterate(transport.url("/api/v1/public/games/", gameQuery(filters)), options) { url ->
            val page = decode(url, PublicGamePage.serializer())
            Page(page.results, page.next)
        }
    fun iterateAnnotatedGames(bookSlug: String, page: PageOptions = PageOptions(), options: IterationOptions = IterationOptions()): Flow<AnnotatedGame> =
        iterate(transport.url("/api/v1/annotated/books/${segment(bookSlug)}/games/", pageQuery(page)), options) { url ->
            val result = decode(url, AnnotatedGamePage.serializer())
            Page(result.results, result.next)
        }

    private suspend fun <T> read(path: String, serializer: DeserializationStrategy<T>, params: Map<String, Any?> = emptyMap()): T =
        decode(transport.url(path, params), serializer)
    private suspend fun <T> decode(url: HttpUrl, serializer: DeserializationStrategy<T>): T {
        val text = transport.read(url, "application/json")
        return withContext(Dispatchers.Default) {
            try { json.decodeFromString(serializer, text) }
            catch (error: SerializationException) { throw ApiException("Response does not match the API schema.", "invalid_response", cause = error) }
        }
    }
    private suspend fun pgn(path: String, params: Map<String, Any?> = emptyMap()): String =
        transport.read(transport.url(path, params), "application/x-chess-pgn")

    private data class Page<T>(val results: List<T>, val next: String?)
    private fun <T> iterate(first: HttpUrl, options: IterationOptions, fetch: suspend (HttpUrl) -> Page<T>): Flow<T> = flow {
        if (options.maxPages < 1 || options.limit != null && options.limit < 0) throw ApiException("Use positive maxPages and a nonnegative limit.", "invalid_pagination")
        var url = first
        var count = 0L
        val seen = mutableSetOf<HttpUrl>()
        while (options.limit == null || count < options.limit) {
            currentCoroutineContext().ensureActive()
            if (!seen.add(url) || seen.size > options.maxPages) throw ApiException("Pagination repeated a page or exceeded maxPages.", "invalid_pagination")
            val page = fetch(url)
            for (item in page.results) {
                emit(item)
                count++
                if (options.limit != null && count >= options.limit) return@flow
            }
            val next = page.next ?: return@flow
            url = transport.safeUrl(next, url)
            if (url.encodedPath != first.encodedPath) throw ApiException("Pagination changed the collection.", "unsafe_url")
        }
    }
    override fun close() = transport.close()
}
