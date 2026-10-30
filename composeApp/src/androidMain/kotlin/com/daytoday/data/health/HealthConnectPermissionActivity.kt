package com.daytoday.data.health

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

/**
 * Transparent Activity that hosts the Health Connect permission contract and reports
 * the outcome back through [HealthConnectPermissionRequest].
 */
class HealthConnectPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val readSteps = HealthPermission.getReadPermission(StepsRecord::class)
        HealthConnectPermissionRequest.complete(granted.contains(readSteps))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launchPermission()
        }
    }

    private fun launchPermission() {
        // The background permission is what lets the summary BroadcastReceiver read
        // steps when the app is not in the foreground; it is requested alongside the
        // foreground read but is NOT required for the in-app `hasSteps` check.
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        )
        runCatching { launcher.launch(permissions) }
            .onFailure {
                Log.e(TAG, "launcher.launch failed", it)
                // Complete the *current* request's deferred, not a stale one.
                HealthConnectPermissionRequest.complete(false)
                finish()
            }
    }

    private companion object {
        const val TAG = "DayToDayHC"
    }
}
