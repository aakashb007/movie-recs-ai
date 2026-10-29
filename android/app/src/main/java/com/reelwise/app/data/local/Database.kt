package com.reelwise.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
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

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id AND mediaType = :mediaType)")
    fun observeContains(id: Int, mediaType: MediaType): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE id = :id AND mediaType = :mediaType)")
    suspend fun contains(id: Int, mediaType: MediaType): Boolean

    @Upsert
    suspend fun upsert(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id AND mediaType = :mediaType")
    suspend fun delete(id: Int, mediaType: MediaType)
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

@Database(
    entities = [InteractionEntity::class, WatchlistEntity::class, FeedCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReelwiseDatabase : RoomDatabase() {
    abstract fun interactions(): InteractionDao
    abstract fun watchlist(): WatchlistDao
    abstract fun feed(): FeedDao
}
