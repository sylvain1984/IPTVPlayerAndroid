package com.iptv.player

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.iptv.player.data.RefreshWorker
import java.util.concurrent.TimeUnit

class IPTVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundRefresh()
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
