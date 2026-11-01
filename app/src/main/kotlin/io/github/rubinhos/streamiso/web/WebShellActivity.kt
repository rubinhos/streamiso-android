package io.github.rubinhos.streamiso.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedDispatcher
import android.widget.TextView
import io.github.rubinhos.streamiso.R
import io.github.rubinhos.streamiso.server.DiscKind
import io.github.rubinhos.streamiso.server.Route
import io.github.rubinhos.streamiso.server.ServerAddress
import io.github.rubinhos.streamiso.server.ServerStore
import io.github.rubinhos.streamiso.server.StreamIsoClient
import io.github.rubinhos.streamiso.setup.ServerSetupActivity
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The native shell: a full-screen WebView showing the StreamISO web interface, plus what only the
 * app can do (screen orientation, immersive mode, the Back button and connection errors).
 */
class WebShellActivity : Activity() {

    private val background: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var address: ServerAddress
    private lateinit var client: StreamIsoClient
    private lateinit var root: ViewGroup
    private lateinit var webView: WebView
    private lateinit var errorPanel: View
    private lateinit var errorText: TextView

    private var route = Route.OTHER
    private var immersive = false
    private var stopping = false
    private var failedUrl: String? = null

    // Bumped on every route change, so that a late answer about an old player page is ignored.
    private var playerGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val saved = ServerStore(this).load()
        if (saved == null) {
            startActivity(Intent(this, ServerSetupActivity::class.java))
            finish()
            return
        }
        address = saved
        client = StreamIsoClient(saved)

        setContentView(R.layout.activity_web_shell)
        root = findViewById(R.id.root)
        webView = findViewById(R.id.web_view)
        errorPanel = findViewById(R.id.error_panel)
        errorText = findViewById(R.id.error_text)

        setUpWindow()
        setUpWebView()
        setUpErrorPanel()
        setUpBackButton()

        webView.loadUrl(address.url("/"))
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        background.shutdownNow()
        if (::webView.isInitialized) {
            root.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ window

    private fun setUpWindow() {
        // The app draws behind the system bars on every Android version and keeps its content
        // clear of them here. In immersive mode the bars are hidden, so there is nothing to avoid.
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val margins = if (immersive) {
                Insets.NONE
            } else {
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
                )
            }
            view.setPadding(margins.left, margins.top, margins.right, margins.bottom)
            WindowInsets.CONSUMED
        }
    }

    private fun setImmersiveMode(on: Boolean) {
        if (immersive == on) return
        immersive = on
        window.insetsController?.let { controller ->
            if (on) {
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            } else {
                controller.show(WindowInsets.Type.systemBars())
            }
        }
        root.requestApplyInsets()
    }

    // ------------------------------------------------------------------ player mode

    private fun onUrlChanged(url: String) {
        val newRoute = address.routeOf(url)
        if (newRoute == route) return
        route = newRoute
        playerGeneration++
        stopping = false
        if (newRoute == Route.PLAYER) enterPlayer() else leavePlayer()
    }

    private fun enterPlayer() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val generation = playerGeneration
        background.execute {
            val kind = client.currentDiscKind()
            runOnUiThread {
                if (!isDestroyed && generation == playerGeneration) applyDiscKind(kind)
            }
        }
    }

    private fun applyDiscKind(kind: DiscKind?) {
        when (kind) {
            DiscKind.DVD -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setImmersiveMode(true)
            }
            DiscKind.CD -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            null -> Unit // nothing open: the player page goes back to the catalog by itself
        }
    }

    private fun leavePlayer() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setImmersiveMode(false)
    }

    // ------------------------------------------------------------------ Back button

    private fun setUpBackButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBack()
            }
        }
    }

    // Only reached on Android 11 and 12. Newer versions use the callback registered above.
    @Deprecated("Replaced by OnBackInvokedDispatcher on Android 13 and newer")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        when {
            errorPanel.visibility == View.VISIBLE -> finish()
            route == Route.PLAYER -> stopAndReturnToCatalog()
            route == Route.CATALOG -> finish()
            webView.canGoBack() -> webView.goBack()
            else -> finish()
        }
    }

    private fun stopAndReturnToCatalog() {
        if (stopping) return
        stopping = true
        background.execute {
            client.stop()
            runOnUiThread {
                // Goes back even if the stop failed: the catalog then shows what is wrong with the server.
                if (!isDestroyed) webView.loadUrl(address.url("/"))
            }
        }
    }

    // ------------------------------------------------------------------ errors and server address

    private fun setUpErrorPanel() {
        findViewById<View>(R.id.retry_button).setOnClickListener {
            errorPanel.visibility = View.GONE
            webView.loadUrl(failedUrl ?: address.url("/"))
        }
        findViewById<View>(R.id.change_address_button).setOnClickListener { openServerSetup() }
    }

    private fun showError(url: String, detail: String) {
        failedUrl = url.takeIf(address::allowsNavigation)
        errorText.text = getString(R.string.shell_error_message, address.display, detail)
        errorPanel.visibility = View.VISIBLE
    }

    private fun openServerSetup() {
        startActivity(Intent(this, ServerSetupActivity::class.java))
    }

    private fun showServerDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.server_dialog_message, address.display))
            .setPositiveButton(R.string.change_address) { _, _ -> openServerSetup() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
        }
        // The video stream is a page on another port, and it must be able to keep its session cookie.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        WebView.setWebContentsDebuggingEnabled(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)

        webView.webViewClient = ShellWebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            // The app only receives video: never grant the camera or the microphone.
            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
        }

        // Long press on the catalog changes the server. The player needs its touches for the video,
        // and text fields need their long press for pasting.
        webView.setOnLongClickListener {
            if (route != Route.CATALOG || webView.hitTestResult.type == WebView.HitTestResult.EDIT_TEXT_TYPE) {
                false
            } else {
                webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showServerDialog()
                true
            }
        }
    }

    private inner class ShellWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val allowed = if (request.isForMainFrame) address.allowsNavigation(url) else address.allowsResource(url)
            return !allowed
        }

        // Runs on a background thread. Answers anything from another host with an empty response.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (address.allowsResource(request.url.toString())) return null
            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            errorPanel.visibility = View.GONE
            onUrlChanged(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            onUrlChanged(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showError(request.url.toString(), error.description.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame) showError(request.url.toString(), "HTTP ${response.statusCode}")
        }

        // A crashed or killed page would otherwise take the whole app down with it.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recreate()
            return true
        }
    }
}
