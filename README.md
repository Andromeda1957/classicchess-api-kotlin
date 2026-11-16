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
| Annotated Notebook packages | `annotatedNotebookFile(bookSlug, ccnbFormat = 3)` returns exact bytes; `annotatedBookCcnb(bookSlug, ccnbFormat = 3)` downloads the book's `.ccnb` package (`urls.ccnb`) with its notes, mapped source pages and rights metadata |
| Names and titles only | `playerNames()`, `eventNames()`, `bookTitles()` |
| Player cards for lists and pickers | `publicPlayerCards()` returns every player with only the biography headline (epithet, lifespan, crown), about a tenth the size of `publicPlayers()`; `playerNames()` reads it |
| Profiles, biographies and notable games | `publicPlayer(slug)`, `publicPlayerBio(slug)`, `publicNotableGames(slug)` |
| Event series | `eventSeries(query = ...)` or `eventSeries(series = ...)` |
| Public game lists, detail and PGN | `publicGames()`, `publicGame(token)`, `publicPgn(token)` |
| MasterDB game lists, detail and PGN | `masterGames(MasterGameFilters(query))`, `masterGame(token)`, `masterPgn(token)` |
| Annotated game lists, detail and PGN | `annotatedGames(bookSlug, page, AnnotatedGameFilters(query, headerQuery, author, sort))`, `annotatedGame(bookSlug, gameSlug)`, `annotatedPgn(bookSlug, gameSlug)` |
| Streaming games | `iteratePublicGames()`, `iterateMasterGames()`, `iterateAnnotatedGames()` |
| Combine PGN text | `pgnTextForGames(pgnUrls)` combines returned `api_pgn` URLs in order |
| PGN or NDJSON exports | `exportPublicGames()`, `exportMasterGames()`; select `format = "pgn"` or `format = "ndjson"`. `exportPublicGamesByToken(tokens)` and `exportMasterGamesByToken(tokens)` send 1–300 returned tokens in the request body instead of the URL |
| Player search and statistics | `players(query)`, `masterStats(MasterStatsFilters(...))` |
| Opening explorer | `explorer(ExplorerFilters(..., since, until))`, `explorerSources()` |
| A MasterDB player's games at a position | `playerExplorer(PlayerExplorerFilters(player, fen, color, result, page, since, until))`; while the player's index is first built the reply is `pending` with `retry_after_ms` |
| Lichess opening explorer | `lichessExplorer(LichessExplorerFilters(fen, player, color, result, since, until))` relays through Classic Chess, so no Lichess token is needed; no player covers the whole Lichess database, a Lichess username covers that player's games. `lichessExplorerGame(gameId)` returns one listed game's PGN |
| Load a game from a link | `resolveGameLink(url)` returns the PGN and board orientation of a public Lichess or Chess.com game, as the Analysis Board loads it |
| Event detail and About page | `publicEvent(slug)`, `publicEventAbout(slug)` |
| Photo gallery | `gallery(GalleryFilters(query, page, pageSize))`, `galleryPhoto(photoId)` |
| Beginner games and Game of the Day | `beginnerGames()`, `dailyGame()` |
| Event of the day | `eventOfTheDay()` returns the landmark event on today's homepage, its tagline and pictured winner; `event` is null when none can be published |
| Today's homepage card | `todaysPicks()` returns the Player of the day, Game of the Day, On this day game and Event of the day in one read, with portraits where available; each is null when absent |
| Chess history and study themes | `chessToday()` and `chessTodayOnDate(month, day)` return a day's sourced anniversaries; `studyThemes()` lists the themes `PublicGameFilters(theme = ...)` accepts |
| What's New | `siteUpdates(PageOptions(page, pageSize))`, `siteUpdate(id)`, `iterateSiteUpdates()` |
| Site search | `siteSearch(query)` for a preview; `siteSearchPage(query, kind, page)` for one paginated kind: `games`, `events` or `players`; `siteContentSearch(query, page)` searches the site's pages, biographies, event histories and What's New posts |
| Shared Gym games | `sharedGymGame(token)` reads a Gym game by the token in its share link |
| Endgame tablebase | `tablebase(fen)` uses local Syzygy tables and falls back to the Lichess tablebase for larger positions, as the site does |
| Account collections | `ApplicationClient.accountMe(token)`, `accountCollections(token)`, `accountCreateCollection(name, token)`, `accountImportPublicPlayerGames(archivePlayer, token, ...)` |
| Add single games to collections | `ApplicationClient.accountAddCollectionGame(collectionId, gameSlug, token)` |
| Collection games, notes and order | `accountCollectionGames(id, token, page, pageSize, sort, query)`, `accountUpdateCollection(id, token, name, sort, description, isPlan)`, `accountUpdateCollectionItem(id, itemId, token, note, studied)`, `accountRemoveCollectionItem(id, itemId, token)`, `accountRemoveCollectionGame(id, gameSlug, token)`, `accountReorderCollection(id, itemIds, token)` |
| Study plans | `accountCreatePlan(name, games, idempotencyKey, token, description)`, `accountPlanProgress(id, token, page, pageSize)` |
| Starred players and games | `accountStarredPlayers(token, page, pageSize)`, `accountStarPlayer(slug, token)`, `accountUnstarPlayer(slug, token)`, `accountTogglePlayerBookmark(slug, token)`, `accountStarredGames(token, ...)`, `accountStarGame(slug, token)`, `accountUnstarGame(slug, token)`, `accountGameLibrary(gameSlug, token)` shows the star and the collections holding a game |
| Imported games | `accountImportedGames(token, page, pageSize, query, source, sort)`, `accountImportGame(source, token, pgn, remoteUrl, visibility)`, `accountImportedGame(slug, token)`, `accountSetImportedGameVisibility(slug, "public" or "private", token)`, `accountDeleteImportedGame(slug, token)` |
| Website preferences | `accountPreferences(token)`, `accountUpdatePreferences(PreferenceChanges(...), token)` |
| Public imported games | `publicImportedGame(username, gameSlug)`, `publicImportedPgn(username, gameSlug)` read a game another account imported and made public, by its page address |
| GIF exports | `ApplicationClient.masterGameGif(gameToken, token)`, `publicGameGif(slug, token)`, `annotatedGameGif(bookSlug, gameSlug, token)`, `publicImportedGameGif(username, slug, token)`, `accountImportedGameGif(slug, token)`; pass `orientation = "black"` to flip the board |
| Notifications | `accountNotifications(token, page, pageSize)`, `accountMarkNotificationRead(id, token)`, `accountMarkAllNotificationsRead(token)`, `accountDismissNotification(id, token)`, `accountDismissAllNotifications(token)`, `accountNotificationPreferences(token)`, `accountUpdateNotificationPreferences(token, topics, soundEnabled)` |
| Notebook exports | `accountNotebooks(token)`, `accountNotebook(uuid, token)`, `accountNotebookChapterPgn(uuid, chapterId, token)`, `accountNotebookFile(uuid, token, password, ccnbFormat = 3)` |
| Notebooks | `accountCreateNotebook(title, token, ...)`, `accountRenameNotebook(uuid, title, token)`, `accountDeleteNotebook(uuid, token)`, `accountNotebookSettings(uuid, token)`, `accountUpdateNotebookSettings(uuid, token, ...)`, `accountLeaveNotebook(uuid, token)` |
| Notebook collaborators and share links | `accountSetNotebookCollaborator(uuid, username, role, token)`, `accountRemoveNotebookCollaborator(uuid, id, token)`, `accountNotebookShare`, `accountCreateNotebookShare` and `accountRevokeNotebookShare(uuid, token)`, and the chapter forms `accountNotebookChapterShare`, `accountCreateNotebookChapterShare` and `accountRevokeNotebookChapterShare(uuid, chapterId, token)` |
| Notebook folders and chapters | `accountCreateNotebookSection(uuid, title, token, parentId)`, `accountRenameNotebookSection`, `accountDeleteNotebookSection`, `accountMoveNotebookSection(uuid, sectionId, token, parentId, beforeSectionId)`, `accountCreateNotebookChapter(uuid, token, ...)`, `accountNotebookChapter(uuid, chapterId, token)`, `accountSaveNotebookChapter(uuid, chapterId, pgn, contentVersion, token)`, `accountRenameNotebookChapter`, `accountDeleteNotebookChapter`, `accountMoveNotebookChapter(uuid, chapterId, token, sectionId, beforeChapterId)` |
| Play the Gym's bots | `accountGym(token)`, `accountGymGames(token, page, pageSize)`, `accountGymNewGame(botKey, token, color, time, minutes, increment, version)`, `accountGymGame(id, token)`, `accountGymMove(id, uci, ply, token)`, `accountGymBotMove(id, ply, token)`, `accountGymTakeBack(id, token)`, `accountGymResign(id, token)`, `accountGymClock(id, token)`, `accountGymAbort(id, token)`, `accountGymDeleteGame(id, token)`, `accountGymGamePgn(id, token)` |
| Puzzles | `accountPuzzles(token)`, `accountPuzzleNext(token, after)`, `accountPuzzle(id, token)`, `accountPuzzleResult(id, "solved" or "missed", token)`, `accountPuzzleHistory(token, page, pageSize)`, `accountPuzzleReset(token)` |
| Android app sessions | `mobileHome()`, `mobileAndroidRelease()`, `mobileSignIn(username, password, twoFactorCode, deviceName)`, `mobileSignOut(token)`, `mobileSessions(token)`, `mobileRevokeSession(id, token)`, `mobileRevokeAllSessions(token)`, `mobileSettings(token)`, `mobileUpdateSettings(MobileSettingsChanges(...), token)` |
| Notebook, Remote, Cast and other application APIs | `ApplicationClient.request(...)`, `requestBytes(...)`, `download(...)` for files |
| Scanner upload | `ApplicationClient.scanPosition(jpegBytes, token)` |

