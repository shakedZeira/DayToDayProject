package com.daytoday.ui.screen.settings

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daytoday.ui.theme.DayTodayCard
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Where other people can download the app. Point this at the page that hosts the APK. */
const val APP_DOWNLOAD_URL = "https://github.com/shakedZeira/DayToDayProject/releases/latest"

/** QR code that opens [APP_DOWNLOAD_URL], plus a button to share the same link. */
@Composable
fun ShareAppCard(url: String = APP_DOWNLOAD_URL) {
    val context = LocalContext.current
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QrCode(content = url, modifier = Modifier.size(200.dp))
            Text(
                text = "Scan to download DayToDay",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Get DayToDay: $url")
                    }
                    context.startActivity(Intent.createChooser(send, "Share DayToDay"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share link")
            }
        }
    }
}

/** Draws [content] as a QR code: dark modules on a white plate, so it scans in dark theme too. */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
    }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(8.dp),
    ) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f), // overlap a hair so cells never leave seams
                    )
                }
            }
        }
    }
}
