package com.sozluk.ai.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ApiKeyStore] backed by [EncryptedSharedPreferences] (AES-256, keys in the Android Keystore),
 * spec §3.2. The key value is never written to logs.
 */
@Singleton
class EncryptedApiKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApiKeyStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    override fun setKey(key: String) {
        prefs.edit().putString(KEY_API, key).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    override fun hasKey(): Boolean = getKey() != null

    private companion object {
        const val PREFS_NAME = "ai_secure_prefs"
        const val KEY_API = "anthropic_api_key"
    }
}
