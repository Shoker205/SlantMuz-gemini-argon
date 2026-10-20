package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        SearchHistoryEntity::class,
        CachedLyricsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SlantMuzDatabase : RoomDatabase() {
    abstract fun dao(): SlantMuzDao

    companion object {
        @Volatile
        private var INSTANCE: SlantMuzDatabase? = null

        fun getInstance(context: Context): SlantMuzDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SlantMuzDatabase::class.java,
                    "slantmuz_audiophile.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
