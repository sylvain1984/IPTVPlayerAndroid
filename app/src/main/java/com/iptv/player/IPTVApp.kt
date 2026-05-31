package com.iptv.player

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.iptv.player.data.RefreshWorker
import java.util.concurrent.TimeUnit

class IPTVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        configureCoil()
        scheduleBackgroundRefresh()
    }

    private fun configureCoil() {
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("logo_cache"))
                        .maxSizeBytes(32 * 1024 * 1024)
                        .build()
                }
                .respectCacheHeaders(false) // logo 几乎不变，忽略 cache-control
                .build()
        )
    }

    private fun scheduleBackgroundRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // KEEP: don't reschedule if a task is already enqueued
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "iptv_background_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
