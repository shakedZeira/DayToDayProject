package com.daytoday.data.notification

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat

/**
 * Foreground service that rings the workout alarm (looping system alarm tone) and vibrates
 * until it receives [ACTION_STOP] or [ACTION_SNOOZE]. Started by [WorkoutAlarmReceiver].
 */
class AlarmRingingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutAlarmNotifications.ensureChannel(this)
        startForeground(WorkoutAlarmNotifications.NOTIFICATION_ID, WorkoutAlarmNotifications.build(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                WorkoutAlarmReceiver.snooze(this)
                shutdown()
                return START_NOT_STICKY
            }
        }
        startRinging()
        return START_STICKY
    }

    private fun startRinging() {
        if (player == null) {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    setDataSource(this@AlarmRingingService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }

        if (vibrator == null) {
            vibrator = runCatching {
                val service = getSystemService(Vibrator::class.java) ?: return@runCatching null
                val pattern = longArrayOf(0, 800, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    service.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    service.vibrate(pattern, 0)
                }
                service
            }.getOrNull()
        }
    }

    private fun shutdown() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        NotificationManagerCompat.from(this).cancel(WorkoutAlarmNotifications.NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.daytoday.alarm.STOP"
        const val ACTION_SNOOZE = "com.daytoday.alarm.SNOOZE"
    }
}
