package com.daytoday.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the daily/weekly summary alarms after a reboot, app update or clock change. */
class SummaryBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                SummaryNotifications.ensureChannel(context)
                SummaryScheduler.scheduleAll(context)
            }
        }
    }
}
