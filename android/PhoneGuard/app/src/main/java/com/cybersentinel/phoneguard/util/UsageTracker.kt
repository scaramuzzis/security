package com.cybersentinel.phoneguard.util

import android.content.Context

/**
 * Conta quante volte l'utente ha aperto ogni voce del menu, per costruire
 * la sezione "Più usati" del drawer. Persistito in SharedPreferences
 * (leggero: pochi interi, nessun costo di avvio).
 */
object UsageTracker {

    private const val PREFS_NAME = "phoneguard_usage"
    private const val KEY_PREFIX = "count_"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Registra un utilizzo della voce di menu [navItemId]. */
    fun recordUse(context: Context, navItemId: Int) {
        val key = KEY_PREFIX + navItemId
        val sp = sp(context)
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    /** Gli [limit] navItemId più usati, in ordine decrescente. Esclude i mai usati. */
    fun topUsed(context: Context, limit: Int): List<Int> {
        val sp = sp(context)
        return sp.all.entries
            .mapNotNull { (key, value) ->
                val id = key.removePrefix(KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val count = value as? Int ?: return@mapNotNull null
                if (count <= 0) return@mapNotNull null
                id to count
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