Public game filters include `query`, `archivePlayer`, `archiveEvent`, `searchMode`,
`since`, `until`, `sort`, `page` and `pageSize`, plus `theme` (comma-separated
slugs from `studyThemes()`, all of which must match), `eco` (a code or prefix such
as `D30` or `D`), `result` (`1-0`, `0-1`, `1/2-1/2` or `*`) and the published
annotation filters `annotationQ`, `annotationAuthor` and `annotationBook`.
`searchMode` chooses which fields `query` searches: `players` (players, event,
site and date), `openings` (opening name and ECO) or `all` (the default). Each
export is capped at 300 games; iterate larger archives and export batches of up
to 300 returned tokens. PGN is the standard text format for chess games. NDJSON
contains one JSON object per line; `pgnInJson = false` omits PGN text from NDJSON
records.

`AnnotatedGameFilters` searches one annotated book: `query` searches its published
notes, `headerQuery` the game headers as the book page's search box does, and
`author` the source label; `sort = "desc"` lists the latest games first.

This prints today's chess anniversaries and the three newest What's New posts:

```kotlin
import com.classicchess.api.ClassicChessClient
import com.classicchess.api.IterationOptions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    ClassicChessClient().use { api ->
        api.chessToday().entries.forEach { println("${it.year}: ${it.text}") }
        api.iterateSiteUpdates(options = IterationOptions(limit = 3)).collect { println("${it.date} ${it.title}") }
    }
}
```

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
including HTTP errors such as conflicts; `errorCode` is the API error code of a
failed reply, such as `chapter_conflict`. `request` accepts JSON text;
`requestBytes` preserves binary notebook uploads and their content type.
`scanPosition` accepts JPEG bytes up to 850,000 bytes. Personal tokens and device
session credentials have different permissions; use the credential required
by the endpoint. Credentials are never stored by the client or sent on public reads.

