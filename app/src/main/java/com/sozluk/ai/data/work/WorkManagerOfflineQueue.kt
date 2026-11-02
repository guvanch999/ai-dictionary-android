package com.sozluk.ai.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sozluk.ai.domain.OfflineQueue
import com.sozluk.ai.domain.repository.PendingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Persists the word into the pending table and schedules [DrainPendingWorker] to run once the
 * network is back (spec F-1.5). Unique work coalesces bursts into a single drain pass.
 */
class WorkManagerOfflineQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pending: PendingRepository,
) : OfflineQueue {

    override suspend fun enqueue(term: String, contextSentence: String?) {
        pending.add(term, contextSentence)

        val request = OneTimeWorkRequestBuilder<DrainPendingWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DrainPendingWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
