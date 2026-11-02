package com.sozluk.ai.ui.lookup

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.sozluk.ai.MainActivity
import com.sozluk.ai.ui.theme.AiDictionaryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent activity launched from the system text-selection menu (PROCESS_TEXT, spec F-5).
 * Shows a compact card over the source app; never enters Recents (see manifest flags).
 */
@AndroidEntryPoint
class LookupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selection = readSelection(intent)
        if (selection.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            AiDictionaryTheme {
                LookupScreen(
                    selection = selection,
                    onOpenInApp = { id -> openApp(id) },
                    onOpenApp = { openApp(null) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    // singleTask: a fresh selection arrives here; rebuild the card with it.
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

    private fun readSelection(intent: Intent): String? =
        (intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY))
            ?.toString()
}