Notebook downloads accept `ccnbFormat = 2` for legacy exchange or
`ccnbFormat = 3` for the binary record container. Omission retains legacy
behavior. Use the same format for the annotated catalog and package so their
revisions match. The public package helpers `annotatedNotebookFile` and
`annotatedBookCcnb` return a `ByteArray` from a fixed same-origin endpoint for a
validated catalog slug, send no account credentials, and reject redirects. Unsupported values fail before a request. Capabilities
unavailable in legacy exchange require format 3.

GIF, chapter PGN and account Notebook file methods return an `ApplicationDownload` with
`ok`, `status`, `bytes` (the file when `ok`), `contentType`, `filename`,
`retryAfter` and `error` (the JSON error reply otherwise). GIF exports need a
registered account, so any personal token or device session works, and they
share the site limit on GIF exports: honor `retryAfter` after a 429.

### Website preferences

Personal tokens need `preferences:read` to read and `preferences:write` to change
the website's board, engine, viewer, sound and speech preferences; device
sessions pass both. `accountPreferences` also returns the choices each setting
accepts. `accountUpdatePreferences` sends only the fields a `PreferenceChanges`
sets, and changes nothing when the server refuses any value:

```kotlin
val reply = api.accountUpdatePreferences(PreferenceChanges(boardTheme = "green", engineLines = 3), token)
println(if (reply.ok) "Saved" else "${reply.status} ${reply.errorCode}")
```

The Android app keeps its own settings for a device session: read and change
them with `mobileSettings(token)` and `mobileUpdateSettings(MobileSettingsChanges(...), token)`.
`mobileSignIn` returns that device session's `token`; a 401 `two_factor_required`
asks for `twoFactorCode`. Like bearer tokens, the password is sent only over HTTPS
or loopback development. The session and app-settings calls accept only a device
session token, never a personal API token.

