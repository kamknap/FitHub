package pl.fithubapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

class MuscleModel : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetector
    var isFrontView = true

    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
        private const val URL = "file:///android_asset/index/"
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.muscle_model)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(WebAppInterface(), "AndroidInterface")

        gestureDetector = GestureDetector(this, SwipeGestureListener())
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        loadView()
    }

    private fun loadView(){
        val url = intent.getStringExtra("url") ?: URL

        if(isFrontView){
            webView.loadUrl(url + "front.html")
        }
        else{
            webView.loadUrl(url + "back.html")
        }
    }

    private fun switchView() {
        isFrontView = !isFrontView
        loadView()
    }



    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY) &&
                abs(diffX) > SWIPE_THRESHOLD &&
                abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                switchView()
                return true
            }
            return false
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onPathClicked(id: String) {
            runOnUiThread {
                Toast.makeText(this@MuscleModel, "Kliknięto w: $id", Toast.LENGTH_SHORT).show()
            }
        }
    }

}