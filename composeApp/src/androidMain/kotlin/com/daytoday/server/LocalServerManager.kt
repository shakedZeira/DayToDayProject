package com.daytoday.server

import android.content.Context
import com.daytoday.network.ApiBaseUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServerStatus { Idle, Starting, Ready, Failed }

@Singleton
class LocalServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val termux: TermuxServerController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(ServerStatus.Idle)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private var startJob: Job? = null
    private var stopJob: Job? = null

    fun onAppForeground() {
        stopJob?.cancel()
        if (_status.value == ServerStatus.Ready) return
        if (startJob?.isActive == true) return
        startJob = scope.launch {
            _status.value = ServerStatus.Starting
            // The bundled Termux server only makes sense when the app is talking to
            // this device. A LAN/remote URL must not spawn a local copy.
            val deviceLocal = isDeviceLocalUrl()
            if (deviceLocal) termux.start()
            val deadline = System.currentTimeMillis() + if (deviceLocal) 45_000 else 15_000
            while (System.currentTimeMillis() < deadline) {
                if (isHealthy()) {
                    _status.value = ServerStatus.Ready
                    return@launch
                }
                delay(1000)
            }
            _status.value = ServerStatus.Failed
        }
    }

    fun onAppBackground() {
        startJob?.cancel()
        if (stopJob?.isActive == true) return
        stopJob = scope.launch {
            _status.value = ServerStatus.Idle
            termux.stop()
        }
    }

    private fun isDeviceLocalUrl(): Boolean {
        val url = ApiBaseUrl.current()
        return url.startsWith("http://127.0.0.1") ||
            url.startsWith("http://localhost") ||
            url.startsWith("https://127.0.0.1") ||
            url.startsWith("https://localhost")
    }

    private fun isHealthy(): Boolean = try {
        val conn = (URL(ApiBaseUrl.current().trimEnd('/') + "/api/health").openConnection() as HttpURLConnection).apply {
            connectTimeout = 1500
            readTimeout = 1500
            requestMethod = "GET"
        }
        val ok = conn.responseCode == 200
        conn.disconnect()
        android.util.Log.i("LocalServer", "health check: $ok")
        ok
    } catch (e: Exception) {
        android.util.Log.e("LocalServer", "health check failed", e)
        false
    }
}
