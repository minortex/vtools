package com.omarea.vtools.activities

import android.content.Intent
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
import com.omarea.data.customer.PowerUtilizationCurve
import com.omarea.data.GlobalStatus
import com.omarea.library.device.BatteryCapacity
import com.omarea.library.shell.BatteryUtils
import com.omarea.store.BatteryHistoryStore
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
        }
        return super.onOptionsItemSelected(item)
    }

    private var batteryUtils = BatteryUtils()
    private val electricityUnit = ElectricityUnit()
    private val handler = Handler(Looper.getMainLooper())
    private fun updateUI() {
        val level = GlobalStatus.batteryCapacity
        val temp = GlobalStatus.updateBatteryTemperature()
        val kernelCapacity = batteryUtils.getKernelCapacity(level)
        val batteryCapacityMAH = BatteryCapacity().getBatteryCapacity(this)
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
        battery_cycle_capacity_drop.text = "${stats.screenOnCapacityDrop}%"
        battery_cycle_screen_off_capacity_drop.text = "${stats.screenOffCapacityDrop}%"
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
