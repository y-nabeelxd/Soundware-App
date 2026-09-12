package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.media.MediaStateManager
import com.example.network.NetworkMonitor
import com.example.service.BackgroundAudioService
import com.example.ui.KeepAliveWebView
import com.example.ui.OfflineScreen
import com.example.ui.SplashScreen
import com.example.ui.SoundwaveWebViewHelper
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SoundwaveDark
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private var webViewInstance: KeepAliveWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure system StatusBar explicitly:
        // Ensures status bar is enabled, clearly visible, with high-contrast light icons on dark theme
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // White text/icons on dark background
        windowInsetsController.isAppearanceLightNavigationBars = false
        windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())

        // Connect MediaStateManager actions (from notification / lockscreen) to WebView
        MediaStateManager.setActionListener(object : MediaStateManager.MediaActionListener {
            override fun onPlayPause() {
                webViewInstance?.playPause()
            }

            override fun onNext() {
                webViewInstance?.nextTrack()
            }

            override fun onPrevious() {
                webViewInstance?.previousTrack()
            }

            override fun onSeekTo(posMs: Long) {
                webViewInstance?.seekTo(posMs / 1000f)
            }
        })

        // Start native hardware network watchdog
        networkMonitor = NetworkMonitor(this).apply {
            startMonitoring()
        }

        // Start background media retention service
        BackgroundAudioService.startService(this)

        setContent {
            MyApplicationTheme {
                SoundwaveApp(
                    networkMonitor = networkMonitor,
                    onGetWebView = { webViewInstance },
                    onSetWebView = { webViewInstance = it },
                    onExitApp = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        webViewInstance?.let { webView ->
            webView.resumeTimers()
            SoundwaveWebViewHelper.injectMediaBridge(webView)
        }
    }

    override fun onStop() {
        super.onStop()
        webViewInstance?.let { webView ->
            webView.resumeTimers()
            SoundwaveWebViewHelper.injectMediaBridge(webView)
        }
    }

    override fun onResume() {
        super.onResume()
        webViewInstance?.let { webView ->
            webView.resumeTimers()
            SoundwaveWebViewHelper.injectMediaBridge(webView)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        webViewInstance?.let { webView ->
            webView.resumeTimers()
            SoundwaveWebViewHelper.injectMediaBridge(webView)
        }
    }

    override fun onDestroy() {
        MediaStateManager.setActionListener(null)
        networkMonitor.stopMonitoring()
        if (isFinishing) {
            // When app is closed by the user, terminate all background services, wake locks, and notifications
            BackgroundAudioService.stopService(this)
            try {
                webViewInstance?.destroy()
            } catch (ignored: Exception) {}
            webViewInstance = null
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundwaveApp(
    networkMonitor: NetworkMonitor,
    onGetWebView: () -> KeepAliveWebView?,
    onSetWebView: (KeepAliveWebView) -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val isOnline by networkMonitor.isOnline.collectAsState()
    val isImeOpen = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var isPageLoaded by remember { mutableStateOf(false) }
    var splashProgress by remember { mutableFloatStateOf(0.1f) }
    var isSplashFinished by remember { mutableStateOf(false) }
    var hasFatalWebError by remember { mutableStateOf(false) }
    var wasOfflineBefore by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Request notification permission on Android 13+ for foreground notification
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Minimum splash screen display time for smooth visual onboarding
    LaunchedEffect(isPageLoaded) {
        if (isPageLoaded) {
            splashProgress = 1.0f
            delay(400)
            isSplashFinished = true
        } else {
            // Safety timeout: transition after 3.5 seconds even if page is slow,
            // so user is not stuck on splash screen indefinitely
            delay(3500)
            if (!isSplashFinished) {
                splashProgress = 1.0f
                isSplashFinished = true
            }
        }
    }

    // Live Instant Network Watchdog:
    // Tracks network drops and automatically refreshes website when reconnected
    LaunchedEffect(isOnline) {
        if (!isOnline) {
            wasOfflineBefore = true
        } else {
            // Network restored! Refresh website immediately without showing error pages
            if (wasOfflineBefore || hasFatalWebError) {
                hasFatalWebError = false
                wasOfflineBefore = false
                val webView = onGetWebView()
                webView?.loadUrl(SoundwaveWebViewHelper.TARGET_URL)
            }
        }
    }

    // Back Navigation Handler:
    // 1. If keyboard is open -> hide keyboard first (standard Android UX)
    // 2. If webView can go back -> go back in web history
    // 3. If on root screen -> double press to exit
    BackHandler(enabled = isSplashFinished) {
        val webView = onGetWebView()
        if (isImeOpen) {
            webView?.hideKeyboard()
            keyboardController?.hide()
            focusManager.clearFocus()
            return@BackHandler
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000L) {
                onExitApp()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(
                    context,
                    context.getString(R.string.exit_prompt),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoundwaveDark)
            .statusBarsPadding()
    ) {
        // Main KeepAliveWebView Layer (keeps rendering and audio active continuously)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val webView = KeepAliveWebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                SoundwaveWebViewHelper.configureWebView(
                    webView = webView,
                    onProgressChanged = { progress ->
                        if (!isPageLoaded) {
                            val normalized = (progress / 100f).coerceIn(0.1f, 1f)
                            if (normalized > splashProgress) {
                                splashProgress = normalized
                            }
                        }
                    },
                    onPageLoaded = {
                        isPageLoaded = true
                        hasFatalWebError = false
                    },
                    onReceivedError = { isFatal ->
                        if (isFatal) {
                            hasFatalWebError = true
                            networkMonitor.notifyNetworkError()
                        }
                    }
                )

                onSetWebView(webView)
                webView.loadUrl(SoundwaveWebViewHelper.TARGET_URL)
                webView
            },
            update = {
                // Continuously maintained active
            }
        )

        // Instant Network Watchdog Offline Screen
        // Detects disconnection immediately like YouTube and Spotify,
        // and hides the webview so Chromium's net::ERR_INTERNET_DISCONNECTED is never visible
        AnimatedVisibility(
            visible = !isOnline || hasFatalWebError,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            OfflineScreen(
                onRetryClick = {
                    val webView = onGetWebView()
                    hasFatalWebError = false
                    if (webView != null) {
                        webView.loadUrl(SoundwaveWebViewHelper.TARGET_URL)
                    }
                }
            )
        }

        // Splash Screen Layer with Smooth Transition to Main Webview
        AnimatedVisibility(
            visible = !isSplashFinished,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            SplashScreen(progress = splashProgress)
        }
    }
}
