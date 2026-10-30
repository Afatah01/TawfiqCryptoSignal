package com.tawfiq.cryptosignal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// The Android app mirrors the live web app 1:1 - same URL, same design, same data.
// Web (Render): https://tawfiq-cryptosignal.onrender.com
const val APP_URL = "https://tawfiq-cryptosignal.onrender.com"

val RoyalBlue = Color(0xFF4776F5)
val EPurple = Color(0xFF7457E8)
val Muted = Color(0xFF8793A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WebApp() } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebApp() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(Modifier.fillMaxSize().background(Color(0xFFF4F7FC))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            loading = true; error = false
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false; canGoBack = view?.canGoBack() == true
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, err: WebResourceError?) {
                            if (request?.isForMainFrame == true) { loading = false; error = true }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            loading = false; error = true
                        }
                    }
                    webView = this
                    loadUrl(APP_URL)
                }
            }
        )
        if (loading && !error) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFFF4F7FC)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = RoyalBlue)
                Text("Tawfiq CryptoSignal", Modifier.padding(top = 16.dp), fontWeight = FontWeight.ExtraBold, color = EPurple)
                Text("TRACK • ALERT • GROW", color = Muted)
                Text("Powered by: Abdifatah Elmi Muhumed", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (error) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFFF4F7FC)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Connection problem", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = EPurple)
                Text(
                    "Tawfiq CryptoSignal could not reach the live server. Render free services sleep when idle — please wait a few seconds and retry. Check your internet connection.",
                    color = Muted, modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                    onClick = { error = false; webView?.loadUrl(APP_URL) }
                ) { Text("Retry") }
            }
        }
    }
}
