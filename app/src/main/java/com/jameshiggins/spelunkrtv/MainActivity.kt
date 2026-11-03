package com.jameshiggins.spelunkrtv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
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
 * The whole app: one full-screen WebView tuned to spelunkr.tv in its "TV mode".
 *
 * The site has a 10-foot mode, switched on by `?tv` in the URL: full-screen picture, a channel
 * bar along the bottom that hides itself after a few seconds and comes back on every channel
 * change, up/down on the D-pad for channel up/down, and a Prevue-style grid for the guide. So
 * this activity only has to get out of the way: always load with `?tv`, tune in automatically
 * on launch, autoplay without a tap, keep the screen on, hide system bars, make Back behave like
 * a TV remote, and show a "no signal" screen when the network is down.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var powerOn: View

    /** The element the page put into fullscreen (WebChromeClient custom view), if any. */
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var poweredOn = false

    /** Switch straight to the picture on the next page load (launch, or back from NO SIGNAL). */
    private var autoTunePending = true

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
     * Back like a TV remote: while watching it brings up the guide (the site's own Escape
     * handling), from the guide it leaves the app. Any open dialog or menu gets the Escape first,
     * so it closes instead of exiting. Only if the page ignores the key do we step back through
     * history, then exit.
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
        webView.evaluateJavascript(BACK_JS) { result ->
            when (result) {
                "\"handled\"" -> Unit
                "\"guide\"" -> finish()
                else -> if (webView.canGoBack()) webView.goBack() else finish()
            }
        }
    }

    private inner class TvWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            // Stay inside the app for web pages (incl. Discord sign-in); drop intent:, mailto:, etc.
            val scheme = url.scheme ?: return true
            if (scheme != "https" && scheme != "http" && scheme != "file") return true
            // Any full navigation on the site keeps TV mode, even where the site drops the query.
            if (request.isForMainFrame && url.host == SITE_HOST && url.getQueryParameter(TV_PARAM) == null) {
                view.loadUrl(url.buildUpon().appendQueryParameter(TV_PARAM, "").build().toString())
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (Uri.parse(url).host == SITE_HOST) view.evaluateJavascript(PAGE_START_JS, null)
        }

        override fun onPageFinished(view: WebView, url: String) {
            playPowerOn()
            if (autoTunePending && Uri.parse(url).host == SITE_HOST) {
                autoTunePending = false
                view.evaluateJavascript(AUTO_TUNE_JS, null)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame && !request.url.toString().startsWith(NO_SIGNAL_URL)) {
                autoTunePending = true
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
        private const val SITE_HOST = "spelunkr.tv"
        private const val TV_PARAM = "tv"
        private const val HOME_URL = "https://$SITE_HOST/?$TV_PARAM"
        private const val NO_SIGNAL_URL = "file:///android_asset/nosignal.html"

        // Runs before the site's bundle: skip the first-run "how to spelunk" card and the splash,
        // and belt-and-braces the TV-mode class in case a navigation lost the query.
        private const val PAGE_START_JS = """
            (function () {
              try {
                localStorage.setItem('spelunkr-onboarded', '1');
                localStorage.setItem('spelunkr-tuned-in', '1');
              } catch (e) {}
              try { document.documentElement.classList.add('tv'); } catch (e) {}
            })()
        """

        // Gets from whatever the site shows first (splash, release note, guide) to the picture,
        // like a TV coming on. Polls because the app renders asynchronously after load.
        private const val AUTO_TUNE_JS = """
            (function () {
              var tries = 0;
              var press = function (key, code) {
                (document.activeElement || document.body).dispatchEvent(new KeyboardEvent('keydown', {
                  key: key, code: key, keyCode: code, which: code, bubbles: true, cancelable: true
                }));
              };
              var tick = function () {
                var stage = document.querySelector('.stage');
                if (stage && stage.classList.contains('stage--watch')) return;
                var btn = document.querySelector('.release-tune, .hp-tunein-btn');
                if (btn) btn.click();
                else if (stage && stage.classList.contains('stage--guide') &&
                         !document.querySelector('[role="dialog"], .leave-card')) press('Enter', 13);
                if (++tries < 40) setTimeout(tick, 400);
              };
              tick();
            })()
        """

        // From the guide with nothing open, report "guide" so the app exits. Otherwise fire Escape
        // at whatever has focus; dispatchEvent() returns false if the page called preventDefault(),
        // i.e. it treated the key as its own "back" (watch -> guide, close a menu or dialog).
        private const val BACK_JS = """
            (function () {
              var stage = document.querySelector('.stage');
              var open = document.querySelector('[role="dialog"], .leave-card, .onboarding-panel, .release-actions');
              var onKeys = document.activeElement && document.activeElement !== document.body &&
                           document.activeElement.hasAttribute('data-remote-ring');
              if (!open && !onKeys && stage && stage.classList.contains('stage--guide')) return 'guide';
              var t = document.activeElement || document.body || document;
              var e = new KeyboardEvent('keydown', {
                key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true
              });
              return t.dispatchEvent(e) ? 'unhandled' : 'handled';
            })()
        """
    }
}
