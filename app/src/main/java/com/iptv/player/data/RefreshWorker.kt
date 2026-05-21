package com.iptv.player.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = ChannelRepository(applicationContext)
            repo.load()

            val lastRefresh = repo.lastRefreshMs.value
            val staleThresholdMs = 6 * 3_600_000L
            val isStale = lastRefresh == null ||
                System.currentTimeMillis() - lastRefresh > staleThresholdMs

            if (isStale) {
                // coroutineScope keeps the worker alive until refresh + full validation finish
                coroutineScope { repo.refresh(this) }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
