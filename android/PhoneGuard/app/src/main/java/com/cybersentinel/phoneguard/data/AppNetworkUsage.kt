package com.cybersentinel.phoneguard.data

/**
 * Traffico di rete di una singola app in un intervallo di tempo.
 */
data class AppNetworkUsage(
    val uid: Int,
    val packageName: String,
    val appLabel: String,
    val rxBytes: Long,       // dati ricevuti (download)
    val txBytes: Long,       // dati trasmessi (upload) — quelli che l'app "manda fuori"
    val isSystemApp: Boolean
) {
    val totalBytes: Long get() = rxBytes + txBytes

    /**
     * Un'app è considerata sospetta se l'upload supera la soglia
     * e rappresenta la maggior parte del suo traffico: sta soprattutto
     * inviando dati verso l'esterno invece di riceverne.
     */
    fun isSuspicious(txThresholdBytes: Long): Boolean =
        txBytes >= txThresholdBytes && txBytes > rxBytes
}

/**
 * Fotografia dello stato della batteria.
 */
data class BatterySnapshot(
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMillivolt: Int,
    val currentMicroAmpere: Int,   // negativo = scarica, positivo = carica
    val healthLabel: String,
    val chargeCounterMicroAmpereHour: Long
) {
    /** Potenza istantanea stimata in watt (corrente x tensione). */
    val estimatedWatts: Float
        get() = (currentMicroAmpere / 1_000_000f) * (voltageMillivolt / 1000f)
}