### Edit Notebooks

The Notebook calls edit as the website's Notebook pages do, within the account's
role in each Notebook. Personal tokens need `notebooks:read` to read a chapter,
`notebooks:write` to create or rename a Notebook and to create, rename, save, move
and delete its folders and chapters, and `notebooks:manage` for settings,
collaborators, share links, leaving and deleting a Notebook; device sessions pass
all three. A chapter save replaces its
PGN only while the chapter is still at the `content_version` you read. Otherwise
it returns 409 `chapter_conflict` and changes nothing; the reply does not carry
the newer version, so read the chapter again, reapply your edit and save:

```kotlin
val chapter = api.accountNotebookChapter(uuid, chapterId, token).data.getValue("chapter").jsonObject
val version = chapter.getValue("content_version").jsonPrimitive.long
val saved = api.accountSaveNotebookChapter(uuid, chapterId, "1. e4 c5 *", version, token)
if (saved.errorCode == "chapter_conflict") println("Changed elsewhere; read it again and reapply the edit")
```

Never retry a save automatically: repeating a save that succeeded also conflicts.
Share links open a read-only view without signing in, so keep their URLs private.

### Play the Gym's bots

Any account can play the Gym's bots; the moves are computed on the server.
Personal tokens need `gym:read` to read games and `gym:play` to start and play
them. Send the `ply` from the latest game state with each move: a stale ply
returns 409 and plays nothing, so a repeated request is safe. The historical bot engine
plays one move at a time. While it is busy, `accountGymBotMove` returns 429
with `capacity_exhausted`: wait `retryAfter` seconds and ask again with the
same ply. Each account may use 30 minutes of engine time in each hour; once it
is spent the answer is 429 `engine_budget` with `retryAfter` set to the seconds
until that hour ends. An account starts at most 30 games an hour. A bot can offer
several versions: pass `version` with a key from the bot's `versions` in
`accountGym`, such as `"v4"`, or omit it for the bot's `default_version`; the game
keeps its version to the end. Anyone with a game's share link can read it with
`ClassicChessClient.sharedGymGame(token)`. Decode replies with the generated
models, such as `GymGameState`.

When the server offers an approved Strength bot, its move request can return
202 with the complete game and `thinking: true`. Read `accountGymGame(id, token)`
until `thinking` is false. A non-null `thinking_error` reports a failed job;
another explicit bot-move request retries it. Repeated move requests at the same
ply coalesce. The SDK preserves the status and does not poll or retry automatically.
Strength metadata uses `engine: "strength"`, nullable rating fields and null
move-match/training figures; historical bots use `engine: "existing"` and retain
their figures. Strength is optional and is not enabled by installing this SDK.

This example plays one move against the historical Romantic Era Bot:

```kotlin
import com.classicchess.api.ApplicationClient
import com.classicchess.api.GymGameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

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
        while (reply.status == 429 && reply.errorCode == "capacity_exhausted") {
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

## Collections and curated bulk additions

Use `library:read` for lookup and `library:write` for create,
delete and bulk additions. `accountCreateCollection(name, token)` creates an
empty collection. `accountFindCollection(name, token)` returns `count`/`results`
for the exact name ignoring case (empty when absent). Use the returned ID.
`accountDeleteCollection(id, token)` deletes the collection, memberships, notes
and progress, keeping underlying games.

`accountCollectionGames(id, token, query = ...)` pages through a collection's
games; `query` matches players, event, date, opening or ECO. Set a game's note
or studied mark with `accountUpdateCollectionItem`, and reorder with
`accountReorderCollection(id, itemIds, token)`, listing every item ID once.
`accountCreatePlan` creates a study plan of 1 to 100 games in playback order, all
at once or not at all. Retrying with the same idempotency key and input returns
the existing plan, so a lost reply is safe to repeat:

```kotlin
val plan = api.accountCreatePlan("Tal's attacks", listOf(PlanGame(gameSlug, "Watch the knight sacrifice")),
    idempotencyKey = "tal-attacks-1", token = token)
