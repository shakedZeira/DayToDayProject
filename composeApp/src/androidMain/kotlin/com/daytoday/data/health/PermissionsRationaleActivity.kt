package com.daytoday.data.health

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView

/**
 * Health Connect sends ACTION_SHOW_PERMISSIONS_RATIONALE to this Activity, so it must be
 * exported (see AndroidManifest.xml). Explains why step data is read.
 */
class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply {
            text = RATIONALE
            textSize = 16f
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        setContentView(
            text,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private companion object {
        const val RATIONALE =
            "DayToDay reads your step count from Health Connect to show daily activity " +
                "progress alongside your workouts. Your health data never leaves this device."
    }
}
