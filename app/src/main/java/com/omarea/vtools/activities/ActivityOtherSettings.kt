package com.omarea.vtools.activities

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Switch
import androidx.core.content.PermissionChecker
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.data.EventBus
import com.omarea.data.EventType
import com.omarea.shell_utils.AppErrorLogcatUtils
import com.omarea.store.SpfConfig
import com.omarea.utils.CommonCmds
import com.omarea.vtools.R
import kotlinx.android.synthetic.main.activity_other_settings.*

class ActivityOtherSettings : ActivityBase() {
    private lateinit var spf: SharedPreferences
    private var myHandler = Handler(Looper.getMainLooper())

    override fun onPostResume() {
        super.onPostResume()
        delegate.onPostResume()

        settings_disable_selinux.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        spf = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_settings)

        setBackArrow()

        settings_disable_selinux.setOnClickListener {
            if (settings_disable_selinux.isChecked) {
                KeepShellPublic.doCmdSync(CommonCmds.DisableSELinux)
                myHandler.postDelayed({
                    spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, settings_disable_selinux.isChecked).apply()
                }, 10000)
            } else {
                KeepShellPublic.doCmdSync(CommonCmds.ResumeSELinux)
                spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, settings_disable_selinux.isChecked).apply()
            }
        }
        settings_logcat.setOnClickListener {
            val log = AppErrorLogcatUtils().catLogInfo()
            settings_log_content.visibility = View.VISIBLE
            settings_log_content.setText(log)
            settings_log_content.setSelection(0, log.length)
        }

        settings_debug_layer.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_SCENE_LOG, false)
        settings_debug_layer.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_SCENE_LOG, (it as Switch).isChecked).apply()

            EventBus.publish(EventType.SERVICE_DEBUG)
        }

        settings_help_icon.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_HELP_ICON, true)
        settings_help_icon.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_HELP_ICON, (it as Switch).isChecked).apply()
        }

        settings_auto_exit.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_AUTO_EXIT, true)
        settings_auto_exit.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_AUTO_EXIT, (it as Switch).isChecked).apply()
        }

        settings_scene_notification.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_SCENE_NOTIFICATION, true)
        settings_scene_notification.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_SCENE_NOTIFICATION, (it as Switch).isChecked).apply()
            EventBus.publish(EventType.SCENE_MODE_ACTION)
        }

        settings_black_notification.isChecked = spf.getBoolean(SpfConfig.GLOBAL_NIGHT_BLACK_NOTIFICATION, false)
        settings_black_notification.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.GLOBAL_NIGHT_BLACK_NOTIFICATION, (it as Switch).isChecked).apply()
        }

        bindHomeDisplaySwitch(settings_home_show_memory, SpfConfig.GLOBAL_SPF_HOME_SHOW_MEMORY)
        bindHomeDisplaySwitch(settings_home_show_gpu, SpfConfig.GLOBAL_SPF_HOME_SHOW_GPU)
        bindHomeDisplaySwitch(settings_home_show_cpu, SpfConfig.GLOBAL_SPF_HOME_SHOW_CPU)
        bindHomeDisplaySwitch(settings_home_show_battery, SpfConfig.GLOBAL_SPF_HOME_SHOW_BATTERY)

        settings_battery_reset_on_unplug.isChecked = spf.getBoolean(SpfConfig.GLOBAL_SPF_BATTERY_RESET_ON_UNPLUG, false)
        settings_battery_reset_on_unplug.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_BATTERY_RESET_ON_UNPLUG, (it as Switch).isChecked).apply()
            updateBatteryResetThresholdState()
        }

        val resetLevel = spf.getInt(SpfConfig.GLOBAL_SPF_BATTERY_RESET_LEVEL, SpfConfig.GLOBAL_SPF_BATTERY_RESET_LEVEL_DEFAULT)
        settings_battery_reset_level.progress = (resetLevel - 50).coerceIn(0, 50)
        settings_battery_reset_level_value.text = "${settings_battery_reset_level.progress + 50}%"
        settings_battery_reset_level.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 50
                settings_battery_reset_level_value.text = "${value}%"
                if (fromUser) {
                    spf.edit().putInt(SpfConfig.GLOBAL_SPF_BATTERY_RESET_LEVEL, value).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = (seekBar?.progress ?: 35) + 50
                spf.edit().putInt(SpfConfig.GLOBAL_SPF_BATTERY_RESET_LEVEL, value).apply()
            }
        })

        val resetChargeDelta = spf.getInt(SpfConfig.GLOBAL_SPF_BATTERY_RESET_CHARGE_DELTA, SpfConfig.GLOBAL_SPF_BATTERY_RESET_CHARGE_DELTA_DEFAULT)
        settings_battery_reset_charge_delta.progress = (resetChargeDelta - 1).coerceIn(0, 99)
        settings_battery_reset_charge_delta_value.text = "${settings_battery_reset_charge_delta.progress + 1}%"
        settings_battery_reset_charge_delta.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 1
                settings_battery_reset_charge_delta_value.text = "${value}%"
                if (fromUser) {
                    spf.edit().putInt(SpfConfig.GLOBAL_SPF_BATTERY_RESET_CHARGE_DELTA, value).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = (seekBar?.progress ?: 39) + 1
                spf.edit().putInt(SpfConfig.GLOBAL_SPF_BATTERY_RESET_CHARGE_DELTA, value).apply()
            }
        })
        updateBatteryResetThresholdState()
    }

    private fun bindHomeDisplaySwitch(view: Switch, key: String) {
        view.isChecked = spf.getBoolean(key, true)
        view.setOnClickListener {
            spf.edit().putBoolean(key, (it as Switch).isChecked).apply()
        }
    }

    private fun updateBatteryResetThresholdState() {
        val enabled = !settings_battery_reset_on_unplug.isChecked
        settings_battery_reset_level.isEnabled = enabled
        settings_battery_reset_charge_delta.isEnabled = enabled
        settings_battery_reset_level.alpha = if (enabled) 1f else 0.45f
        settings_battery_reset_charge_delta.alpha = if (enabled) 1f else 0.45f
        settings_battery_reset_level_value.alpha = if (enabled) 1f else 0.45f
        settings_battery_reset_charge_delta_value.alpha = if (enabled) 1f else 0.45f
    }

    private fun checkPermission(context: Context, permission: String): Boolean = PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    private fun hasRWPermission(): Boolean {
        return checkPermission(this.applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE)
                &&
                checkPermission(this.applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun onThemeClick(view: View) {
        val tag = view.tag.toString().toInt()
        if (tag == 10 && spf.getInt(SpfConfig.GLOBAL_SPF_THEME, 1) == 10) {
            spf.edit().remove(SpfConfig.GLOBAL_SPF_THEME).apply()
            this.recreate()
        } else {
            if (tag == 10 && !hasRWPermission()) {
                DialogHelper.helpInfo(view.context, "", getString(R.string.wallpaper_rw_permission))
                (view as Switch).isChecked = false
            } else {
                spf.edit().putInt(SpfConfig.GLOBAL_SPF_THEME, tag).apply()
                this.recreate()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()

        spf.edit().putBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, settings_disable_selinux.isChecked).apply()
    }

    public override fun onPause() {
        super.onPause()
    }
}
