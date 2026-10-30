package com.daytoday.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daytoday.settings.SettingsManager
import com.daytoday.usecase.LocalSummaryUseCases
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires a daily or weekly summary notification and immediately re-arms the next
 * occurrence of the same kind (exact alarms are one-shot, so the schedule relies
 * on this self re-arm).
 */
@AndroidEntryPoint
class SummaryReceiver : BroadcastReceiver() {

    @Inject lateinit var localSummaryUseCases: LocalSummaryUseCases
    @Inject lateinit var settingsManager: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        val kind = when (intent.getStringExtra(EXTRA_KIND)) {
            SummaryKind.WEEKLY.name -> SummaryKind.WEEKLY
            else -> SummaryKind.DAILY
        }

        // The alarm has fired: clear the armed marker first, otherwise schedule()
        // would treat the just-delivered trigger as an overdue alarm and schedule
        // another catch-up two seconds from now.
        SummaryScheduler.markFired(context, kind)
        // Re-arm first so the next occurrence exists even if posting fails.
        SummaryScheduler.schedule(context, kind)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SummaryNotifications.ensureChannel(context)
                SummaryNotifications.postIfNeeded(
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
        const val ACTION_DAILY = "com.daytoday.action.SUMMARY_DAILY"
        const val ACTION_WEEKLY = "com.daytoday.action.SUMMARY_WEEKLY"

        fun actionFor(kind: SummaryKind): String = when (kind) {
            SummaryKind.DAILY -> ACTION_DAILY
            SummaryKind.WEEKLY -> ACTION_WEEKLY
        }
    }
}
