package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
        // CRITICAL FOR AUDIO RETENTION:
        // DO NOT call webViewInstance?.onPause()
        // Keep timers running and ensure keep-alive JS is active
        webViewInstance?.let { webView ->
            webView.resumeTimers()
            SoundwaveWebViewHelper.injectMediaBridge(webView)
        }
    }

    override fun onStop() {
        super.onStop()
        // DO NOT pause timers or webview; background audio continues uninterrupted
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
        super.onDestroy()
        MediaStateManager.setActionListener(null)
        networkMonitor.stopMonitoring()
    }
}

@Composable
fun SoundwaveApp(
    networkMonitor: NetworkMonitor,
    onGetWebView: () -> KeepAliveWebView?,
    onSetWebView: (KeepAliveWebView) -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val isOnline by networkMonitor.isOnline.collectAsState()

    var isPageLoaded by remember { mutableStateOf(false) }
    var splashProgress by remember { mutableFloatStateOf(0.1f) }
    var isSplashFinished by remember { mutableStateOf(false) }
    var hasFatalWebError by remember { mutableStateOf(false) }
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
            delay(500) // Small visual completion buffer
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

    // Hot-Recovery Signal Engine:
    // When network reconnects, reload web view if previously failed
    LaunchedEffect(isOnline) {
        if (isOnline) {
            val webView = onGetWebView()
            if (hasFatalWebError || webView?.url == null) {
                hasFatalWebError = false
                webView?.loadUrl(SoundwaveWebViewHelper.TARGET_URL)
            }
        }
    }

    // Back Navigation Handler
    BackHandler(enabled = isSplashFinished) {
        val webView = onGetWebView()
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
                    setLayerType(View.LAYER_TYPE_NONE, null)
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
                        }
                    }
                )

                onSetWebView(webView)
                webView.loadUrl(SoundwaveWebViewHelper.TARGET_URL)
                webView
            },
            update = {
                // Continuously maintained active in background layer
            }
        )

        // Instant Network Watchdog Offline Overlay Screen
        AnimatedVisibility(
            visible = !isOnline || (hasFatalWebError && !isOnline),
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
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
