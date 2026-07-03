package com.cybersentinel.phoneguard.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cybersentinel.phoneguard.data.BatterySnapshot

/**
 * Legge lo stato attuale della batteria combinando il broadcast sticky
 * ACTION_BATTERY_CHANGED (livello, temperatura, tensione, salute) con le
 * proprietà di BatteryManager (corrente istantanea, contatore di carica).
 */
class BatteryMonitor(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun snapshot(): BatterySnapshot {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale
        else batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val temperature =
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buona"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surriscaldata"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Esausta"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sovratensione"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fredda"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Guasto"
            else -> "Sconosciuta"
        }

        val currentNow =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeCounter =
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        return BatterySnapshot(
            levelPercent = percent,
            isCharging = isCharging,
            temperatureCelsius = temperature,
            voltageMillivolt = voltage,
            currentMicroAmpere = currentNow,
            healthLabel = health,
            chargeCounterMicroAmpereHour = chargeCounter
        )
    }
}
