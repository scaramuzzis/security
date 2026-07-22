package com.cybersentinel.phoneguard.data

/**
 * App considerata attiva in background nel momento della lettura.
 */
data class RunningApp(
    val packageName: String,
    val appLabel: String,
    val reason: String,
    val lastActiveMillis: Long,
    val hasForegroundService: Boolean
)
