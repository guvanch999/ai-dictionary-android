package com.sozluk.ai.data.screenshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sozluk.ai.R
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.screenshot.ScreenshotDebouncer
import com.sozluk.ai.ui.screenshot.PencilBubble
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * Foreground service that watches MediaStore for new screenshots and pops the [PencilBubble]
 * (spec F-6, task 10). Runs only while the "Screenshot watcher" toggle is on; started/stopped from
 * the app (no boot autostart — explicit simplification). `specialUse` FGS type (see manifest).
 *
 * Pipeline: `ContentObserver.onChange` → [ScreenshotDebouncer] (collapse the onChange storm) →
 * [NewestScreenshotQuery] (newest screenshot in the last few seconds) → dedup by Uri → show bubble.
 * Everything inside the observer is guarded so a stray exception can never kill the service.
 */
@AndroidEntryPoint
class ScreenshotWatcherService : Service() {

    @Inject
    @field:Named("io")
    lateinit var io: CoroutineDispatcher

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope by lazy { CoroutineScope(SupervisorJob() + io) }
    private val debouncer = ScreenshotDebouncer { System.currentTimeMillis() }

    private lateinit var observerThread: HandlerThread
    private lateinit var observer: ContentObserver
    private var bubble: PencilBubble? = null

    @Volatile
    private var lastHandledUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        // Must call startForeground within ~5s of start; do it first, before any query work.
        startInForeground()
        bubble = PencilBubble(this)
        registerObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // "Stop" from the notification: persist the toggle off, then tear down.
            scope.launch { runCatching { settingsRepository.setScreenshotWatcherEnabled(false) } }
            stopSelf()
            return START_NOT_STICKY
        }
        // Guard against a START_STICKY restart after the toggle was turned off.
        scope.launch {
            val enabled = runCatching { settingsRepository.screenshotWatcherEnabled.first() }.getOrDefault(true)
            if (!enabled) stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(observer) }
        bubble?.hide()
        bubble = null
        scope.cancel()
        if (this::observerThread.isInitialized) observerThread.quitSafely()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerObserver() {
        observerThread = HandlerThread("screenshot-observer").apply { start() }
        val handler = Handler(observerThread.looper)
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onMediaChanged()
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
    }

    private fun onMediaChanged() {
        // Never let observer work crash the service (some ROMs storm onChange).
        try {
            debouncer.onEvent()
            scope.launch {
                delay(ScreenshotDebouncer.DEFAULT_WINDOW_MS)
                if (!debouncer.shouldFire()) return@launch // superseded by a newer event
                val uri = NewestScreenshotQuery.find(contentResolver, System.currentTimeMillis())
                    ?: return@launch
                if (uri == lastHandledUri) return@launch // dedup: same screenshot
                lastHandledUri = uri
                withContext(Dispatchers.Main) { bubble?.show(uri) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Screenshot observer failed", t)
        }
    }

    private fun startInForeground() {
        ensureChannel()
        val stopIntent = Intent(this, ScreenshotWatcherService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.screenshot_service_notification_title))
            .setContentText(getString(R.string.screenshot_service_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(R.string.screenshot_service_notification_stop),
                stopPending,
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.screenshot_service_notification_channel),
                    NotificationManager.IMPORTANCE_LOW, // unobtrusive: no sound
                ),
            )
        }
    }

    companion object {
        private const val TAG = "ScreenshotWatcher"
        private const val CHANNEL_ID = "screenshot_watcher"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.sozluk.ai.action.STOP_SCREENSHOT_WATCHER"

        /** Starts the foreground watcher. Call only after all permissions are granted. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenshotWatcherService::class.java),
            )
        }

        /** Stops the watcher and tears down observer/overlay/notification. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotWatcherService::class.java))
        }
    }
}
