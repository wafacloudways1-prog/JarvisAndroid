package com.example.jarvis

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color as AColor
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class OverlayService : Service(), TextToSpeech.OnInitListener {
    private lateinit var wm: WindowManager
    private var orb: View? = null
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var listening = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val content = intent?.getStringExtra("screen_text").orEmpty()
            if (content.isNotBlank()) speak("Visible screen text: $content")
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        val notification = Notification.Builder(this, "jarvis")
            .setContentTitle("JARVIS is ready")
            .setContentText("Tap the floating orb to give a command.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(7, notification)
        registerReceiver(screenReceiver, IntentFilter("com.example.jarvis.SCREEN_TEXT"), RECEIVER_NOT_EXPORTED)
        addOrb()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("jarvis", "JARVIS assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun addOrb() {
        val bubble = TextView(this).apply {
            text = "J"
            textSize = 23f
            setTextColor(AColor.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(AColor.rgb(0, 105, 150))
            elevation = 18f
            setOnClickListener { startListening() }
            setOnLongClickListener { stopSelf(); true }
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(64, 64, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 24; params.y = 220
        orb = bubble
        try { wm.addView(bubble, params) } catch (_: Exception) { Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show(); stopSelf() }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { speak("Speech recognition is unavailable on this device."); return }
        if (listening) return
        listening = true
        speak("Listening")
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) { listening = false }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (heard.isNotBlank()) handleCommand(heard)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(intent)
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.trim()
        val lower = command.lowercase(Locale.ROOT)
        if (lower.startsWith("hey jarvis")) {
            val rest = command.substringAfter("hey jarvis", "").trim()
            if (rest.isNotBlank()) { executeLocal(rest); return }
            speak("Yes. How can I help?")
            startListening()
            return
        }
        if (executeLocal(command)) return
        speak("Let me think.")
        worker.execute {
            val answer = askAi(command)
            android.os.Handler(mainLooper).post { speak(answer ?: "I could not reach the AI service. Check your connection and AI settings.") }
        }
    }

    private fun executeLocal(command: String): Boolean {
        val c = command.lowercase(Locale.ROOT)
        when {
            c.startsWith("open ") -> {
                val appName = command.substringAfter("open ").trim()
                val launch = findLaunchIntent(appName)
                if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); speak("Opening $appName") }
                else speak("I couldn't find that app.")
                return true
            }
            c.startsWith("search ") || c.startsWith("google ") -> {
                val query = command.substringAfter(' ').trim()
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i); speak("Searching the web"); return true
            }
            c.startsWith("call ") -> {
                val who = command.substringAfter("call ").trim()
                val i = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i); speak("Opening the dialer. Please choose the contact and confirm the call."); return true
            }
            c.startsWith("message ") -> {
                val body = command.substringAfter("message ").trim()
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                    putExtra("sms_body", body); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i); speak("Opening your messaging app. Choose a recipient and send when ready."); return true
            }
            c.contains("bluetooth") -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Opening Bluetooth settings"); return true
            }
            c.contains("wifi") -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Opening Wi-Fi settings"); return true
            }
            c.contains("read screen") || c.contains("what is on my screen") -> {
                sendBroadcast(Intent("com.example.jarvis.READ_SCREEN").setPackage(packageName))
                speak("If screen reading is enabled, I will read the visible text."); return true
            }
            c == "stop" || c == "goodbye jarvis" -> { speak("Going idle."); return true }
        }
        return false
    }

    private fun findLaunchIntent(name: String): Intent? {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(name, true) }
            ?: apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(name, true) }
        return match?.let { pm.getLaunchIntentForPackage(it.packageName) }
    }

    private fun askAi(prompt: String): String? {
        return try {
            val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
            val endpoint = prefs.getString("endpoint", "") ?: ""
            val model = prefs.getString("model", "") ?: ""
            val key = prefs.getString("key", "") ?: ""
            if (endpoint.isBlank() || model.isBlank() || key.isBlank()) return null
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            val body = JSONObject().put("model", model).put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You are JARVIS, a concise and safe Android voice assistant. Give clear answers. Never claim to have performed phone actions unless the app reports success."))
                .put(JSONObject().put("role", "user").put("content", prompt)))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val response = if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
                else conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        } catch (_: Exception) { null }
    }

    private fun speak(text: String) {
        if (::wm.isInitialized) android.os.Handler(mainLooper).post {
            Toast.makeText(this, text.take(150), Toast.LENGTH_SHORT).show()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
        }
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        listening = false
        speech?.destroy(); tts?.stop(); tts?.shutdown()
        try { orb?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        worker.shutdownNow()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
