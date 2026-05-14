package com.omarea.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import com.omarea.data.GlobalStatus
import com.omarea.store.SpfConfig
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.abs

class ElectricityUnit {
    public fun getDefaultElectricityUnit(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return if (Build.MANUFACTURER.toUpperCase(Locale.getDefault()) == "XIAOMI") {
            SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT_DEFAULT
        } else {
            if (GlobalStatus.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                if (currentNow > 20000) {
                    -1000
                } else if (currentNow < -20000) {
                    1000
                } else if (currentNow > 0) {
                    -1
                } else {
                    1
                }
            } else if (GlobalStatus.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
                if (currentNow > 20000) {
                    1000
                } else if (currentNow < -20000) {
                    -1000
                } else if (currentNow > 0) {
                    1
                } else {
                    -1
                }
            } else {
                SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT_DEFAULT
            }
        }
    }

    fun getBatteryVoltage(context: Context): Double {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0.0
        return normalizeVoltage(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0))
    }

    fun normalizeVoltage(voltage: Int): Double {
        return when {
            voltage > 1000000 -> voltage / 1000000.0
            voltage > 1000 -> voltage / 1000.0
            voltage > 100 -> voltage / 100.0
            voltage > 10 -> voltage / 10.0
            else -> voltage.toDouble()
        }
    }

    fun formatBatteryIO(
            context: Context,
            currentMA: Long,
            signed: Boolean = false,
            delimiter: String = " / ",
            voltage: Double = getBatteryVoltage(context),
            spf: SharedPreferences = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE),
            displayBoth: Boolean = spf.getBoolean(SpfConfig.GLOBAL_SPF_BATTERY_POWER_CURRENT_BOTH, false)
    ): String {
        val currentText = formatCurrent(currentMA, signed)
        if (!displayBoth) {
            return currentText
        }

        val powerText = formatPower(currentMA, voltage, signed)
        return if (powerText == null) {
            currentText
        } else {
            "$powerText$delimiter$currentText"
        }
    }

    fun formatCurrent(currentMA: Long, signed: Boolean = false): String {
        val prefix = if (signed && currentMA > 0) "+" else ""
        return "$prefix${currentMA}mA"
    }

    fun formatPower(currentMA: Long, voltage: Double, signed: Boolean = false): String? {
        if (voltage <= 0) {
            return null
        }

        val power = currentMA * voltage / 1000.0
        val prefix = if (signed && power > 0) "+" else ""
        return "$prefix${format1(power)}W"
    }

    private fun format1(value: Double): String {
        var bd = BigDecimal(value)
        bd = bd.setScale(if (abs(value) < 10) 2 else 1, RoundingMode.HALF_UP)
        return bd.toString()
    }
}
