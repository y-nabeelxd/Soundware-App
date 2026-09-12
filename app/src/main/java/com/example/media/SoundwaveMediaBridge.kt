package com.example.media

import android.content.Context
import android.graphics.Bitmap
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
        isPlaying: Boolean
    ) {
        val safeTitle = title?.trim().orEmpty()
        val safeArtist = artist?.trim().orEmpty()
        val safeArtworkUrl = artworkUrl?.trim().orEmpty()

        MediaStateManager.updateTrack(
            title = safeTitle,
            artist = safeArtist,
            artworkUrl = safeArtworkUrl,
            isPlaying = isPlaying
        )

        if (safeArtworkUrl.isNotBlank() && safeArtworkUrl != lastLoadedArtworkUrl) {
            lastLoadedArtworkUrl = safeArtworkUrl
            loadArtwork(safeArtworkUrl)
        }
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
                // Ignore failure and keep fallback icon
            }
        }
    }
}
