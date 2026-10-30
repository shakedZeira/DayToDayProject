package com.daytoday.di

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.daytoday.data.notification.SummaryNotifications
import com.daytoday.data.notification.SummaryScheduler
import com.daytoday.network.ServerUrlProvider
import com.daytoday.server.LocalServerManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DayTodayApplication : Application() {

    @Inject lateinit var localServer: LocalServerManager
    @Inject lateinit var serverUrlProvider: ServerUrlProvider

    override fun onCreate() {
        super.onCreate()
        // Install the persisted/emulator-aware backend URL before anything can call
        // the API or run the startup health check.
        serverUrlProvider.install()

        // Daily (21:00) / weekly (Saturday 20:00) summary notifications. Idempotent,
        // and re-runs on every process start, so the schedule heals itself after
        // reboots, app updates or force-stops (the boot receiver covers reboots too).
        SummaryNotifications.ensureChannel(this)
        SummaryScheduler.scheduleAll(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                localServer.onAppForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                localServer.onAppBackground()
            }
        })
    }
}
