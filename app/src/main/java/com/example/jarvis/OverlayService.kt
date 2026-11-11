package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class OverlayService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val CHANNEL_ID = "jarvis"
        private const val NOTIFICATION_ID = 7
        private const val DEFAULT_ENDPOINT =
            "https://jarvis-gemini.facet4419.workers.dev/chat"
        private const val ACTION_READ_SCREEN = "com.example.jarvis.READ_SCREEN"
        private const val ACTION_SCREEN_TEXT = "com.example.jarvis.SCREEN_TEXT"
    }

    private lateinit var windowManager: WindowManager
    private var orb: View? = null
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listening = false
    private var receiverRegistered = false

    // Short-lived conversation history, kept only while this service runs.
    private val conversation = JSONArray()

    private val systemPrompt =
        "You are JARVIS, a helpful Android voice assistant. " +
        "Answer clearly and naturally. Keep spoken answers concise. " +
        "You cannot directly control the phone unless the Android app " +
        "reports that an action was performed. Never claim otherwise. " +
        "Ask for clarification when a request is ambiguous."

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val content = intent?.getStringExtra("screen_text").orEmpty()

            if (content.isNotBlank()) {
                speak("Visible screen text: $content")
            } else {
                speak("I could not read any visible text.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS is ready")
            .setContentText("Tap the floating orb to speak.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val filter = IntentFilter(ACTION_SCREEN_TEXT)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }

        receiverRegistered = true
        addOrb()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
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

            setOnClickListener {
                startListening()
            }

            setOnLongClickListener {
                speak("JARVIS is shutting down.")
                stopSelf()
                true
            }
        }

        val type =
            if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            64,
            64,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 220
        }

        orb = bubble

        try {
            windowManager.addView(bubble, params)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Overlay permission required.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    private fun startListening() {
        if (listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is unavailable on this device.")
            return
        }

        if (Build.VERSION.SDK_INT >= 31 &&
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this) &&
            !packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        ) {
            speak("A speech recognition service is not available.")
            return
        }

        listening = true
        speak("Listening.")

        try {
            speech?.destroy()

            speech = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->

                recognizer.setRecognitionListener(object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        listening = false
                    }

                    override fun onError(error: Int) {
                        listening = false
                        speak("I couldn't understand that. Tap the orb to try again.")
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false

                        val heard = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()

                        if (heard.isNotBlank()) {
                            handleCommand(heard)
                        } else {
                            speak("I didn't hear a command.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    val language = getSharedPreferences("jarvis", MODE_PRIVATE)
                        .getString("speech_language", "auto") ?: "auto"
                    val speechLocale = when (language) {
                        "en" -> Locale("en", "IN")
                        "hi" -> Locale("hi", "IN")
                        "hinglish" -> Locale("en", "IN")
                        else -> Locale.getDefault()
                    }
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        speechLocale.toLanguageTag()
                    )
                    if (Build.VERSION.SDK_INT >= 34 && language == "auto") {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED, true)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED, true)
                    }
                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        false
                    )
                }

                recognizer.startListening(intent)
            }
        } catch (_: Exception) {
            listening = false
            speak("I couldn't start the microphone.")
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.trim()
        if (command.isBlank()) return

        val lower = command.lowercase(Locale.ROOT)

        val actualCommand =
            if (lower.startsWith("hey jarvis")) {
                command.substringAfter("hey jarvis").trim()
            } else {
                command
            }

        if (actualCommand.isBlank()) {
            speak("Yes. How can I help?")
            return
        }

        if (executeLocal(actualCommand)) return

        speak("Let me think.")

        worker.execute {
            val answer = askAi(actualCommand)

            mainHandler.post {
                speak(
                    answer ?: "I couldn't reach the AI service. " +
                        "Please check your internet connection."
                )
            }
        }
    }

    private fun executeLocal(command: String): Boolean {
        val c = command.lowercase(Locale.ROOT)

        return try {
            when {
                c.startsWith("open ") -> {
                    val appName = command.substringAfter("open ").trim()
                    val launch = findLaunchIntent(appName)

                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launch)
                        speak("Opening $appName.")
                    } else {
                        speak("I couldn't find that app.")
                    }
                    true
                }

                c.startsWith("search ") || c.startsWith("google ") -> {
                    val query = command.substringAfter(" ").trim()

                    if (query.isBlank()) {
                        speak("What should I search for?")
                    } else {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://www.google.com/search?q=" +
                                    Uri.encode(query)
                            )
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        startActivity(intent)
                        speak("Opening search results.")
                    }
                    true
                }

                c.startsWith("call ") -> {
                    val intent = Intent(Intent.ACTION_DIAL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    startActivity(intent)
                    speak("Choose the contact and confirm the call in your dialer.")
                    true
                }

                c.startsWith("message ") -> {
                    val body = command.substringAfter("message ").trim()

                    val intent = Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("smsto:")
                    ).apply {
                        putExtra("sms_body", body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    startActivity(intent)
                    speak("Choose a recipient and send the message when ready.")
                    true
                }

                c.contains("bluetooth") -> {
                    startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    speak("Opening Bluetooth settings.")
                    true
                }

                c.contains("wifi") -> {
                    startActivity(
                        Intent(Settings.ACTION_WIFI_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    speak("Opening Wi-Fi settings.")
                    true
                }

                c.contains("read screen") ||
                    c.contains("what is on my screen") -> {

                    sendBroadcast(
                        Intent(ACTION_READ_SCREEN).setPackage(packageName)
                    )

                    speak("Checking visible screen text.")
                    true
                }

                c == "stop" || c == "goodbye jarvis" -> {
                    speak("Going idle. Tap the orb when you need me.")
                    true
                }

                else -> false
            }
        } catch (_: Exception) {
            speak("I couldn't complete that phone action.")
            true
        }
    }

    private fun findLaunchIntent(name: String): Intent? {
        val pm = packageManager

        val apps = pm.getInstalledApplications(
            PackageManager.GET_META_DATA
        )

        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().equals(name, true)
        } ?: apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, true)
        }

        return match?.let {
            pm.getLaunchIntentForPackage(it.packageName)
        }
    }

    private fun askAi(prompt: String): String? {
        var connection: HttpURLConnection? = null

        return try {
            val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)

            val endpoint = prefs.getString(
                "endpoint",
                DEFAULT_ENDPOINT
            ) ?: DEFAULT_ENDPOINT

            if (!endpoint.startsWith("https://")) {
                return null
            }

            conversation.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", prompt)
            )

            // Keep only the latest 12 conversation messages.
            while (conversation.length() > 12) {
                conversation.remove(0)
            }

            val messages = JSONArray().put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt)
            )

            for (index in 0 until conversation.length()) {
                messages.put(conversation.getJSONObject(index))
            }

            val body = JSONObject().put("messages", messages)

            connection = URL(endpoint).openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true

                setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )
            }

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            val responseText =
                if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                } else {
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                }

            if (responseCode !in 200..299) {
                conversation.remove(conversation.length() - 1)
                return null
            }

            val answer = JSONObject(responseText)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    conversation.remove(conversation.length() - 1)
                    return null
                }

            conversation.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", answer)
            )

            while (conversation.length() > 12) {
                conversation.remove(0)
            }

            answer

        } catch (_: Exception) {
            if (conversation.length() > 0 &&
                conversation.optJSONObject(conversation.length() - 1)
                    ?.optString("role") == "user"
            ) {
                conversation.remove(conversation.length() - 1)
            }
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun speak(text: String) {
        mainHandler.post {
            Toast.makeText(
                this,
                text.take(180),
                Toast.LENGTH_LONG
            ).show()

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_reply"
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val language = getSharedPreferences("jarvis", MODE_PRIVATE)
                .getString("speech_language", "auto") ?: "auto"
            val speechLocale = when (language) {
                "en" -> Locale("en", "IN")
                "hi" -> Locale("hi", "IN")
                "hinglish" -> Locale("en", "IN")
                else -> Locale.getDefault()
            }
            val result = tts?.setLanguage(speechLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts?.setLanguage(Locale.US)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        listening = false

        speech?.cancel()
        speech?.destroy()
        speech = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        try {
            orb?.let {
                windowManager.removeView(it)
            }
        } catch (_: Exception) {
        }

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
        }

        worker.shutdownNow()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
