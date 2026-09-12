package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView that maintains persistent rendering and execution state
 * for YouTube Iframe and Web Audio even when the host Activity loses window focus,
 * is minimized, or the device screen is locked.
 */
class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // ALWAYS signal VISIBLE to Chromium's internal compositor so background
        // audio and video players are never paused or throttled by Android window visibility changes.
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        // Always report window focus as true so media session and audio timers stay active
        super.onWindowFocusChanged(true)
    }

    override fun hasWindowFocus(): Boolean {
        return true
    }

    fun playPause() {
        post {
            evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.__soundwavePlayPause) {
                            window.__soundwavePlayPause();
                            return;
                        }
                        var btn = document.querySelector('[data-testid="deck-mobile-play-button"]') ||
                                  document.querySelector('.transport-play') ||
                                  document.querySelector('[data-testid="deck-play-button"]') ||
                                  document.querySelector('button[aria-label="Play"], button[aria-label="Pause"]');
                        if (btn) btn.click();
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun nextTrack() {
        post {
            evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.__soundwaveNext) {
                            window.__soundwaveNext();
                            return;
                        }
                        var btn = document.querySelector('[data-testid="deck-mobile-next-button"]') ||
                                  document.querySelector('[data-testid$="next-button"]') ||
                                  document.querySelector('button[aria-label="Next"]');
                        if (btn) btn.click();
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun previousTrack() {
        post {
            evaluateJavascript(
                """
                (function() {
                    try {
                        if (window.__soundwavePrevious) {
                            window.__soundwavePrevious();
                            return;
                        }
                        var btn = document.querySelector('[data-testid$="previous-button"]') ||
                                  document.querySelector('button[aria-label="Previous"]');
                        if (btn) btn.click();
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }
}
