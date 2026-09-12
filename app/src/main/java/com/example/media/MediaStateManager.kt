package com.example.media

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class TrackInfo(
    val title: String = "Soundwave",
    val artist: String = "Find the feeling between the notes",
    val artworkUrl: String = "",
    val artworkBitmap: Bitmap? = null,
    val isPlaying: Boolean = false
)

object MediaStateManager {

    private val _trackState = MutableStateFlow(TrackInfo())
    val trackState: StateFlow<TrackInfo> = _trackState.asStateFlow()

    private var actionListener: MediaActionListener? = null

    interface MediaActionListener {
        fun onPlayPause()
        fun onNext()
        fun onPrevious()
    }

    fun setActionListener(listener: MediaActionListener?) {
        this.actionListener = listener
    }

    fun updateTrack(
        title: String,
        artist: String,
        artworkUrl: String,
        isPlaying: Boolean,
        bitmap: Bitmap? = null
    ) {
        val cleanTitle = if (title.isBlank() || title == "Untitled track") "Soundwave" else title
        val cleanArtist = if (artist.isBlank() || artist == "Unknown artist") "Now Playing" else artist

        val current = _trackState.value
        val bitmapToUse = bitmap ?: if (artworkUrl == current.artworkUrl) current.artworkBitmap else null

        _trackState.value = TrackInfo(
            title = cleanTitle,
            artist = cleanArtist,
            artworkUrl = artworkUrl,
            artworkBitmap = bitmapToUse,
            isPlaying = isPlaying
        )
    }

    fun updateArtworkBitmap(bitmap: Bitmap?) {
        val current = _trackState.value
        _trackState.value = current.copy(artworkBitmap = bitmap)
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        val current = _trackState.value
        if (current.isPlaying != isPlaying) {
            _trackState.value = current.copy(isPlaying = isPlaying)
        }
    }

    fun triggerPlayPause() {
        actionListener?.onPlayPause()
    }

    fun triggerNext() {
        actionListener?.onNext()
    }

    fun triggerPrevious() {
        actionListener?.onPrevious()
    }
}
