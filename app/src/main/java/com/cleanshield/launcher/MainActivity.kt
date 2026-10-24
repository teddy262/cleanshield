package com.cleanshield.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        // Ignore the system font scale: CleanFrame lays out on a fixed 1920x1080 stage.
        web.settings.textZoom = 100

        val bridge = Bridge(this, web)
        val host = "appassets.androidplatform.net"
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/appicon/", WebViewAssetLoader.PathHandler { path -> bridge.iconResponse(path) })
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            // Never navigate away from the bundled UI.
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = request.url.host != host
        }

        web.addJavascriptInterface(bridge, "AndroidBridge")
        setContentView(web)
        web.requestFocus()
        web.loadUrl("https://$host/assets/app/index.html")
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.requestFocus()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    // Remote Back is mapped to Escape, which CleanFrame uses to close panels.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            sendEscape()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyUp(keyCode, event)
    }

    // This is the home screen: Back never leaves it.
    override fun onBackPressed() {}

    private fun sendEscape() {
        web.evaluateJavascript(
            """(function(){['keydown','keyup'].forEach(function(t){
var e=new KeyboardEvent(t,{key:'Escape',code:'Escape',bubbles:true,cancelable:true});
Object.defineProperty(e,'keyCode',{get:function(){return 27}});
Object.defineProperty(e,'which',{get:function(){return 27}});
document.dispatchEvent(e);});})();""",
            null
        )
    }
}
