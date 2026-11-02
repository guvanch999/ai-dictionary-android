package com.sozluk.ai.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sozluk.ai.R
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.model.toWordEntry
import com.sozluk.ai.domain.repository.PendingRepository
import com.sozluk.ai.domain.repository.WordRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the offline queue once the network is available (spec F-1.5): explains each pending word
 * via [AiClient], saves it, removes it from the queue, and notifies the user. Network/rate-limit
 * failures ask WorkManager to retry; other failures leave the word queued for a later pass.
 */
@HiltWorker
class DrainPendingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pending: PendingRepository,
    private val wordRepository: WordRepository,
    private val aiClient: AiClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var shouldRetry = false
        for (item in pending.getAll()) {
            aiClient.explainWord(item.term, item.contextSentence)
                .onSuccess { article ->
                    wordRepository.upsert(article.toWordEntry(item.term, item.contextSentence))
                    pending.remove(item.id)
                    notifyExplained(item.term)
                }
                .onFailure { error ->
                    // Retry only for transient failures; otherwise leave it queued for later.
                    if (error is AiError.Network || error is AiError.RateLimited) shouldRetry = true
                }
        }
        return if (shouldRetry) Result.retry() else Result.success()
    }

    private fun notifyExplained(term: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // No permission — skip silently (best-effort, spec §M1).
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.pending_notification_title))
            .setContentText(context.getString(R.string.pending_notification_text, term))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(term.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.pending_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    companion object {
        const val UNIQUE_NAME = "drain_pending_words"
        private const val CHANNEL_ID = "pending_words"
    }
}
