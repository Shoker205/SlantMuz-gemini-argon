package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.data.remote.HttpClientProvider
import java.io.File

@OptIn(UnstableApi::class)
object ExoPlayerCacheProvider {

    @Volatile
    private var simpleCache: SimpleCache? = null

    private var cacheDirFile: File? = null

    fun getCache(context: Context, maxCacheSizeMb: Int = 512): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val dir = File(context.cacheDir, "slantmuz_exo_media_cache").apply { mkdirs() }
                cacheDirFile = dir
                val maxBytes = maxCacheSizeMb.toLong() * 1024L * 1024L
                val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
                val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(dir, evictor, dbProvider).also { simpleCache = it }
            }
        }
    }

    fun buildCacheDataSourceFactory(
        context: Context,
        maxCacheSizeMb: Int = 512
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpClientProvider.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(10_000)

        val upstreamFactory = DefaultDataSource.Factory(context.applicationContext, httpFactory)
        val cache = getCache(context, maxCacheSizeMb)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getCacheUsedBytes(context: Context): Long {
        return runCatching {
            val cache = simpleCache
            if (cache != null) {
                cache.cacheSpace
            } else {
                val dir = File(context.cacheDir, "slantmuz_exo_media_cache")
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }.getOrDefault(0L)
    }

    fun clearCacheFiles(context: Context) {
        runCatching {
            val dir = cacheDirFile ?: File(context.cacheDir, "slantmuz_exo_media_cache")
            simpleCache?.keys?.toList()?.forEach { key ->
                runCatching { simpleCache?.removeResource(key) }
            }
            dir.listFiles()?.forEach { file ->
                if (file.isFile && !file.name.endsWith(".db")) {
                    runCatching { file.delete() }
                }
            }
        }
    }
}
