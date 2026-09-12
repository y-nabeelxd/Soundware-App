package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Custom WebView that:
 * 1. Maintains persistent rendering and execution state for audio in background
 * 2. Fully supports Android soft keyboard (IME) input for search, playlist editing, and inputs
 * 3. Bridges media control operations (play, pause, next, prev, seek)
 */
class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        // Crucial for software keyboard input inside WebView
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean {
        // Informs Android InputMethodManager that this view accepts text input
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!hasFocus()) {
                requestFocus()
            }
        }
        return super.onTouchEvent(event)
    }

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
        super.onWindowFocusChanged(true)
    }

    override fun hasWindowFocus(): Boolean {
        return true
    }

    fun showKeyboard() {
        post {
            requestFocus(View.FOCUS_DOWN)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
        }
    }

    fun playPause() {
        post {
            evaluateJavascript(
                """
                (function() {
                    try {
                        if (typeof window.__soundwavePlayPause === 'function') {
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
                        if (typeof window.__soundwaveNext === 'function') {
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
                        if (typeof window.__soundwavePrevious === 'function') {
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

    fun seekTo(seconds: Float) {
        post {
            evaluateJavascript(
                """
                (function() {
                    try {
                        if (typeof window.__soundwaveSeek === 'function') {
                            window.__soundwaveSeek($seconds);
                        }
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }
}
