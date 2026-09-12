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

object SoundwaveWebViewHelper {

    const val TARGET_URL = "https://musics.luffyxd.store/"

    // Injected JavaScript that prevents YouTube Iframe and HTML5 audio from
    // pausing when the app/WebView is obscured, minimized, or the screen locks.
    const val KEEP_ALIVE_JS = """
        (function() {
            try {
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
            } catch (e) {}
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(
        webView: WebView,
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
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true

            // Clean modern Chrome mobile user agent to remove '; wv' (WebView identifier)
            // which YouTube Iframe API sometimes checks to restrict background playback
            val rawUa = userAgentString ?: ""
            userAgentString = rawUa.replace("; wv", "").replace("Version/4.0 ", "")
        }

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
                injectKeepAlive(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectKeepAlive(view)
                onPageLoaded()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val scheme = request.url.scheme?.lowercase() ?: ""
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
                // Ensure streaming playback isn't halted by intermediate SSL chain notices
                handler?.proceed()
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // Gracefully handle render process termination without crashing the host app
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
                onReceivedError(true)
            }
        }
    }

    fun injectKeepAlive(webView: WebView?) {
        webView?.evaluateJavascript(KEEP_ALIVE_JS, null)
    }
}
