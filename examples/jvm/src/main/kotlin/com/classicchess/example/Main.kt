package com.classicchess.example

import com.classicchess.api.ClassicChessClient
import com.classicchess.api.ClientOptions
import kotlinx.coroutines.runBlocking

/** Run with players (default), events, books or profile, followed by an optional origin. */
fun main(args: Array<String>) = runBlocking {
    ClassicChessClient(ClientOptions(
        baseUrl = args.getOrNull(1) ?: "https://classicchess.com",
        userAgent = "ClassicChess-KotlinExample/0.1.0",
    )).use { api ->
        when (args.firstOrNull() ?: "players") {
            "players" -> api.playerNames().forEach(::println)
            "events" -> api.eventNames().forEach(::println)
            "books" -> api.bookTitles().forEach(::println)
            "profile" -> {
                val player = api.publicPlayers().results.firstOrNull()
                if (player == null) {
                    println("No curated players")
                    return@use
                }
                println(player.name)
                println(api.publicPlayerBio(player.slug).bio?.lede ?: "Biography unavailable")
                val notable = api.publicNotableGames(player.slug)
                notable.results.forEach { println(it.title) }
                notable.results.firstOrNull()?.let { println(api.publicPgn(it.game.token)) }
            }
            else -> error("Use players, events, books or profile")
        }
    }
}
