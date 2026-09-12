package com.classicchess.example

import com.classicchess.api.ApiException
import com.classicchess.api.ClassicChessClient
import com.classicchess.api.ClientOptions
import com.classicchess.api.PublicPlayerBiography
import com.classicchess.api.PublicNotableGames
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Own in a screen/ViewModel; call close() when that owner ends. Pass lifecycleScope/viewModelScope. */
class ArchiveReader(private val scope: CoroutineScope) : Closeable {
    private val api = ClassicChessClient(ClientOptions(userAgent = "ClassicChess-AndroidExample/0.1.0"))
    private var selection: Job? = null

    fun loadPlayers(onNames: (List<String>) -> Unit, onError: (ApiException) -> Unit): Job =
        scope.launch {
            try { onNames(api.playerNames()) }
            catch (error: ApiException) { onError(error) }
        }

    /** Use the exact slug from publicPlayers(); a newer selection cancels the old request. */
    fun selectPlayer(slug: String, onLoaded: (PublicPlayerBiography, PublicNotableGames) -> Unit,
                     onError: (ApiException) -> Unit) {
        selection?.cancel()
        selection = scope.launch {
            try { onLoaded(api.publicPlayerBio(slug), api.publicNotableGames(slug)) }
            catch (error: ApiException) { onError(error) }
        }
    }

    override fun close() {
        selection?.cancel()
        api.close()
    }
}
