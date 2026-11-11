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
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
    private var wakeModeEnabled = false
    private var awaitingWakeCommand = false
    private var resumeWakeAfterSpeech = false
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
            .setContentText("Tap the orb for a command. Long-press for Hey JARVIS wake mode.")
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

    private fun updateWakeNotification() {
        val message = if (wakeModeEnabled) {
            "Hey JARVIS wake mode is ON. Tap the orb to disable it."
        } else {
            "Wake mode is OFF. Tap for a command; long-press for wake mode."
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS assistant")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
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
        val bubble = JarvisOrbView(this).apply {
            elevation = 22f
            setWakeActive(false)
            setOnClickListener {
                if (wakeModeEnabled) {
                    wakeModeEnabled = false
                    awaitingWakeCommand = false
                    resumeWakeAfterSpeech = false
                    listening = false
                    speech?.cancel()
                    updateWakeNotification()
                    setWakeActive(false)
                    speak("Hey JARVIS wake mode is off.")
                } else {
                    startListening()
                }
            }

            setOnLongClickListener {
                if (wakeModeEnabled) {
                    wakeModeEnabled = false
                    awaitingWakeCommand = false
                    resumeWakeAfterSpeech = false
                    listening = false
                    speech?.cancel()
                    setWakeActive(false)
                    updateWakeNotification()
                    speak("Wake mode is off.")
                } else {
                    wakeModeEnabled = true
                    awaitingWakeCommand = false
                    setWakeActive(true)
                    updateWakeNotification()
                    speak("Hey JARVIS wake mode is on. Say Hey JARVIS followed by your command.")
                    mainHandler.postDelayed({
                        if (wakeModeEnabled && !listening) startListening()
                    }, 1800)
                }
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

        val orbSize = (84 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            orbSize,
            orbSize,
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
        Toast.makeText(this, "JARVIS is listening. Speak now.", Toast.LENGTH_SHORT).show()

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
                        if (wakeModeEnabled) {
                            mainHandler.postDelayed({
                                if (wakeModeEnabled && !listening) startListening()
                            }, 900)
                        } else {
                            speak("I couldn't understand that. Tap the orb to try again.")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false

                        val heard = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        val normalized = heard.trim()
                        val lower = normalized.lowercase(Locale.ROOT)

                        if (wakeModeEnabled) {
                            if (awaitingWakeCommand) {
                                awaitingWakeCommand = false
                                if (normalized.isNotBlank()) {
                                    handleCommand(normalized)
                                } else {
                                    mainHandler.postDelayed({
                                        if (wakeModeEnabled && !listening) startListening()
                                    }, 400)
                                }
                            } else if (lower.startsWith("hey jarvis") || lower.startsWith("हे जार्विस")) {
                                val wakePhrase = if (lower.startsWith("हे जार्विस")) "हे जार्विस" else "hey jarvis"
                                val command = normalized.drop(wakePhrase.length).trim()
                                if (command.isBlank()) {
                                    awaitingWakeCommand = true
                                    resumeWakeAfterSpeech = true
                                    speak("Yes? I'm listening.")
                                } else {
                                    handleCommand(command)
                                }
                            } else {
                                mainHandler.postDelayed({
                                    if (wakeModeEnabled && !listening) startListening()
                                }, 350)
                            }
                        } else if (normalized.isNotBlank()) {
                            handleCommand(normalized)
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
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                        putExtra(
                            RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                            RecognizerIntent.LANGUAGE_SWITCH_BALANCED
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                            arrayListOf("en-IN", "hi-IN")
                        )
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

        resumeWakeAfterSpeech = wakeModeEnabled
        if (executeLocal(actualCommand)) return

        if (!wakeModeEnabled) speak("Let me think.")

        worker.execute {
            val answer = askAi(actualCommand)

            mainHandler.post {
                resumeWakeAfterSpeech = wakeModeEnabled
                speak(
                    answer ?: "I couldn't reach the AI service. " +
                        "Please check your internet connection."
                )
            }
        }
    }

    private fun executeLocal(command: String): Boolean {
        val c = command.lowercase(Locale.ROOT)

        if (openPreparedWhatsAppMessage(command)) return true

        return try {
            when {
                // YouTube search commands. These open results; they do not autoplay videos.
                c.startsWith("search youtube for ") ||
                    c.startsWith("youtube search ") ||
                    (c.startsWith("search ") && c.endsWith(" on youtube")) ||
                    (c.startsWith("play ") && c.endsWith(" on youtube")) ||
                    (c.startsWith("open youtube") && c.contains(" search ")) -> {
                    val query = when {
                        c.startsWith("search youtube for ") ->
                            command.drop("search youtube for ".length).trim()
                        c.startsWith("youtube search ") ->
                            command.drop("youtube search ".length).trim()
                        c.startsWith("search ") && c.endsWith(" on youtube") ->
                            command.substring(7, command.length - " on youtube".length).trim()
                        c.startsWith("play ") ->
                            command.substring(5, command.length - " on youtube".length).trim()
                        else ->
                            command.substringAfter("search", "").trim()
                                .removePrefix("for ").trim()
                    }
                    openSearchResults(query, true)
                    true
                }

                // Google/web search commands.
                c.startsWith("search google for ") ||
                    c.startsWith("google for ") ||
                    c.startsWith("search for ") ||
                    c.startsWith("look up ") ||
                    c.startsWith("search ") ||
                    c.startsWith("google ") -> {
                    val prefixes = listOf(
                        "search google for ", "google for ", "search for ",
                        "look up ", "search ", "google "
                    )
                    val prefix = prefixes.first { c.startsWith(it) }
                    val query = command.drop(prefix.length).trim()
                    openSearchResults(query, false)
                    true
                }

                // A natural-language shortcut for opening a chat app.
                c == "open chat" || c == "open whatsapp chat" ||
                    c == "open my chats" || c == "open messages" -> {
                    val target = if (c == "open messages") {
                        findLaunchIntent("Messages") ?: findLaunchIntent("WhatsApp")
                    } else {
                        findLaunchIntent("WhatsApp") ?: findLaunchIntent("Messages")
                    }
                    if (target != null) {
                        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(target)
                        speak(if (c == "open messages") "Opening messages." else "Opening your chats.")
                    } else {
                        speak("I couldn't find WhatsApp or a messaging app.")
                    }
                    true
                }

                // "Open YouTube and search for ..." is one spoken command.
                c.startsWith("open youtube and search ") -> {
                    val query = command.substringAfter("search ").trim()
                        .removePrefix("for ").trim()
                    openSearchResults(query, true)
                    true
                }

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


    private fun openPreparedWhatsAppMessage(command: String): Boolean {
        val patterns = listOf(
            Regex("""^(?:open|start)\s+(?:a\s+)?(?:whatsapp\s+)?chat\s+(?:with\s+)?(.+?)\s+and\s+say\s+(.+?)(?:\s+to\s+(?:him|her|them))?$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:message|whatsapp)\s+(.+?)\s+(?:saying|and\s+say)\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^open\s+chat\s+(.+?)\s+and\s+tell\s+(?:him|her|them)\s+(.+)$""", RegexOption.IGNORE_CASE)
        )
        val match = patterns.firstNotNullOfOrNull { it.find(command) } ?: return false
        val contactName = match.groupValues[1].trim()
        val message = match.groupValues[2].trim()
        if (contactName.isBlank() || message.isBlank()) {
            speak("Tell me the contact name and the message.")
            return true
        }

        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            speak("Please allow Contacts permission in the JARVIS app first.")
            return true
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val matches = mutableListOf<Pair<String, String>>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf("%$contactName%"),
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex).orEmpty()
                val number = cursor.getString(numberIndex).orEmpty()
                if (number.isNotBlank()) matches.add(name to number)
            }
        }

        val exact = matches.filter { it.first.equals(contactName, ignoreCase = true) }
        val distinctExactNumbers = exact.map { it.second }.distinct()
        val distinctNumbers = matches.map { it.second }.distinct()
        val chosen = when {
            distinctExactNumbers.size == 1 -> exact.first()
            exact.isEmpty() && distinctNumbers.size == 1 -> matches.first()
            else -> null
        }

        if (chosen == null) {
            speak(if (matches.isEmpty()) "I couldn't find $contactName in your contacts." else "I found multiple matching contacts. Please say the full name.")
            return true
        }

        val digits = chosen.second.filter { it.isDigit() }
        if (digits.length < 8) {
            speak("That contact's phone number doesn't look complete.")
            return true
        }

        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        speak("I prepared a WhatsApp message to ${chosen.first}. Please review it and tap Send.")
        return true
    }

    private fun openSearchResults(query: String, youtube: Boolean) {
        if (query.isBlank()) {
            speak(if (youtube) "What should I search for on YouTube?" else "What should I search for?")
            return
        }

        val baseUrl = if (youtube) {
            "https://www.youtube.com/results?search_query="
        } else {
            "https://www.google.com/search?q="
        }
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(baseUrl + Uri.encode(query))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivity(intent)
        speak(if (youtube) "Searching YouTube for $query." else "Searching the web for $query.")
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
            if (resumeWakeAfterSpeech && wakeModeEnabled) {
                resumeWakeAfterSpeech = false
                mainHandler.postDelayed({
                    if (wakeModeEnabled && !listening) startListening()
                }, 2200)
            }
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
        wakeModeEnabled = false
        awaitingWakeCommand = false

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
