package com.example.media

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.webkit.JavascriptInterface
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SoundwaveMediaBridge(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val imageLoader = ImageLoader(context)
    private var lastLoadedArtworkUrl = ""

    @JavascriptInterface
    fun onTrackUpdate(
        title: String?,
        artist: String?,
        artworkUrl: String?,
        isPlaying: Boolean,
        positionSec: Double,
        durationSec: Double
    ) {
        val safeTitle = title?.trim().orEmpty()
        val safeArtist = artist?.trim().orEmpty()
        val safeArtworkUrl = artworkUrl?.trim().orEmpty()
        val posMs = (positionSec * 1000.0).toLong().coerceAtLeast(0L)
        val durMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L)

        MediaStateManager.updateTrack(
            title = safeTitle,
            artist = safeArtist,
            artworkUrl = safeArtworkUrl,
            isPlaying = isPlaying,
            positionMs = posMs,
            durationMs = durMs
        )

        if (safeArtworkUrl.isNotBlank() && safeArtworkUrl != lastLoadedArtworkUrl) {
            lastLoadedArtworkUrl = safeArtworkUrl
            loadArtwork(safeArtworkUrl)
        }
    }

    @JavascriptInterface
    fun onProgressUpdate(positionSec: Double, durationSec: Double) {
        val posMs = (positionSec * 1000.0).toLong().coerceAtLeast(0L)
        val durMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L)
        MediaStateManager.updateProgress(posMs, durMs)
    }

    @JavascriptInterface
    fun onPlaybackChanged(isPlaying: Boolean) {
        MediaStateManager.updatePlaybackState(isPlaying)
    }

    private fun loadArtwork(url: String) {
        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false) // Must be software bitmap for RemoteViews / Notification LargeIcon
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        MediaStateManager.updateArtworkBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                // Keep fallback icon if remote artwork fails
            }
        }
    }
}
