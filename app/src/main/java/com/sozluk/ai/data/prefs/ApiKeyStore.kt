package com.sozluk.ai.data.prefs

/**
 * Storage for the user's Anthropic API key. An interface so it can be faked in unit tests
 * (the encrypted implementation needs Android context). The key is never logged.
 */
interface ApiKeyStore {
    /** The stored key, or null if none is set. */
    fun getKey(): String?

    /** Persist [key]. */
    fun setKey(key: String)

    /** Remove the stored key. */
    fun clear()

    /** True if a non-blank key is stored. */
    fun hasKey(): Boolean
}
