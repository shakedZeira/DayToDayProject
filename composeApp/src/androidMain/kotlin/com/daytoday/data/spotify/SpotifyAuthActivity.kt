package com.daytoday.data.spotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

object SpotifyAuthCallback {
    private val current = AtomicReference(CompletableDeferred<Uri?>())

    fun next(): CompletableDeferred<Uri?> {
        val deferred = CompletableDeferred<Uri?>()
        current.set(deferred)
        return deferred
    }

    fun get(): CompletableDeferred<Uri?> = current.get()

    fun complete(uri: Uri?) {
        current.get().complete(uri)
    }
}

class SpotifyAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent) {
        val uri = intent.data
        val rejected = uri == null || uri.getQueryParameter("error") != null
        SpotifyAuthCallback.complete(if (rejected) null else uri)
        finish()
    }
}