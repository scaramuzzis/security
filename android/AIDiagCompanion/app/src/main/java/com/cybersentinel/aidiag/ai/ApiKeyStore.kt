package com.cybersentinel.aidiag.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Salva la API key cifrata su disco (Android Keystore), mai in chiaro né trasmessa altrove. */
object ApiKeyStore {

    private const val PREFS_NAME = "aidiag_secure_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun apiKey(context: Context): String? = prefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun model(context: Context): String = prefs(context).getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: AiClient.DEFAULT_MODEL

    fun setModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
