package com.redpacketalert.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.redpacketalert.R
import com.redpacketalert.RedAlert
import com.redpacketalert.data.SettingsStore
import com.redpacketalert.databinding.ActivityMainBinding
import com.redpacketalert.util.Permissions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var syncing = false

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val uri = pickedRingtoneUri(result.data)
            SettingsStore.update(this) { it.putString(SettingsStore.Keys.SOUND_URI, uri?.toString()) }
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        bindSwitches()
        bindSeekBars()
        bindActions()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    // ---------------------------------------------------------------- 开关

    private fun bindSwitches() {
        bindSwitch(binding.swEnabled, SettingsStore.Keys.ENABLED) { enabled ->
            if (enabled && !Permissions.hasNotificationAccess(this)) {
                toast(getString(R.string.toast_need_notification_access))
                Permissions.openNotificationAccessSettings(this)
            }
        }
        bindSwitch(binding.swSound, SettingsStore.Keys.SOUND_ENABLED)
        bindSwitch(binding.swAlarmStream, SettingsStore.Keys.USE_ALARM_STREAM)
        bindSwitch(binding.swVibrate, SettingsStore.Keys.VIBRATE_ENABLED)
        bindSwitch(binding.swVibrateContinuous, SettingsStore.Keys.VIBRATE_CONTINUOUS)
        bindSwitch(binding.swTorch, SettingsStore.Keys.TORCH_ENABLED)
        bindSwitch(binding.swOverlayAlert, SettingsStore.Keys.OVERLAY_ENABLED) { enabled ->
            if (enabled && !Permissions.canDrawOverlays(this)) {
                toast(getString(R.string.toast_need_overlay))
                Permissions.openOverlaySettings(this)
            }
        }
        bindSwitch(binding.swScreenOn, SettingsStore.Keys.SCREEN_ON_ENABLED)
        bindSwitch(binding.swIgnoreOngoing, SettingsStore.Keys.IGNORE_ONGOING)
        bindSwitch(binding.swIgnoreSuspended, SettingsStore.Keys.IGNORE_SUSPENDED)
        bindSwitch(binding.swWhitelist, SettingsStore.Keys.WHITELIST_ENABLED)
        bindSwitch(binding.swMatchSender, SettingsStore.Keys.MATCH_SENDER)
        bindSwitch(binding.swAlertUnknown, SettingsStore.Keys.ALERT_UNKNOWN_GROUP)
    }

    private fun bindSwitch(view: MaterialSwitch, key: String, onUserChange: ((Boolean) -> Unit)? = null) {
        view.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            SettingsStore.update(this) { it.putBoolean(key, checked) }
            onUserChange?.invoke(checked)
        }
    }

    // ---------------------------------------------------------------- 拖动条

    private fun bindSeekBars() {
        binding.seekDuration.max = MAX_DURATION_SECONDS - MIN_DURATION_SECONDS
        binding.seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvDurationLabel.text =
                    getString(R.string.label_duration_value, MIN_DURATION_SECONDS + progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val seconds = MIN_DURATION_SECONDS + (seekBar?.progress ?: 0)
                SettingsStore.update(this@MainActivity) {
                    it.putInt(SettingsStore.Keys.ALERT_DURATION_SECONDS, seconds)
                }
            }
        })

        binding.seekInterval.max = MAX_INTERVAL_SECONDS
        binding.seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvIntervalLabel.text = getString(R.string.label_interval_value, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val seconds = seekBar?.progress ?: 0
                SettingsStore.update(this@MainActivity) {
                    it.putLong(SettingsStore.Keys.MIN_INTERVAL_MS, seconds * 1000L)
                }
            }
        })
    }

    // ---------------------------------------------------------------- 点击项

    private fun bindActions() {
        binding.btnNotifAccess.setOnClickListener { Permissions.openNotificationAccessSettings(this) }
        binding.btnOverlay.setOnClickListener { Permissions.openOverlaySettings(this) }
        binding.btnBattery.setOnClickListener { Permissions.requestIgnoreBatteryOptimizations(this) }
        binding.rowListener.setOnClickListener { Permissions.openNotificationAccessSettings(this) }
        binding.rowWeChat.setOnClickListener {
            if (!Permissions.openApp(this, WECHAT_PACKAGE)) {
                toast(getString(R.string.toast_wechat_missing))
            }
        }

        binding.btnPickTone.setOnClickListener {
            try {
                ringtonePicker.launch(buildRingtoneIntent())
            } catch (throwable: Throwable) {
                toast(getString(R.string.toast_no_ringtone_picker))
            }
        }

        binding.rowKeywords.setOnClickListener {
            openEditor(
                SettingsStore.Keys.KEYWORDS,
                R.string.editor_keywords,
                R.string.editor_keywords_hint
            )
        }
        binding.rowExcludes.setOnClickListener {
            openEditor(
                SettingsStore.Keys.EXCLUDE_KEYWORDS,
                R.string.editor_excludes,
                R.string.editor_excludes_hint
            )
        }
        binding.rowWhitelist.setOnClickListener {
            openEditor(
                SettingsStore.Keys.WHITELIST,
                R.string.editor_whitelist,
                R.string.editor_whitelist_hint
            )
        }
        binding.rowMute.setOnClickListener {
            openEditor(
                SettingsStore.Keys.MUTE_LIST,
                R.string.editor_mute,
                R.string.editor_mute_hint
            )
        }
        binding.rowPackages.setOnClickListener {
            openEditor(
                SettingsStore.Keys.MONITORED_PACKAGES,
                R.string.editor_packages,
                R.string.editor_packages_hint
            )
        }
        binding.rowLog.setOnClickListener {
            startActivity(Intent(this, EventLogActivity::class.java))
        }

        binding.btnTest.setOnClickListener {
            val settings = SettingsStore.settings(this)
            if (!settings.soundEnabled && !settings.vibrateEnabled &&
                !settings.torchEnabled && !settings.overlayEnabled
            ) {
                toast(getString(R.string.toast_no_channel))
                return@setOnClickListener
            }
            RedAlert.triggerTest(this)
            toast(getString(R.string.toast_test_triggered))
        }
    }

    private fun openEditor(key: String, titleRes: Int, hintRes: Int) {
        startActivity(
            ListEditorActivity.intent(this, key, getString(titleRes), getString(hintRes))
        )
    }

    // ---------------------------------------------------------------- 刷新

    private fun refreshUi() {
        syncing = true
        val settings = SettingsStore.settings(this)

        binding.swEnabled.isChecked = settings.enabled
        binding.swSound.isChecked = settings.soundEnabled
        binding.swAlarmStream.isChecked = settings.useAlarmStream
        binding.swVibrate.isChecked = settings.vibrateEnabled
        binding.swVibrateContinuous.isChecked = settings.vibrateContinuous
        binding.swTorch.isChecked = settings.torchEnabled
        binding.swOverlayAlert.isChecked = settings.overlayEnabled
        binding.swScreenOn.isChecked = settings.screenOnEnabled
        binding.swIgnoreOngoing.isChecked = settings.ignoreOngoing
        binding.swIgnoreSuspended.isChecked = settings.ignoreSuspended
        binding.swWhitelist.isChecked = settings.whitelistEnabled
        binding.swMatchSender.isChecked = settings.matchSenderName
        binding.swAlertUnknown.isChecked = settings.alertWhenGroupUnknown

        val duration = settings.alertDurationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        binding.seekDuration.progress = duration - MIN_DURATION_SECONDS
        binding.tvDurationLabel.text = getString(R.string.label_duration_value, duration)

        val interval = (settings.minAlertIntervalMs / 1000L).toInt().coerceIn(0, MAX_INTERVAL_SECONDS)
        binding.seekInterval.progress = interval
        binding.tvIntervalLabel.text = getString(R.string.label_interval_value, interval)

        refreshStatus(settings.enabled)
        binding.tvToneLabel.text = ringtoneLabel()
        syncing = false
    }

    private fun refreshStatus(enabled: Boolean) {
        val notificationAccess = Permissions.hasNotificationAccess(this)
        binding.statusNotifAccess.text = statusText(notificationAccess)
        binding.statusNotifAccess.setTextColor(statusColor(notificationAccess))
        binding.btnNotifAccess.text =
            getString(if (notificationAccess) R.string.action_view else R.string.action_open)

        val overlay = Permissions.canDrawOverlays(this)
        binding.statusOverlay.text = statusText(overlay)
        binding.statusOverlay.setTextColor(statusColor(overlay))
        binding.btnOverlay.text =
            getString(if (overlay) R.string.action_view else R.string.action_open)

        val battery = Permissions.isIgnoringBatteryOptimizations(this)
        binding.statusBattery.text = getString(
            if (battery) R.string.status_battery_ok else R.string.status_battery_bad
        )
        binding.statusBattery.setTextColor(statusColor(battery))

        val wechat = Permissions.isAppInstalled(this, WECHAT_PACKAGE)
        binding.statusWeChat.text = getString(
            if (wechat) R.string.status_wechat_ok else R.string.status_wechat_missing
        )
        binding.statusWeChat.setTextColor(statusColor(wechat))

        val connected = RedAlert.listenerConnected && notificationAccess
        binding.statusListener.text = getString(
            if (connected) R.string.status_listener_connected else R.string.status_listener_disconnected
        )
        binding.statusListener.setTextColor(statusColor(connected))

        binding.tvMasterHint.text = getString(
            if (enabled) R.string.master_hint_on else R.string.master_hint_off
        )
    }

    private fun statusText(granted: Boolean): String =
        getString(if (granted) R.string.status_granted else R.string.status_not_granted)

    private fun statusColor(granted: Boolean): Int =
        getColor(if (granted) R.color.status_ok else R.color.status_warn)

    private fun ringtoneLabel(): String {
        val uriString = SettingsStore.settings(this).soundUri
        if (uriString.isNullOrBlank()) return getString(R.string.ringtone_default)
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(uriString))?.getTitle(this) ?: uriString
        } catch (throwable: Throwable) {
            uriString
        }
    }

    private fun buildRingtoneIntent(): Intent {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_ringtone))
        SettingsStore.settings(this).soundUri?.let {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
        }
        return intent
    }

    @Suppress("DEPRECATION")
    private fun pickedRingtoneUri(data: Intent?): Uri? {
        if (data == null) return null
        val extra = RingtoneManager.EXTRA_RINGTONE_PICKED_URI
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(extra, Uri::class.java)
        } else {
            data.getParcelableExtra(extra) as? Uri
        }
    }

    private fun toast(message: CharSequence) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val MIN_DURATION_SECONDS = 3
        const val MAX_DURATION_SECONDS = 60
        const val MAX_INTERVAL_SECONDS = 10
    }
}
