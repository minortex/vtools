package com.omarea.vtools.activities

import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.omarea.common.ui.DialogHelper
import com.omarea.data.customer.PowerUtilizationCurve
import com.omarea.data.GlobalStatus
import com.omarea.library.device.BatteryCapacity
import com.omarea.library.shell.BatteryUtils
import com.omarea.store.BatteryHistoryStore
import com.omarea.store.SpfConfig
import com.omarea.ui.power.AdapterBatteryStats
import com.omarea.utils.ElectricityUnit
import com.omarea.vtools.R
import com.omarea.vtools.dialogs.DialogElectricityUnit
import kotlinx.android.synthetic.main.activity_power_utilization.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs

class ActivityPowerUtilization : ActivityBase() {
    private lateinit var storage: BatteryHistoryStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_power_utilization)

        setBackArrow()
        storage = BatteryHistoryStore(context)

        electricity_adj_unit.setOnClickListener {
            DialogElectricityUnit().showDialog(this)
        }
        more_charge.setOnClickListener {
            val intent = Intent(context, ActivityCharge::class.java)
            startActivity(intent)
        }
        GlobalScope.launch(Dispatchers.Main) {
            if (BatteryUtils().qcSettingSupport() || batteryUtils.bpSettingSupport()) {
                charge_controller.visibility = View.VISIBLE
                charge_controller.setOnClickListener {
                    val intent = Intent(context, ActivityChargeController::class.java)
                    startActivity(intent)
                }
            }
        }
        battery_stats.layoutManager = LinearLayoutManager(this).apply {
            orientation = LinearLayoutManager.VERTICAL
            isSmoothScrollbarEnabled = false
        }

        // 切换阶梯模式
        view_time_title.setOnClickListener {
            view_time.setLadder(!view_time.getLadder())
        }
    }

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_power_utilization)
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.delete, menu)
        return true
    }

    //右上角菜单
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete -> {
                BatteryHistoryStore(context).clearData()
                Toast.makeText(context, "统计记录已清理", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            R.id.action_battery_debug -> {
                showBatteryDebugInfo()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private var batteryUtils = BatteryUtils()
    private val electricityUnit = ElectricityUnit()
    private val handler = Handler(Looper.getMainLooper())

    private fun showBatteryDebugInfo() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val voltage = GlobalStatus.batteryVoltage
        val remainingInfo = batteryUtils.getRemainingCapacityInfo(this, voltage)
        val currentCapacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val capacityInfo = batteryUtils.getKernelCapacityInfo(currentCapacity)
        val fullCapacityInfo = batteryUtils.getFullCapacityInfo(this)
        val rawCurrent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentUnit = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE).getInt(
                SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT,
                SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT_DEFAULT
        )
        val currentMA = if (currentUnit != 0) rawCurrent / currentUnit else 0
        val estimatedMAH = if (fullCapacityInfo.value > 0 && currentCapacity > 0) {
            fullCapacityInfo.value * currentCapacity / 100.0
        } else {
            0.0
        }
        val message = StringBuilder()
                .append("剩余 mAh：\n")
                .append(remainingInfo.source).append("\n\n")
                .append("原始值：").append(remainingInfo.rawValue).append("\n")
                .append("换算值：").append(String.format(Locale.getDefault(), "%.1fmAh", remainingInfo.valueMAH)).append("\n")
                .append("说明：").append(remainingInfo.reason).append("\n")
                .append("可选来源：\n").append(remainingInfo.options.joinToString("\n")).append("\n\n")
                .append("电量百分比：\n")
                .append(capacityInfo.source).append("\n")
                .append("原始值：").append(capacityInfo.rawValue).append("\n")
                .append("显示值：").append(String.format(Locale.getDefault(), "%.2f%%", capacityInfo.value)).append("\n")
                .append("说明：").append(capacityInfo.reason).append("\n")
                .append("可选来源：\n").append(capacityInfo.options.joinToString("\n")).append("\n\n")
                .append("电池容量：\n")
                .append(fullCapacityInfo.source).append("\n")
                .append("原始值：").append(fullCapacityInfo.rawValue).append("\n")
                .append("换算值：").append(String.format(Locale.getDefault(), "%.1fmAh", fullCapacityInfo.value)).append("\n")
                .append("按系统百分比估算剩余：").append(String.format(Locale.getDefault(), "%.1fmAh", estimatedMAH)).append("\n")
                .append("说明：").append(fullCapacityInfo.reason).append("\n")
                .append("可选来源：\n").append(fullCapacityInfo.options.joinToString("\n")).append("\n\n")
                .append("电流：\n")
                .append("BatteryManager.BATTERY_PROPERTY_CURRENT_NOW\n")
                .append("原始值：").append(rawCurrent).append("\n")
                .append("单位换算：/ ").append(currentUnit).append("\n")
                .append("换算值：").append(currentMA).append("mA\n")
                .append("说明：Android API 读取；实际显示值会受当前单位校准影响\n")
                .append("可选来源：\n")
                .append("BatteryManager.BATTERY_PROPERTY_CURRENT_NOW\n\n")
                .append("电压：\n")
                .append("ACTION_BATTERY_CHANGED / EXTRA_VOLTAGE\n")
                .append("当前值：").append(voltage).append("V\n")
                .append("说明：Android 广播值，无需 root、无需 shell；用于 W 和 energy_now 换算\n")
                .append("可选来源：\n")
                .append("ACTION_BATTERY_CHANGED / EXTRA_VOLTAGE")
                .toString()

        DialogHelper.helpInfo(this, "耗电统计调试", message)
    }

    private fun updateUI() {
        val level = GlobalStatus.batteryCapacity
        val temp = GlobalStatus.updateBatteryTemperature()
        val kernelCapacity = batteryUtils.getKernelCapacity(level)
        val fullCapacityInfo = batteryUtils.getFullCapacityInfo(this)
        val batteryCapacityMAH = if (fullCapacityInfo.value > 0) fullCapacityInfo.value else BatteryCapacity().getBatteryCapacity(this)
        val batteryMAH = batteryCapacityMAH.toInt().toString() + "mAh" + "   "
        val voltage = GlobalStatus.batteryVoltage
        val remainingBatteryMAH = batteryUtils.getRemainingCapacityMAH(this, voltage)

        val data = storage.getAvgData()
        val cycleStats = storage.cycleStats
        val sampleTime = (PowerUtilizationCurve.SAMPLING_INTERVAL / 1000).toInt()

        handler.post {
            val appStats = data.filter {
                // 仅显示运行时间超过约1分钟的应用数据，避免短时间采样误差过大
                (it.count * sampleTime) >= 60
            }
            battery_stats.adapter = AdapterBatteryStats(context, appStats.ifEmpty { data })

            view_time.invalidate()

            if (kernelCapacity > -1) {
                val str = "$kernelCapacity%"
                val ss = SpannableString(str)
                if (str.contains(".")) {
                    val small = AbsoluteSizeSpan((battery_capacity.textSize * 0.45).toInt(), false)
                    ss.setSpan(small, str.indexOf("."), str.lastIndexOf("%"), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val medium = AbsoluteSizeSpan((battery_capacity.textSize * 0.65).toInt(), false)
                    ss.setSpan(medium, str.indexOf("%"), str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                battery_capacity.text = ss
            } else {
                battery_capacity.text = "" + level + "%"
            }

            battery_status.text = (when (GlobalStatus.batteryStatus) {
                BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                    getString(R.string.battery_status_discharging)
                }
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    getString(R.string.battery_status_charging)
                }
                BatteryManager.BATTERY_STATUS_FULL -> {
                    getString(R.string.battery_status_full)
                }
                BatteryManager.BATTERY_STATUS_UNKNOWN -> {
                    getString(R.string.battery_status_unknown)
                }
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                    getString(R.string.battery_status_not_charging)
                }
                else -> getString(R.string.battery_status_unknown)
            })
            battery_voltage.text = "${voltage}v"
            battery_temperature.text =  "$temp°C"
            battery_size.text = batteryMAH
            updateCycleStats(cycleStats, if (kernelCapacity > -1) kernelCapacity else level.toFloat(), batteryCapacityMAH, remainingBatteryMAH)
        }

        updateMaxState()
    }

    private fun updateCycleStats(stats: com.omarea.model.BatteryCycleStats, currentCapacity: Float, batteryCapacityMAH: Double, remainingBatteryMAH: Double) {
        if (stats.sampleCount < 2) {
            battery_cycle_avg_power.text = "--"
            battery_cycle_screen_time.text = "--"
            battery_cycle_screen_off_time.text = "--"
            battery_cycle_capacity_drop.text = "--"
            battery_cycle_screen_off_capacity_drop.text = "--"
            battery_cycle_remaining_screen.text = "数据不足"
            return
        }

        battery_cycle_avg_power.text = if (stats.screenOnAvgCurrent > 0) {
            electricityUnit.formatBatteryIO(context, stats.screenOnAvgCurrent.toLong(), false, " / ", stats.screenOnAvgVoltage.toDouble())
        } else {
            "--"
        }
        battery_cycle_screen_time.text = formatDuration(stats.screenOnTime)
        battery_cycle_screen_off_time.text = formatDuration(stats.screenOffTime)
        battery_cycle_capacity_drop.text = formatConsumedCapacity(stats.screenOnConsumedMAH, stats.screenOnCapacityDrop, batteryCapacityMAH)
        battery_cycle_screen_off_capacity_drop.text = formatConsumedCapacity(stats.screenOffConsumedMAH, stats.screenOffCapacityDrop, batteryCapacityMAH)
        val estimateCurrent = if (stats.screenOnAvgCurrent > 0) stats.screenOnAvgCurrent else stats.avgCurrent
        battery_cycle_remaining_screen.text = if (batteryCapacityMAH > 0 && currentCapacity > 0 && estimateCurrent > 0) {
            val remainingMAH = if (remainingBatteryMAH > 0 && remainingBatteryMAH <= batteryCapacityMAH * 1.3) {
                remainingBatteryMAH
            } else {
                batteryCapacityMAH * currentCapacity / 100.0
            }
            val remainingHours = remainingMAH / estimateCurrent
            formatDuration((remainingHours * 60 * 60 * 1000).toLong())
        } else {
            "数据不足"
        }
    }

    private fun formatConsumedCapacity(consumedMAH: Double, fallbackDrop: Int, batteryCapacityMAH: Double): String {
        if (consumedMAH > 0 && batteryCapacityMAH > 0) {
            val percent = consumedMAH * 100.0 / batteryCapacityMAH
            return if (percent < 0.1) {
                String.format(Locale.getDefault(), "%.0fmAh", consumedMAH)
            } else {
                String.format(Locale.getDefault(), "%.1f%%", percent)
            }
        }
        return "${fallbackDrop}%"
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) {
            return "--"
        }
        val minutes = (ms / 60000).toInt()
        if (minutes < 1) {
            return "<1分钟"
        }
        val days = minutes / (60 * 24)
        val hours = (minutes % (60 * 24)) / 60
        val mins = minutes % 60
        return when {
            days > 0 && hours > 0 -> "${days}天${hours}小时"
            days > 0 -> "${days}天"
            hours > 0 && mins > 0 -> "${hours}小时${mins}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${mins}分钟"
        }
    }

    private fun updateMaxState() {
        // 峰值设置
        val maxInput = abs(storage.getMaxIO(BatteryManager.BATTERY_STATUS_CHARGING))
        val maxOutput = abs(storage.getMinIO(BatteryManager.BATTERY_STATUS_DISCHARGING))
        val maxTemperature = abs(storage.getMaxTemperature())
        var batteryInputMax = 10000
        var batteryOutputMax = 3000
        var batteryTemperatureMax = 60

        if (maxInput > batteryInputMax) {
            batteryInputMax = maxInput
        }
        if (maxOutput > batteryOutputMax) {
            batteryOutputMax = maxOutput
        }
        if (maxTemperature > batteryTemperatureMax) {
            batteryTemperatureMax = maxTemperature
        }

        handler.post {
            try {
                battery_max_output.setData(batteryOutputMax.toFloat(), batteryOutputMax - maxOutput.toFloat())
                battery_max_output_text.text = electricityUnit.formatBatteryIO(context, maxOutput.toLong(), false, "\n")
                battery_max_intput.setData(batteryInputMax.toFloat(), batteryInputMax - maxInput.toFloat())
                battery_max_intput_text.text = electricityUnit.formatBatteryIO(context, maxInput.toLong(), false, "\n")
                if (maxTemperature < 0) {
                    battery_max_temperature.setData(batteryTemperatureMax.toFloat(), batteryTemperatureMax.toFloat())
                } else {
                    battery_max_temperature.setData(batteryTemperatureMax.toFloat(), batteryTemperatureMax - maxTemperature.toFloat())
                }
                battery_max_temperature_text.text = maxTemperature.toString() + "°C"
            } catch (ex: Exception) {
            }
        }
    }
}
