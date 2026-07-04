package com.cybersentinel.aidiag.data

/** Un campionamento periodico: stato di batteria e schermo in un istante. */
data class BatterySample(
    val timestamp: Long,
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val isCharging: Boolean,
    val screenOn: Boolean
)

/** Traffico dati di un'app osservato in un intervallo di campionamento. */
data class NetworkSample(
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val txBytes: Long,
    val rxBytes: Long,
    val screenOn: Boolean
)

enum class GrantKind { DEVICE_ADMIN, ACCESSIBILITY, NOTIFICATION_LISTENER }

/** Un permesso privilegiato concesso a un'app, con quando è stato osservato la prima volta. */
data class PrivilegedGrant(
    val packageName: String,
    val appLabel: String,
    val kind: GrantKind,
    val firstSeen: Long
)
