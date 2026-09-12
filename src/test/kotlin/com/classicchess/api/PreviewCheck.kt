package com.classicchess.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList

/** Explicit read-only integration journey; the ordinary test suite is offline. */
fun main(args: Array<String>) = runBlocking {
    ClassicChessClient(ClientOptions(baseUrl = args.single(), userAgent = "classicchess-kotlin-local-acceptance/0.1.0")).use { api ->
        val players = api.publicPlayers()
        val events = api.publicEvents()
        val books = api.annotatedBooks()
        check(api.playerNames().size.toLong() == players.count)
        check(api.eventNames().size.toLong() == events.count)
        check(api.bookTitles().size.toLong() == books.count)
        val tal = players.results.single { it.name == "Mikhail Tal" }
        check(api.publicPlayer(tal.slug).player.slug == tal.slug)
        check(!api.publicPlayerBio(tal.slug).bio?.lede.isNullOrEmpty())
        val notables = api.publicNotableGames(tal.slug)
        check(notables.results.isNotEmpty())
        val token = notables.results.first().game.token
        check(api.publicGame(token).token == token)
        check(api.publicPgn(token).contains("[Event "))
        check(api.exportPublicGames(tokens = listOf(token)).contains("[Event "))
        check(api.iteratePublicGames(PublicGameFilters(archivePlayer = tal.slug, pageSize = 2), IterationOptions(limit = 5)).toList().size == 5)
        val book = books.results.first()
        val games = api.iterateAnnotatedGames(book.slug, PageOptions(pageSize = 2), IterationOptions(limit = 5)).toList()
        check(games.size == minOf(5, book.gameCount.toInt()))
        val gameSlug = games.first().token.substringAfter('/')
        check(api.annotatedGame(book.slug, gameSlug).annotation.licenseName.isNotEmpty())
        check(api.annotatedPgn(book.slug, gameSlug).contains("[Event "))
        check(api.annotatedPgn(book.slug).contains("[Event "))
        println("Verified ${players.count} players, ${events.count} events, ${books.count} books; Tal biography/${notables.count} notables, PGNs and multi-page public/annotated lists.")
    }
}
