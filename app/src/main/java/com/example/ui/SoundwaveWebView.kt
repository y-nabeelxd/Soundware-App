package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.media.SoundwaveMediaBridge

object SoundwaveWebViewHelper {

    const val TARGET_URL = "https://musics.luffyxd.store/"

    // Injected JavaScript that:
    // 1. Prevents YouTube Iframe and Web Audio from suspending when minimized/backgrounded
    // 2. Extracts currently playing track metadata (title, artist, artwork) & playback state
    // 3. Reports state to AndroidMediaBridge so the notification and lockscreen update in real-time
    // 4. Exposes direct hooks (playPause, next, previous) for native notification actions
    const val INJECTED_MEDIA_BRIDGE_JS = """
        (function() {
            try {
                // Prevent background throttling of timers and visibility
                Object.defineProperty(document, 'hidden', {
                    get: function() { return false; },
                    configurable: true
                });
                Object.defineProperty(document, 'visibilityState', {
                    get: function() { return 'visible'; },
                    configurable: true
                });

                window.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);
                document.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);

                // Global control functions called by native Android media notification
                window.__soundwavePlayPause = function() {
                    var btn = document.querySelector('[data-testid="deck-mobile-play-button"]') ||
                              document.querySelector('.transport-play') ||
                              document.querySelector('[data-testid$="play-button"]') ||
                              document.querySelector('button[aria-label="Pause"], button[aria-label="Play"]');
                    if (btn) {
                        btn.click();
                        setTimeout(sendMediaUpdate, 150);
                    }
                };

                window.__soundwaveNext = function() {
                    var btn = document.querySelector('[data-testid="deck-mobile-next-button"]') ||
                              document.querySelector('[data-testid$="next-button"]') ||
                              document.querySelector('button[aria-label="Next"]');
                    if (btn) {
                        btn.click();
                        setTimeout(sendMediaUpdate, 350);
                    }
                };

                window.__soundwavePrevious = function() {
                    var btn = document.querySelector('[data-testid$="previous-button"]') ||
                              document.querySelector('button[aria-label="Previous"]');
                    if (btn) {
                        btn.click();
                        setTimeout(sendMediaUpdate, 350);
                    }
                };

                var lastTitle = '';
                var lastArtist = '';
                var lastArt = '';
                var lastPlaying = null;

                function sendMediaUpdate() {
                    try {
                        var titleElem = document.querySelector('[data-testid="deck-track-title"]') ||
                                        document.querySelector('[data-testid="player-modal-title"]') ||
                                        document.querySelector('.deck-track strong');

                        var artistElem = document.querySelector('[data-testid="deck-track-artist"]') ||
                                         document.querySelector('[data-testid="player-modal-artist"]') ||
                                         document.querySelector('.deck-track small');

                        var imgElem = document.querySelector('[data-testid="deck-track-image"] img') ||
                                      document.querySelector('[data-testid="deck-track-image"]') ||
                                      document.querySelector('.deck-art img');

                        var playBtn = document.querySelector('[data-testid="deck-mobile-play-button"]') ||
                                      document.querySelector('.transport-play') ||
                                      document.querySelector('[data-testid$="play-button"]');

                        var title = titleElem ? (titleElem.innerText || titleElem.textContent || '').trim() : '';
                        var artist = artistElem ? (artistElem.innerText || artistElem.textContent || '').trim() : '';
                        
                        var artwork = '';
                        if (imgElem) {
                            artwork = imgElem.getAttribute('src') || imgElem.currentSrc || imgElem.src || '';
                        }

                        var isPlaying = false;
                        if (playBtn) {
                            var ariaLabel = (playBtn.getAttribute('aria-label') || '').toLowerCase();
                            isPlaying = (ariaLabel === 'pause') || playBtn.classList.contains('is-playing');
                        }

                        // Also verify if any video/audio element is playing in the DOM
                        var mediaElements = document.querySelectorAll('video, audio');
                        for (var i = 0; i < mediaElements.length; i++) {
                            var m = mediaElements[i];
                            if (!m.paused && m.currentTime > 0) {
                                isPlaying = true;
                                break;
                            }
                        }

                        if (title !== lastTitle || artist !== lastArtist || artwork !== lastArt || isPlaying !== lastPlaying) {
                            lastTitle = title;
                            lastArtist = artist;
                            lastArt = artwork;
                            lastPlaying = isPlaying;

                            if (window.AndroidMediaBridge) {
                                window.AndroidMediaBridge.onTrackUpdate(title, artist, artwork, isPlaying);
                            }
                        }
                    } catch(err) {}
                }

                // Poll regularly and observe DOM changes
                if (!window.__soundwaveWatcherStarted) {
                    window.__soundwaveWatcherStarted = true;
                    setInterval(sendMediaUpdate, 800);

                    var observer = new MutationObserver(function() {
                        sendMediaUpdate();
                    });
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true, attributes: true });
                    }
                }

                sendMediaUpdate();
            } catch (e) {}
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(
        webView: KeepAliveWebView,
        onProgressChanged: (Int) -> Unit,
        onPageLoaded: () -> Unit,
        onReceivedError: (isFatal: Boolean) -> Unit
    ) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true

            // Clean modern Chrome mobile user agent to remove '; wv' (WebView identifier)
            val rawUa = userAgentString ?: ""
            userAgentString = rawUa.replace("; wv", "").replace("Version/4.0 ", "")
        }

        // Add JavaScript bridge for Android MediaSession communication
        webView.addJavascriptInterface(
            SoundwaveMediaBridge(webView.context.applicationContext),
            "AndroidMediaBridge"
        )

        // Cookie configuration
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged(newProgress)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    try {
                        it.grant(it.resources)
                    } catch (e: Exception) {
                        super.onPermissionRequest(request)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectMediaBridge(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectMediaBridge(view)
                onPageLoaded()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: ""
                return if (scheme == "http" || scheme == "https") {
                    false // Keep inside WebView
                } else {
                    true
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                onReceivedError(true)
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    onReceivedError(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (failingUrl == null || failingUrl == TARGET_URL || failingUrl.startsWith(TARGET_URL)) {
                    onReceivedError(true)
                }
            }
        }
    }

    fun injectMediaBridge(webView: WebView?) {
        webView?.evaluateJavascript(INJECTED_MEDIA_BRIDGE_JS, null)
    }
}
