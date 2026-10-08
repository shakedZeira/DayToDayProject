package com.daytoday.data.spotify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SpotifyAuthActivity : ComponentActivity() {

    @Inject
    lateinit var spotifyRepository: SpotifyRepository

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent) {
        if (handled) return
        handled = true
        val uri = intent.data
        if (uri != null) {
            spotifyRepository.handleLoginCallback(uri)
        }
        finish()
    }
}