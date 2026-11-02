package com.sozluk.ai.ui.imageshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.sozluk.ai.MainActivity
import com.sozluk.ai.ui.theme.AiDictionaryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for sharing an image into the app (spec F-6, simplified F-6 entry: ACTION_SEND with
 * an image mime type, optionally ACTION_VIEW). Full-screen; hosts the annotate then OCR then save
 * flow, then deep-links into [MainActivity] to show the saved word.
 */
@AndroidEntryPoint
class ImageShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = readImageUri(intent)
        if (uri == null) {
            finish()
            return
        }

        setContent {
            AiDictionaryTheme {
                ImageShareRoot(
                    uri = uri,
                    onOpenWord = { id -> openApp(id) },
                    onOpenSettings = { openApp(null) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    // singleTask: a fresh share arrives here; rebuild the flow with it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun openApp(wordId: String?) {
        val target = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (wordId != null) target.putExtra(MainActivity.EXTRA_WORD_ID, wordId)
        startActivity(target)
        finish()
    }

    private fun readImageUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}
