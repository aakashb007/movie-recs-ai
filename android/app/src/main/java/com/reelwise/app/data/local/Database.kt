package com.reelwise.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Signal
import kotlinx.coroutines.flow.Flow

/** The person's taste lives only on the device and is sent with each request; there is no account. */
@Entity(tableName = "interactions", primaryKeys = ["id", "mediaType"])
data class InteractionEntity(
    val id: Int,
    val mediaType: MediaType,
    val signal: Signal,
    val title: String,
    val posterPath: String?,
    val updatedAt: Long,
)

@Entity(tableName = "watchlist", primaryKeys = ["id", "mediaType"])
data class WatchlistEntity(
    val id: Int,
    val mediaType: MediaType,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val voteAverage: Double,
    val addedAt: Long,
)

/** Last recommendation response per feed filter, so the app opens instantly and works offline. */
@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    @PrimaryKey val filter: String,
    val json: String,
    val fetchedAt: Long,
)

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interactions ORDER BY updatedAt ASC")
    fun observeAll(): Flow<List<InteractionEntity>>

    @Query("SELECT * FROM interactions ORDER BY updatedAt ASC")
    suspend fun getAll(): List<InteractionEntity>

    @Upsert
    suspend fun upsert(entity: InteractionEntity)

    @Query("DELETE FROM interactions WHERE id = :id AND mediaType = :mediaType")
    suspend fun delete(id: Int, mediaType: MediaType)

    @Query("DELETE FROM interactions")
    suspend fun clear()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist ORDER BY addedAt ASC")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id AND mediaType = :mediaType)")
    fun observeContains(id: Int, mediaType: MediaType): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id AND mediaType = :mediaType)")
    suspend fun contains(id: Int, mediaType: MediaType): Boolean

    @Upsert
    suspend fun upsert(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id AND mediaType = :mediaType")
    suspend fun delete(id: Int, mediaType: MediaType)

    @Query("DELETE FROM watchlist")
    suspend fun clear()
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_cache WHERE filter = :filter")
    fun observe(filter: String): Flow<FeedCacheEntity?>

    @Query("SELECT * FROM feed_cache WHERE filter = :filter")
    suspend fun get(filter: String): FeedCacheEntity?

    @Upsert
    suspend fun upsert(entity: FeedCacheEntity)

    @Query("DELETE FROM feed_cache")
    suspend fun clear()
}

/** Genres, language and year of a title the person reacted to, so the taste model needs no network. */
@Entity(tableName = "title_features", primaryKeys = ["id", "mediaType"])
data class TitleFeaturesEntity(
    val id: Int,
    val mediaType: MediaType,
    /** TMDB genre names joined with '|'. */
    val genres: String,
    val language: String?,
    val year: Int?,
)

/** Quiet signals: a title's details were opened, or it was swiped past in For you. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val id: Int,
    val mediaType: MediaType,
    val kind: String,
    val at: Long,
) {
    companion object {
        const val OPENED = "opened"
        const val SKIPPED = "skipped"
    }
}

@Dao
interface LearningDao {
    @Upsert
    suspend fun upsertFeatures(entity: TitleFeaturesEntity)

    @Query("SELECT * FROM title_features")
    suspend fun allFeatures(): List<TitleFeaturesEntity>

    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY at ASC")
    suspend fun allEvents(): List<EventEntity>

    /** Keeps the newest [keep] events. */
    @Query("DELETE FROM events WHERE eventId NOT IN (SELECT eventId FROM events ORDER BY at DESC LIMIT :keep)")
    suspend fun trimEvents(keep: Int)

    @Query("DELETE FROM events")
    suspend fun clearEvents()
}

@Database(
    entities = [InteractionEntity::class, WatchlistEntity::class, FeedCacheEntity::class, TitleFeaturesEntity::class, EventEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ReelwiseDatabase : RoomDatabase() {
    abstract fun interactions(): InteractionDao
    abstract fun watchlist(): WatchlistDao
    abstract fun feed(): FeedDao
    abstract fun learning(): LearningDao

    companion object {
        /** v2 adds the taste-learning tables; ratings, My list and the cache are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `title_features` (`id` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, " +
                        "`genres` TEXT NOT NULL, `language` TEXT, `year` INTEGER, PRIMARY KEY(`id`, `mediaType`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`id` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `kind` TEXT NOT NULL, `at` INTEGER NOT NULL)",
                )
            }
        }
    }
}
