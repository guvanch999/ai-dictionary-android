package com.sozluk.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sozluk.ai.ui.navigation.AppNavHost
import com.sozluk.ai.ui.theme.AiDictionaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash must be installed before super.onCreate / setContent.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // "Open in app" from the lookup card deep-links straight to a word (spec F-5).
        val deepLinkWordId = intent.getStringExtra(EXTRA_WORD_ID)
        setContent {
            AiDictionaryTheme {
                AppNavHost(deepLinkWordId = deepLinkWordId)
            }
        }
    }

    companion object {
        const val EXTRA_WORD_ID = "com.sozluk.ai.extra.WORD_ID"
    }
}
