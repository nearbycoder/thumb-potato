package com.nearbycoder.thumbpotato.domain

import com.nearbycoder.thumbpotato.data.local.AppDatabase
import com.nearbycoder.thumbpotato.data.local.PlaySession
import com.nearbycoder.thumbpotato.data.local.SavedGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Reads and writes the backlog and planner. */
class Library(db: AppDatabase, scope: CoroutineScope) {
    private val games = db.savedGames()
    private val sessions = db.playSessions()

    val savedGames: StateFlow<List<SavedGame>> = games.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val playSessions: StateFlow<List<PlaySession>> = sessions.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observe(key: String): Flow<SavedGame?> = games.observe(key)

    suspend fun saved(key: String): SavedGame? = games.get(key)

    suspend fun save(game: Game, status: BacklogStatus = BacklogStatus.QUEUED, mine: Set<Platform> = emptySet()): SavedGame {
        val existing = games.get(game.ref.key)
        val saved = existing?.updated(game)?.withStatus(status) ?: SavedGame.from(game, status, mine)
        games.upsert(saved)
        return saved
    }

    suspend fun setStatus(key: String, status: BacklogStatus) {
        val existing = games.get(key) ?: return
        games.upsert(existing.withStatus(status))
    }

    /** Applies [change] to a saved game and stamps `updatedAt`. */
    suspend fun edit(key: String, change: (SavedGame) -> SavedGame) {
        val existing = games.get(key) ?: return
        games.upsert(change(existing).copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun refresh(game: Game) {
        val existing = games.get(game.ref.key) ?: return
        val updated = existing.updated(game)
        if (updated != existing) games.upsert(updated)
    }

    suspend fun remove(key: String) = games.delete(key)

    // Planner

    suspend fun addSession(session: PlaySession) = sessions.upsert(session)
    suspend fun updateSession(session: PlaySession) = sessions.upsert(session)
    suspend fun deleteSession(session: PlaySession) = sessions.delete(session)

    // Backup

    suspend fun allGames(): List<SavedGame> = games.all()
    suspend fun allSessions(): List<PlaySession> = sessions.all()

    /** Merges imported games: a game already here is only replaced by a newer copy. */
    suspend fun importGames(list: List<SavedGame>): Int {
        val newer = mergeNewer(list, games.all().associateBy { it.key })
        games.upsertAll(newer)
        return newer.size
    }

    /** Merges imported sessions by id: new ones are added, existing ones are kept. */
    suspend fun importSessions(list: List<PlaySession>): Int {
        val fresh = list.filter { sessions.get(it.id) == null }
        sessions.upsertAll(fresh)
        return fresh.size
    }

    companion object {
        /** The incoming games that are new here or newer (by `updatedAt`) than the local copy. */
        fun mergeNewer(incoming: List<SavedGame>, existing: Map<String, SavedGame>): List<SavedGame> =
            incoming.groupBy { it.key }.values.map { copies -> copies.maxBy { it.updatedAt } }
                .filter { (existing[it.key]?.updatedAt ?: Long.MIN_VALUE) < it.updatedAt }
    }
}

/** Backlog stats card: beaten this year, hours logged, backlog size. */
data class BacklogStats(val beatenThisYear: Int, val hoursLogged: Double, val backlogSize: Int) {
    companion object {
        fun of(games: List<SavedGame>, year: Int = java.time.LocalDate.now().year): BacklogStats = BacklogStats(
            beatenThisYear = games.count { g ->
                g.backlogStatus == BacklogStatus.BEATEN &&
                    (g.finishedAt ?: g.updatedAt).let { com.nearbycoder.thumbpotato.data.remote.DateParsing.localDate(it).year == year }
            },
            hoursLogged = games.sumOf { it.hoursPlayed },
            backlogSize = games.count { it.backlogStatus == BacklogStatus.QUEUED || it.backlogStatus == BacklogStatus.PLAYING },
        )
    }
}
