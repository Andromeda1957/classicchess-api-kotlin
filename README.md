# Classic Chess Kotlin SDK

Use Kotlin on the JVM or Android to read chess games, player biographies, notable
games, tournaments and annotated books from [Classic Chess](https://classicchess.com/).
The SDK also searches the larger MasterDB game database, retrieves opening
statistics, exports games, and calls account and application APIs.

Public reads need no account or API key. The SDK is MIT licensed; see
[LICENSE](LICENSE). Its Gradle wrapper retains the Apache 2.0 notice in
[THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES/gradle-Apache-2.0.txt).

## Run your first example

Install [JDK 17 or newer](https://adoptium.net/) and [Git](https://git-scm.com/).
Make sure `java -version` works in your terminal, then run:

```sh
git clone https://github.com/Andromeda1957/classicchess-api-kotlin.git
cd classicchess-api-kotlin
./gradlew -q -p examples/jvm run --args="players"
```

On Windows, replace `./gradlew` with `gradlew.bat`. The included Gradle wrapper
downloads the build tool and dependencies automatically. The example builds
this SDK directly from the public checkout and prints the curated player names.
No Maven ZIP, manually copied JAR, private repository or API token is required.

Try `--args="events"`, `--args="books"` or `--args="profile"` to explore other
public data. The runnable source is [Main.kt](examples/jvm/src/main/kotlin/com/classicchess/example/Main.kt).
The first build can take several minutes while Gradle downloads dependencies.

## Add the SDK to your Kotlin project

The SDK has not been published to Maven Central yet. For an existing Kotlin
Gradle project, run this command from the project directory containing
`settings.gradle.kts`:

```sh
git clone https://github.com/Andromeda1957/classicchess-api-kotlin.git vendor/classicchess-api-kotlin
```

Add this block to that project's `settings.gradle.kts`:

```kotlin
includeBuild("vendor/classicchess-api-kotlin") {
    dependencySubstitution {
        substitute(module("com.classicchess:api-client")).using(project(":"))
    }
}
```

Add the dependency to your app module's `build.gradle.kts`, retaining its existing
plugins and repositories. Make sure Maven Central is among its repositories:

```kotlin
repositories { mavenCentral() }
dependencies { implementation("com.classicchess:api-client:0.1.0") }
```

Gradle now builds the dependency from `vendor/classicchess-api-kotlin`. This is
a Gradle composite build: the module name resolves to that source checkout.
The SDK targets JVM 8 bytecode. It is built with Kotlin 2.4.20 for Kotlin 2.1
consumers and uses OkHttp, kotlinx.serialization and kotlinx.coroutines. Its Android example targets
minSdk 23 with JDK 17 and Android Gradle Plugin 9.4.1.

## Read an archive

Here is a complete Kotlin program for a configured JVM application:

```kotlin
import com.classicchess.api.ClassicChessClient
import com.classicchess.api.IterationOptions
import com.classicchess.api.PublicGameFilters
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    ClassicChessClient().use { api ->
        val player = api.publicPlayers().results.firstOrNull()
        if (player == null) {
            println("No curated players available")
            return@use
        }
        println(player.name)
        val bio = api.publicPlayerBio(player.slug).bio
        println(bio?.lede ?: "Biography unavailable")
        api.iteratePublicGames(
            PublicGameFilters(archivePlayer = player.slug),
            IterationOptions(limit = 10),
        ).collect { game -> println("${game.token}: ${game.white} – ${game.black}") }
    }
}
```

`runBlocking` starts a coroutine for this small command-line program. In an
Android app, call the suspend methods from an existing lifecycle-aware coroutine
rather than blocking the UI thread. Reuse a client and close it when finished;
`use` closes it automatically in the example.

Catalogs return complete lists. Game methods return one page; `iterate*` methods
return a coroutine Flow that follows `next` until the archive is exhausted.
Cancelling collection or setting `IterationOptions(limit = ...)` stops early.
Use the exact slugs and tokens returned by the API, not names transformed into slugs.
Missing biographies are `null`, and notable lists can be empty.

## Available operations

| Capability | Kotlin methods |
| --- | --- |
| API discovery | `discovery()` |
| Player, event and book catalogs | `publicPlayers()`, `publicEvents()`, `annotatedBooks(ccnbFormat = 3)`; format is optional |
| Annotated Notebook packages | `annotatedNotebookFile(bookSlug, ccnbFormat = 3)` returns exact bytes |
| Names and titles only | `playerNames()`, `eventNames()`, `bookTitles()` |
| Player cards for lists and pickers | `publicPlayerCards()` returns every player with only the biography headline (epithet, lifespan, crown), about a tenth the size of `publicPlayers()`; `playerNames()` reads it |
| Profiles, biographies and notable games | `publicPlayer(slug)`, `publicPlayerBio(slug)`, `publicNotableGames(slug)` |
| Event series | `eventSeries(query = ...)` or `eventSeries(series = ...)` |
| Public game lists, detail and PGN | `publicGames()`, `publicGame(token)`, `publicPgn(token)` |
| MasterDB game lists, detail and PGN | `masterGames(MasterGameFilters(query))`, `masterGame(token)`, `masterPgn(token)` |
| Annotated game lists, detail and PGN | `annotatedGames(bookSlug)`, `annotatedGame(bookSlug, gameSlug)`, `annotatedPgn(bookSlug, gameSlug)` |
| Streaming games | `iteratePublicGames()`, `iterateMasterGames()`, `iterateAnnotatedGames()` |
| Combine PGN text | `pgnTextForGames(pgnUrls)` combines returned `api_pgn` URLs in order |
| PGN or NDJSON exports | `exportPublicGames()`, `exportMasterGames()`; select `format = "pgn"` or `format = "ndjson"` |
| Player search and statistics | `players(query)`, `masterStats(MasterStatsFilters(...))` |
| Opening explorer | `explorer(ExplorerFilters(...))`, `explorerSources()` |
| Lichess opening explorer | `lichessExplorer(LichessExplorerFilters(fen, player, color, result))` relays through Classic Chess, so no Lichess token is needed; no player covers the whole Lichess database, a Lichess username covers that player's games |
| Event detail and About page | `publicEvent(slug)`, `publicEventAbout(slug)` |
| Photo gallery | `gallery(GalleryFilters(query, page, pageSize))`, `galleryPhoto(photoId)` |
| Beginner games and Game of the Day | `beginnerGames()`, `dailyGame()` |
| Event of the day | `eventOfTheDay()` returns the landmark event on today's homepage, its tagline and pictured winner; `event` is null when none can be published |
| Today's homepage card | `todaysPicks()` returns the Player of the day, Game of the Day, On this day game and Event of the day in one read, with portraits where available; each is null when absent |
| Site search | `siteSearch(query)` for a preview; `siteSearchPage(query, kind, page)` for one paginated kind: `games`, `events` or `players` |
| Endgame tablebase | `tablebase(fen)` uses local Syzygy tables and falls back to the Lichess tablebase for larger positions, as the site does |
| Account collections | `ApplicationClient.accountMe(token)`, `accountCollections(token)`, `accountCreateCollection(name, token)`, `accountImportPublicPlayerGames(archivePlayer, token, ...)` |
| Add single games to collections | `ApplicationClient.accountAddCollectionGame(collectionId, gameSlug, token)` |
| Starred players and games | `accountStarredPlayers(token, page, pageSize)`, `accountStarPlayer(slug, token)`, `accountUnstarPlayer(slug, token)`, `accountStarredGames(token, ...)`, `accountStarGame(slug, token)`, `accountUnstarGame(slug, token)` |
| Change or delete imported games | `accountSetImportedGameVisibility(slug, "public" or "private", token)`, `accountDeleteImportedGame(slug, token)` |
| Public imported games | `publicImportedGame(username, gameSlug)`, `publicImportedPgn(username, gameSlug)` read a game another account imported and made public, by its page address |
| GIF exports | `ApplicationClient.masterGameGif(gameToken, token)`, `publicGameGif(slug, token)`, `annotatedGameGif(bookSlug, gameSlug, token)`, `publicImportedGameGif(username, slug, token)`, `accountImportedGameGif(slug, token)`; pass `orientation = "black"` to flip the board |
| Notifications | `accountNotifications(token, page, pageSize)`, `accountMarkNotificationRead(id, token)`, `accountMarkAllNotificationsRead(token)`, `accountDismissNotification(id, token)`, `accountDismissAllNotifications(token)`, `accountNotificationPreferences(token)`, `accountUpdateNotificationPreferences(token, topics, soundEnabled)` |
| Notebook exports | `accountNotebooks(token)`, `accountNotebook(uuid, token)`, `accountNotebookChapterPgn(uuid, chapterId, token)`, `accountNotebookFile(uuid, token, password, ccnbFormat = 3)` |
| Play the Gym's bots | `accountGym(token)`, `accountGymGames(token, page, pageSize)`, `accountGymNewGame(botKey, token, color, time, minutes, increment)`, `accountGymGame(id, token)`, `accountGymMove(id, uci, ply, token)`, `accountGymBotMove(id, ply, token)`, `accountGymTakeBack(id, token)`, `accountGymResign(id, token)`, `accountGymClock(id, token)`, `accountGymAbort(id, token)`, `accountGymDeleteGame(id, token)`, `accountGymGamePgn(id, token)` |
| Puzzles | `accountPuzzles(token)`, `accountPuzzleNext(token, after)`, `accountPuzzle(id, token)`, `accountPuzzleResult(id, "solved" or "missed", token)`, `accountPuzzleHistory(token, page, pageSize)`, `accountPuzzleReset(token)` |
| Notebook, Remote, Cast and other application APIs | `ApplicationClient.request(...)`, `requestBytes(...)`, `download(...)` for files |
| Scanner upload | `ApplicationClient.scanPosition(jpegBytes, token)` |

Public game filters include `query`, `archivePlayer`, `archiveEvent`, `searchMode`,
`since`, `until`, `sort`, `page` and `pageSize`. `searchMode` chooses which fields
`query` searches: `players` (players, event, site and date), `openings` (opening
name and ECO) or `all` (the default). Each export is capped at 300 games; iterate
larger archives and export batches of up to 300 returned tokens. PGN is the
standard text format for chess games. NDJSON contains one JSON object per line;
`pgnInJson = false` omits PGN text from NDJSON records.

## Account and application requests

For private account or device APIs, use `ApplicationClient` and pass a token
explicitly for each request. Create a personal API token in your signed-in
Classic Chess profile settings with the scopes described in the
[account API reference](https://classicchess.com/api/#account-api).
Set `CLASSICCHESS_API_TOKEN` to that token before running this read-only example
in your configured Kotlin project:

```kotlin
import com.classicchess.api.ApplicationClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val token = requireNotNull(System.getenv("CLASSICCHESS_API_TOKEN")) {
        "Set CLASSICCHESS_API_TOKEN to your personal API token first"
    }
    ApplicationClient().use { api ->
        val response = api.accountMe(token)
        println("${response.status}: ${response.data}")
    }
}
```

Application responses contain `ok`, `status`, JSON `data` and `retryAfter`,
including HTTP errors such as conflicts. `request` accepts JSON text;
`requestBytes` preserves binary notebook uploads and their content type.
`scanPosition` accepts JPEG bytes up to 850,000 bytes. Personal tokens and device
session credentials have different permissions; use the credential required
by the endpoint. Credentials are never stored by the client or sent on public reads.

Notebook downloads accept `ccnbFormat = 2` for legacy exchange or
`ccnbFormat = 3` for the binary record container. Omission retains legacy
behavior. Use the same format for the annotated catalog and package so their
revisions match. The public package helper returns a `ByteArray` from the fixed
same-origin endpoint for a validated catalog slug, sends no account credentials,
and rejects redirects. Unsupported values fail before a request. Capabilities
unavailable in legacy exchange require format 3.

GIF, chapter PGN and account Notebook file methods return an `ApplicationDownload` with
`ok`, `status`, `bytes` (the file when `ok`), `contentType`, `filename`,
`retryAfter` and `error` (the JSON error reply otherwise). GIF exports need a
registered account, so any personal token or device session works, and they
share the site limit on GIF exports: honor `retryAfter` after a 429.

### Play the Gym's bots

Any account can play the Gym's bots; the moves are computed on the server.
Personal tokens need `gym:read` to read games and `gym:play` to start and play
them. Send the `ply` from the latest game state with each move: a stale ply
returns 409 and plays nothing, so a repeated request is safe. The bot engine
plays one move at a time. While it is busy, `accountGymBotMove` returns 429
with `capacity_exhausted`: wait `retryAfter` seconds and ask again with the
same ply. Each account may use 30 minutes of engine time in each hour; once it
is spent the answer is 429 `engine_budget` with `retryAfter` set to the seconds
until that hour ends. An account starts at most 30 games an hour. Decode replies with the
generated models, such as `GymGameState`:

```kotlin
import com.classicchess.api.ApplicationClient
import com.classicchess.api.GymGameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

fun main() = runBlocking {
    val token = requireNotNull(System.getenv("CLASSICCHESS_API_TOKEN")) { "Set a token with gym:read and gym:play" }
    ApplicationClient().use { api ->
        val created = api.accountGymNewGame("morphy", token, color = "white", time = "5+0")
        check(created.ok) { "${created.status}: ${created.data}" }
        var game = json.decodeFromJsonElement(GymGameState.serializer(), created.data)
        game = json.decodeFromJsonElement(GymGameState.serializer(),
            api.accountGymMove(game.id, "e2e4", game.ply.toInt(), token).data)
        var reply = api.accountGymBotMove(game.id, game.ply.toInt(), token)
        while (reply.status == 429 &&
            reply.data["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content == "capacity_exhausted") {
            delay((reply.retryAfter?.toLongOrNull() ?: 1) * 1000)
            reply = api.accountGymBotMove(game.id, game.ply.toInt(), token)
        }
        println("${reply.status}: ${reply.data["sans"]}")
    }
}
```

### Play puzzles

Puzzles come from master games and are for signed-in accounts only. Personal
tokens need `puzzles:read` to read puzzles, figures and history and
`puzzles:write` to save a result or reset; cookie sessions are never enough.
Each account has its own rotation: a puzzle solved on the first try leaves it
until reset, and a missed one stays. A puzzle carries the game so far
(`start_fen` and `lead`), the position (`fen`) and `solution.moves`: the
accepted solver moves at each point are the entries whose `after` equals the
moves played so far. Save each play once, `missed` at the first wrong move or
when the solution is shown and `solved` for a clean finish. A 404
`rotation_empty` reply means every puzzle is solved; reset to play them again.
Decode replies with the generated models, such as `Puzzle`. This plays one
puzzle in the terminal and saves how it went:

```kotlin
import com.classicchess.api.ApplicationClient
import com.classicchess.api.Puzzle
import com.classicchess.api.PuzzleResultSaved
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun main() = runBlocking {
    val token = requireNotNull(System.getenv("CLASSICCHESS_API_TOKEN")) { "Set a token with puzzles:read and puzzles:write" }
    ApplicationClient().use { api ->
        val next = api.accountPuzzleNext(token)
        check(next.ok) { "${next.status}: ${next.data}" }
        val puzzle = json.decodeFromJsonElement(Puzzle.serializer(), next.data)
        println("${puzzle.prompt} ${puzzle.fen}")
        val played = mutableListOf<String>()
        var result = "solved"
        while (true) {
            print("Your move in UCI, such as e2e4: ")
            val answer = readln().trim()
            val step = puzzle.solution.moves.firstOrNull { it.move == answer && it.after == played }
            if (step == null) {
                result = "missed"
                break
            }
            played += answer
            val reply = step.reply ?: break
            println("Reply: $reply")
            played += reply
        }
        val saved = json.decodeFromJsonElement(PuzzleResultSaved.serializer(),
            api.accountPuzzleResult(puzzle.id, result, token).data)
        println("${saved.result.value}: ${saved.summary}")
    }
}
```

## Errors and cancellation

Public-client HTTP failures and transport failures throw `ApiException` with
`code`, `status` and `retryAfter` when available. Coroutine cancellation cancels
the active HTTP call. The default timeout is 30 seconds and the response bound
is 32 MiB; configure them with `ClientOptions`. Pagination rejects repeated
pages, changed origins or collections, and more than 100,000 pages.

The application transport follows no redirects and sends no cookies. Bearer
requests require a private API path over HTTPS or loopback development.
Neither client retries mutations automatically. A GET whose pooled keep-alive
connection was already closed by the server is replayed once on a fresh
connection; POST, PUT, PATCH and DELETE never are. Honor `Retry-After` when
handling rate limits. Response models accept new fields, and an enum value added by
the server decodes as that enum's `UNKNOWN_DEFAULT_OPEN_API` entry. Statistics and
explorer helpers, the event crosstable and search result lists return JSON to
preserve the API's complete data.

## Connections and caching

Every client in a process shares one connection pool, so a public client and
an application client reuse the same TLS connection. Closing a client cancels
only its own requests. To keep public responses between runs, give the
clients a cache directory:

```kotlin
val options = ClientOptions(httpCacheDirectory = File(cacheDir, "classicchess-http"))
ClassicChessClient(options).use { api -> println(api.discovery().version) }
```

A fresh response (`Cache-Control: max-age`) is answered from disk, and a
stale one is revalidated with `If-None-Match`, so an unchanged catalog costs
a `304 Not Modified` instead of its full body. Only requests without a bearer
token read or write the cache; account responses never reach the disk.
`httpCacheMaxBytes` bounds it (16 MiB by default), and clients given the
same directory share one cache.

## Build and test this repository

From the SDK checkout above, with JDK 17 or newer, run the model contract check
and the tests:

```sh
./gradlew check
```

The response models in `src/generated/kotlin` are generated from
`contracts/openapi.yaml` by the [OpenAPI Generator](https://openapi-generator.tech/)
Gradle plugin and committed. After a contract change, regenerate them and test:

```sh
./gradlew generateModels test
```

`./gradlew check` runs `checkContract`, which fails if the committed models drift
from the contract.

`./gradlew packageClient` is an optional contributor command that creates a local
Maven repository ZIP in `artifacts/`; normal source installation does not need it.
The other maintained SDKs have the same API capabilities:
[Python](https://github.com/Andromeda1957/classicchess-api-python) and
[TypeScript](https://github.com/Andromeda1957/classicchess-api-typescript).
See the [API reference](https://classicchess.com/api/) for endpoint permissions,
response fields, rate limits and resource attribution. The SDK license covers
its software; chess resources retain their own stated reuse terms.
Issues and pull requests are welcome; see [CONTRIBUTING.md](https://github.com/Andromeda1957/classicchess-api-kotlin/blob/main/CONTRIBUTING.md).
