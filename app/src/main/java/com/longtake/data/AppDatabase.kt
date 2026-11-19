package com.longtake.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Upsert
    suspend fun upsertVideos(videos: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    suspend fun getVideos(ids: List<String>): List<VideoEntity>

    @Query("SELECT id FROM videos WHERE fromSource = 1")
    suspend fun sourceVideoIds(): List<String>

    @Query("SELECT id FROM videos WHERE fromSource = 1 AND fetchedAt < :before ORDER BY fetchedAt LIMIT :limit")
    suspend fun staleSourceVideoIds(before: Long, limit: Int): List<String>

    @Query("UPDATE videos SET views = :views, likes = :likes, fetchedAt = :at WHERE id = :id")
    suspend fun updateStats(id: String, views: Long, likes: Long, at: Long)

    @Query("UPDATE videos SET fromSource = 1 WHERE id IN (:ids)")
    suspend fun markFromSource(ids: List<String>)

    @Query("UPDATE videos SET curated = 1 WHERE id IN (:ids)")
    suspend fun markCurated(ids: List<String>)

    @Transaction
    @Query("SELECT * FROM videos WHERE fromSource = 1")
    fun feed(): Flow<List<VideoWithState>>

    @Transaction
    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    fun videosWithState(ids: List<String>): Flow<List<VideoWithState>>

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :id")
    fun videoWithState(id: String): Flow<VideoWithState?>

    @Transaction
    @Query("SELECT v.* FROM videos v JOIN user_videos u ON u.videoId = v.id WHERE u.saved = 1 ORDER BY u.savedAt DESC")
    fun saved(): Flow<List<VideoWithState>>

    @Transaction
    @Query("SELECT v.* FROM videos v JOIN user_videos u ON u.videoId = v.id WHERE u.watched = 1 ORDER BY u.watchedAt DESC")
    fun watched(): Flow<List<VideoWithState>>

    @Transaction
    @Query("SELECT v.* FROM videos v JOIN user_videos u ON u.videoId = v.id WHERE u.rating > 0 OR u.note != '' ORDER BY u.updatedAt DESC")
    fun ratedOrNoted(): Flow<List<VideoWithState>>

    @Query("DELETE FROM videos WHERE fromSource = 0 AND id NOT IN (SELECT videoId FROM user_videos) AND fetchedAt < :before")
    suspend fun pruneOldSearchResults(before: Long)

    @Query("UPDATE videos SET fromSource = 0 WHERE channelId = :channelId")
    suspend fun unmarkChannelFromSource(channelId: String)
}

@Dao
interface ChannelDao {
    @Upsert
    suspend fun upsert(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun get(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isSource = 1 ORDER BY title COLLATE NOCASE")
    fun sources(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isSource = 1")
    suspend fun sourcesNow(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isBlocked = 1 ORDER BY title COLLATE NOCASE")
    fun blocked(): Flow<List<ChannelEntity>>

    @Query("SELECT id FROM channels WHERE isBlocked = 1")
    suspend fun blockedIds(): List<String>
}

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsert(p: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY title COLLATE NOCASE")
    fun all(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun allNow(): List<PlaylistEntity>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AiCheckDao {
    @Upsert
    suspend fun upsert(c: AiCheckEntity)

    @Query("SELECT * FROM ai_checks WHERE videoId = :id")
    suspend fun get(id: String): AiCheckEntity?

    @Query("SELECT videoId FROM ai_checks")
    suspend fun checkedIds(): List<String>

    /** Forget checks where the Claude step failed, so they're retried. */
    @Query("DELETE FROM ai_checks WHERE reason LIKE '%Claude check failed%'")
    suspend fun clearFailedClaude()

    @Query("DELETE FROM ai_checks WHERE videoId = :id")
    suspend fun delete(id: String)

    /** Forget "no transcript" results from before [before], so those videos are tried again. */
    @Query("DELETE FROM ai_checks WHERE verdict IN ('no_transcript', 'age_restricted') AND checkedAt < :before")
    suspend fun clearNoTranscriptBefore(before: Long)
}

@Dao
interface UserVideoDao {
    @Query("SELECT * FROM user_videos WHERE videoId = :id")
    suspend fun get(id: String): UserVideoEntity?

    @Upsert
    suspend fun upsert(u: UserVideoEntity)

    @Upsert
    suspend fun upsertAll(u: List<UserVideoEntity>)

    @Query("SELECT videoId FROM user_videos")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM user_videos WHERE videoId IN (:ids)")
    suspend fun getAll(ids: List<String>): List<UserVideoEntity>
}

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<WatchHistoryEntity>)

    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun count(): Int
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM section_videos")
    fun all(): Flow<List<SectionVideoEntity>>

    @Query("DELETE FROM section_videos WHERE section = :section")
    suspend fun clear(section: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SectionVideoEntity>)

    /** Swap a section's video list for a fresh one. */
    @Transaction
    suspend fun replace(section: String, videoIds: List<String>) {
        clear(section)
        insertAll(videoIds.distinct().map { SectionVideoEntity(section, it) })
    }
}

@Database(
    entities = [
        VideoEntity::class, ChannelEntity::class, PlaylistEntity::class,
        AiCheckEntity::class, UserVideoEntity::class, WatchHistoryEntity::class, SectionVideoEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videos(): VideoDao
    abstract fun channels(): ChannelDao
    abstract fun playlists(): PlaylistDao
    abstract fun aiChecks(): AiCheckDao
    abstract fun userVideos(): UserVideoDao
    abstract fun watchHistory(): WatchHistoryDao
    abstract fun sections(): SectionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "longtake.db")
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}

/** Adds the Discover sections table without touching anything already saved. */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `section_videos` (`section` TEXT NOT NULL, `videoId` TEXT NOT NULL, PRIMARY KEY(`section`, `videoId`))")
    }
}
