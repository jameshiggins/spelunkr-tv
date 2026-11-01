package com.jameshiggins.spelunkrtv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * The whole app: one full-screen WebView tuned to spelunkr.tv.
 *
 * The site already speaks "TV remote" (arrows, OK, Escape-as-Back, media and channel keys), so
 * this activity only has to get out of the way: autoplay without a tap, keep the screen on,
 * hide system bars, translate the remote's Back into the page's Escape, and show a "no signal"
 * screen when the network is down.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var powerOn: View

    /** The element the page put into fullscreen (WebChromeClient custom view), if any. */
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var poweredOn = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "$userAgentString SpelunkrTV-Android/${BuildConfig.VERSION_NAME}"
            }
            webViewClient = TvWebViewClient()
            webChromeClient = TvChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        powerOn = buildPowerOnOverlay()
        root.addView(webView, matchParent())
        root.addView(powerOn, matchParent())
        setContentView(root)
        hideSystemBars()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(HOME_URL)
        }
        webView.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onStop() {
        // Leaving the app should feel like switching the TV off, not leave audio running.
        webView.onPause()
        webView.pauseTimers()
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) handleBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Back goes to the page first, as an Escape keydown (the site maps Escape to "back" and calls
     * preventDefault when it used it: closing the guide, a menu, the "leave?" card...). Only if
     * the page ignores it do we step back through history, then exit.
     */
    private fun handleBack() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        if (webView.url?.startsWith(NO_SIGNAL_URL) == true) {
            finish()
            return
        }
        webView.evaluateJavascript(SEND_ESCAPE_JS) { result ->
            when {
                result == "\"handled\"" -> Unit
                webView.canGoBack() -> webView.goBack()
                else -> finish()
            }
        }
    }

    private inner class TvWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Stay inside the app for web pages (incl. Discord sign-in); drop intent:, mailto:, etc.
            val scheme = request.url.scheme ?: return true
            return scheme != "https" && scheme != "http" && scheme != "file"
        }

        override fun onPageFinished(view: WebView, url: String) {
            playPowerOn()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame && !request.url.toString().startsWith(NO_SIGNAL_URL)) {
                view.loadUrl(NO_SIGNAL_URL)
            }
        }
    }

    private inner class TvChromeClient : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            root.addView(view, matchParent())
            view.requestFocus()
        }

        override fun onHideCustomView() {
            val view = customView ?: return
            root.removeView(view)
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            webView.requestFocus()
        }

        // Without this, WebView paints a grey "play" placeholder over videos before they start.
        override fun getDefaultVideoPoster(): Bitmap =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
    }

    /** Old-CRT "switch on": a thin bright line that snaps open to the full picture. */
    private fun buildPowerOnOverlay(): View {
        val line = View(this).apply { setBackgroundColor(Color.WHITE) }
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = false
            isFocusable = false
            addView(line, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, Gravity.CENTER))
            line.scaleX = 0f
            line.animate().scaleX(1f).setDuration(180).start()
        }
    }

    private fun playPowerOn() {
        if (poweredOn) return
        poweredOn = true
        val line = (powerOn as ViewGroup).getChildAt(0)
        line.animate()
            .scaleY(root.height / 6f)
            .alpha(0f)
            .setDuration(260)
            .start()
        powerOn.animate()
            .alpha(0f)
            .setStartDelay(120)
            .setDuration(320)
            .withEndAction { powerOn.visibility = View.GONE }
            .start()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun matchParent() =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    companion object {
        private const val HOME_URL = "https://spelunkr.tv/"
        private const val NO_SIGNAL_URL = "file:///android_asset/nosignal.html"

        // Fires Escape at whatever has focus; dispatchEvent() returns false if the page called
        // preventDefault(), i.e. it treated the key as its own "back".
        private const val SEND_ESCAPE_JS = """
            (function () {
              var t = document.activeElement || document.body || document;
              var e = new KeyboardEvent('keydown', {
                key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true
              });
              return t.dispatchEvent(e) ? 'unhandled' : 'handled';
            })()
        """
    }
}