check(plan.ok) { "${plan.status} ${plan.errorCode}" }
val planId = plan.data.getValue("collection").jsonObject.getValue("id").jsonPrimitive.long
val progress = api.accountPlanProgress(planId, token).data
println("${progress["studied"]} of ${progress["count"]} studied")
```

`accountAddCuratedGames(collectionId, filters, token, ...)` processes one batch
from curated games, never MasterDB or user imports. `CuratedGameFilter` accepts
`players`, `events`, `since`, `until`, `notable`, `q`, `theme`, `eco`, `result`,
`annotationQ`, `annotationAuthor`, and `annotationBook`. Criteria are AND inside
a group; groups are OR; `exclude` subtracts groups. Player/event slug lists are
OR; players match either participant, including event games. Resolve slugs with
`ClassicChessClient.publicPlayers(...)` and `publicEvents(...)`. Years include
unknown months/days. `listOf(CuratedGameFilter(notable = true))` selects all
players' curated notable games.

For every curated game played in 1920, this complete JVM example reads its token
from the private environment. Run it in the example JVM project after replacing
its `Main.kt` with the following (keep the project's package declaration):

```kotlin
import com.classicchess.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val token = requireNotNull(System.getenv("CLASSICCHESS_API_TOKEN"))
    ApplicationClient().use { api ->
        val created = api.accountCreateCollection("Curated games from 1920", token)
        check(created.ok) { created.data.toString() }
        val id = created.data.getValue("id").jsonPrimitive.long
        val filters = listOf(CuratedGameFilter(since = 1920, until = 1920))
        val preview = api.accountAddCuratedGames(id, filters, token, dryRun = true)
        check(preview.ok) { preview.data.toString() }
        println("Matched: ${preview.data["matched"]}")
        var after = 0L
        while (true) {
            val reply = api.accountAddCuratedGames(id, filters, token, afterGameId = after)
            check(reply.ok) { reply.data.toString() }
            println("Added this batch: ${reply.data["added"]}")
            if (!reply.data.getValue("has_more").jsonPrimitive.boolean) break
            after = reply.data.getValue("next_after_game_id").jsonPrimitive.long
        }
    }
}
```

`batchSize` is 1–1000, default 1000; `afterGameId` starts at 0. Keep filters and
exclusions fixed. `matched` is the full live selection; `added`,
`already_present` and `processed` are per batch. Dry runs write nothing; begin
actual additions at 0. Retries preserve existing entries, notes and progress.
There is no frozen catalog snapshot or automatic write retry. Stop on errors
and retain the last successful cursor for resuming. HTTP errors retain their
status/data; coroutine cancellation propagates normally.

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

`gradle.lockfile` records resolved library/test dependencies, and
`buildscript-gradle.lockfile` records plugin/build dependencies. Keep both in
source control so builds and vulnerability inventories use the same versions.
After changing dependency declarations, run
`./gradlew resolveDependencyInventory --write-locks` and commit both the
declarations and resulting lock changes. `./gradlew resolveDependencyInventory`
checks every resolvable dependency graph without compiling or packaging; it is
also part of `check`. Strict locking rejects missing configuration state as
well as changes to a recorded graph.

`./gradlew packageClient` is an optional contributor command that creates a local
Maven repository ZIP in `artifacts/`; normal source installation does not need it.
The other maintained SDKs have the same API capabilities:
[Python](https://github.com/Andromeda1957/classicchess-api-python) and
[TypeScript](https://github.com/Andromeda1957/classicchess-api-typescript).
See the [API reference](https://classicchess.com/api/) for endpoint permissions,
response fields, rate limits and resource attribution. The SDK license covers
its software; chess resources retain their own stated reuse terms.
Issues and pull requests are welcome; see [CONTRIBUTING.md](https://github.com/Andromeda1957/classicchess-api-kotlin/blob/main/CONTRIBUTING.md).

## Notebook annotation search and offline puzzle attempts

These helpers require the matching server release. Use
`accountNotebookAnnotationSearch(token, q = "king", kind = "comments", notebookUuid = uuid, limit = 50, cursor = cursor)`
for annotations in owned and directly invited Notebooks. Hits include authorized PGN URLs,
child-index paths and content versions. Follow non-null cursors even after empty pages;
`complete = false` can report a partial scan.

Use `accountPuzzleNext(token, result = "missed")` and
`accountPuzzleHistory(token, result = "missed")` for current incorrect attempts.
`accountPuzzleBatch(token, limit = 25)` downloads 1–100 playable puzzles. Persist the account ID,
reset generation and puzzle revisions in account-separated private storage. Persist each
attempt before `accountPuzzleResult(id, result, token, eventId, generation, revision)`.
Retries keep the same UUID and original reset generation. Saved/duplicate responses acknowledge
delivery; 409 stale_generation/stale_puzzle/event_conflict and 404 are terminal. Your app
owns durable storage, network retry/backoff and sign-out cleanup.
