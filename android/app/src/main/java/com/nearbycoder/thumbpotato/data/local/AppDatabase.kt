package com.nearbycoder.thumbpotato.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<SavedGame>>

    @Query("SELECT * FROM saved_games WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<SavedGame?>

    @Query("SELECT * FROM saved_games WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SavedGame?

    @Query("SELECT * FROM saved_games")
    suspend fun all(): List<SavedGame>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(games: List<SavedGame>)

    @Query("DELETE FROM saved_games WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface PlaySessionDao {
    @Query("SELECT * FROM play_sessions ORDER BY starts_at ASC")
    fun observeAll(): Flow<List<PlaySession>>

    @Query("SELECT * FROM play_sessions")
    suspend fun all(): List<PlaySession>

    @Query("SELECT * FROM play_sessions WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PlaySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PlaySession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<PlaySession>)

    @Delete
    suspend fun delete(session: PlaySession)
}

@Database(entities = [SavedGame::class, PlaySession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedGames(): SavedGameDao
    abstract fun playSessions(): PlaySessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "thumb-potato.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
