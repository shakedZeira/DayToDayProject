package com.daytoday.ui.screen.pdf

import android.net.Uri

data class UriFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val fileId: String
)