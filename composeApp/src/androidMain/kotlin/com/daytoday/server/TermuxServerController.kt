package com.daytoday.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxServerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val serviceComponent = ComponentName("com.termux", "com.termux.app.RunCommandService")

    fun start() = runScript("/data/data/com.termux/files/home/run.sh")
    fun stop() = runScript("/data/data/com.termux/files/home/stop.sh")

    private fun runScript(script: String) {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            component = serviceComponent
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // Best effort: background service-start limits may reject this.
        }
    }
}
