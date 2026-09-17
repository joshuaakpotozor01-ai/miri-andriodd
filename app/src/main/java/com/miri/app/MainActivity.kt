package com.miri.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val micPermissionCode = 501

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.webChromeClient = object : WebChromeClient() {
            // Grant in-page getUserMedia() calls automatically once we already
            // hold the RECORD_AUDIO permission — needed for the orb's live
            // mic-level animation even though recognition itself runs natively.
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(0.95f)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { webView.evaluateJavascript("window.onNativeSpeakStart && window.onNativeSpeakStart()", null) }
            }
            override fun onDone(utteranceId: String?) {
                runOnUiThread { webView.evaluateJavascript("window.onNativeSpeakEnd && window.onNativeSpeakEnd()", null) }
            }
            override fun onError(utteranceId: String?) {
                runOnUiThread { webView.evaluateJavascript("window.onNativeSpeakEnd && window.onNativeSpeakEnd()", null) }
            }
        })

        ensureMicPermission()
        maybeRequestOverlayPermission()

        webView.loadUrl("file:///android_asset/miri.html")
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode
            )
        }
    }

    /** The floating bubble needs "draw over other apps" — a one-time manual grant. */
    private fun maybeRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    /** JS-callable bridge exposed to miri.html as `window.AndroidBridge`. */
    inner class Bridge {

        @JavascriptInterface
        fun startListening() {
            runOnUiThread {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                }
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        webView.evaluateJavascript("window.onNativeListenStart && window.onNativeListenStart()", null)
                    }
                    override fun onResults(results: Bundle) {
                        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                        webView.evaluateJavascript("window.onNativeResult && window.onNativeResult(${org.json.JSONObject.quote(text)}, true)", null)
                    }
                    override fun onPartialResults(partialResults: Bundle) {
                        val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                        webView.evaluateJavascript("window.onNativeResult && window.onNativeResult(${org.json.JSONObject.quote(text)}, false)", null)
                    }
                    override fun onError(error: Int) {
                        webView.evaluateJavascript("window.onNativeListenEnd && window.onNativeListenEnd()", null)
                    }
                    override fun onEndOfSpeech() {
                        webView.evaluateJavascript("window.onNativeListenEnd && window.onNativeListenEnd()", null)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        webView.evaluateJavascript("window.onNativeLevel && window.onNativeLevel(${rmsdB})", null)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer?.startListening(intent)
            }
        }

        @JavascriptInterface
        fun stopListening() {
            runOnUiThread { speechRecognizer?.stopListening() }
        }

        @JavascriptInterface
        fun speak(text: String, rate: Float) {
            runOnUiThread {
                tts?.setSpeechRate(rate)
                val params = Bundle()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "miri_utt")
            }
        }

        @JavascriptInterface
        fun stopOverlayBubble() {
            runOnUiThread { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }
    }

    override fun onResume() {
        super.onResume()
        // If the user just granted overlay permission and is returning here, start the bubble.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
}
