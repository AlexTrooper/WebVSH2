package com.example.singlepagebrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var prefs: android.content.SharedPreferences

    private var savedUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    // --- triple-tap state ---
    private var tapCount = 0
    private var lastTapTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // localStorage / sessionStorage
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    // Show a retry overlay only for the main frame, not for subresources
                    if (request.isForMainFrame) showErrorOverlay()
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    hideErrorOverlay()
                }
            }
        }

        val root = FrameLayout(this).apply { addView(webView) }
        setContentView(root)

        hideSystemBars()

        val url = savedUrl
        if (url.isNullOrBlank()) {
            showUrlDialog(initialSetup = true)
        } else {
            webView.loadUrl(url)
        }

        installTripleTapListener()
    }

    // ------------------------------------------------------------------ UI

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showUrlDialog(initialSetup: Boolean) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.dialog_hint)
            setText(savedUrl.orEmpty())
            setSelection(text.length)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(R.string.dialog_message)
            .setView(input)
            .setPositiveButton(R.string.dialog_save, null) // override below to validate
            .setCancelable(!initialSetup) // first run: URL is required

        if (!initialSetup) {
            builder.setNegativeButton(R.string.dialog_cancel, null)
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalized = normalizeUrl(input.text.toString())
                if (normalized == null) {
                    input.error = getString(R.string.dialog_invalid_url)
                } else {
                    savedUrl = normalized
                    dialog.dismiss()
                    webView.loadUrl(normalized)
                }
            }
        }
        dialog.show()
    }

    /** Adds scheme if missing, validates the result. Returns null on invalid input. */
    private fun normalizeUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty()) return null
        if (!url.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) url = "https://$url"
        return try {
            val uri = android.net.Uri.parse(url)
            if (uri.host.isNullOrBlank()) null else url
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------- triple-tap on corner

    private fun installTripleTapListener() {
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val cornerPx = CORNER_DP * resources.displayMetrics.density
                val inCorner =
                    event.x > webView.width - cornerPx && event.y < cornerPx

                if (inCorner) {
                    val now = System.currentTimeMillis()
                    tapCount = if (now - lastTapTime < TAP_TIMEOUT_MS) tapCount + 1 else 1
                    lastTapTime = now
                    if (tapCount >= 3) {
                        tapCount = 0
                        showUrlDialog(initialSetup = false)
                    }
                } else {
                    tapCount = 0
                }
            }
            false // let WebView receive the event as usual
        }
    }

    // ----------------------------------------------------------- error view

    private var errorView: android.widget.TextView? = null

    private fun showErrorOverlay() {
        if (errorView != null) return
        val tv = android.widget.TextView(this).apply {
            text = getString(R.string.error_no_connection)
            textSize = 16f
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFFFFFFFF.toInt())
            setTextColor(0xFF222222.toInt())
            setOnClickListener { savedUrl?.let(webView::loadUrl) }
        }
        (webView.parent as ViewGroup).addView(
            tv,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        errorView = tv
    }

    private fun hideErrorOverlay() {
        errorView?.let { (webView.parent as ViewGroup).removeView(it) }
        errorView = null
    }

    // ------------------------------------------------------------ lifecycle

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars() // re-hide after system bars appear (swipe, dialog, etc.)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "app"
        private const val KEY_URL = "url"
        private const val CORNER_DP = 100f
        private const val TAP_TIMEOUT_MS = 500L
    }
}
