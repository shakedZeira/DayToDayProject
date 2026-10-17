package com.daytoday.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daytoday.BuildConfig
import com.daytoday.settings.SettingsManager
import com.daytoday.usecase.LocalSummaryUseCases
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY test hook: posts a summary notification immediately instead of
 * waiting until 21:00 (today) or Saturday 20:00 (week).
 *
 * Registered only in `src/debug/AndroidManifest.xml`, so it never ships in a
 * release build; additionally guarded by [BuildConfig.DEBUG]. It bypasses the
 * in-app notifications toggle (but not the OS permission), so it can be used to
 * verify the notification while testing.
 *
 * Usage:
 * ```
 * adb shell am broadcast \
 *   -n com.daytoday/com.daytoday.data.notification.SummaryDebugReceiver \
 *   -a com.daytoday.DEBUG_FIRE_SUMMARY --es kind daily
 * # ...or --es kind weekly
 * ```
 */
@AndroidEntryPoint
class SummaryDebugReceiver : BroadcastReceiver() {

    @Inject lateinit var localSummaryUseCases: LocalSummaryUseCases
    @Inject lateinit var settingsManager: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val kind = when (intent.getStringExtra(EXTRA_KIND)) {
            SummaryKind.WEEKLY.name -> SummaryKind.WEEKLY
            else -> SummaryKind.DAILY
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SummaryNotifications.ensureChannel(context)
                SummaryNotifications.post(
                    context = context,
                    kind = kind,
                    localSummaryUseCases = localSummaryUseCases,
                    settingsManager = settingsManager,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val ACTION = "com.daytoday.DEBUG_FIRE_SUMMARY"
    }
}
