package com.example.jarvis

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * JARVIS capability directory.
 *
 * These are real Android intent-based entry points. Protected actions are handed
 * to the official app or Android settings UI; JARVIS never silently sends a
 * message, places a call, changes protected settings, or performs a purchase.
 */
private data class JarvisFeature(
    val number: Int,
    val group: String,
    val title: String,
    val detail: String,
    val action: String,
    val argument: String = ""
)

private val jarvisFeatures = listOf(
    // VOICE & WAKE WORD (1-12)
    JarvisFeature(1,"Voice & wake word","Tap-to-talk voice input","Start voice recognition from the floating orb.","voice"),
    JarvisFeature(2,"Voice & wake word","Spoken AI replies","Speak answers using Android text-to-speech.","tts"),
    JarvisFeature(3,"Voice & wake word","English speech mode","Select English (India) recognition.","language","en"),
    JarvisFeature(4,"Voice & wake word","Hindi speech mode","Select Hindi (India) recognition.","language","hi"),
    JarvisFeature(5,"Voice & wake word","Hinglish speech mode","Use English-India recognition with Hinglish AI replies.","language","hinglish"),
    JarvisFeature(6,"Voice & wake word","Automatic language mode","Use device language and supported Android language detection.","language","auto"),
    JarvisFeature(7,"Voice & wake word","Wake phrase help","Explains the limits of wake phrase detection on Android.","info","Wake phrase is only handled during an active listening session in this build. Always-on hotword needs a dedicated on-device engine and background microphone support."),
    JarvisFeature(8,"Voice & wake word","Repeat last spoken answer","Repeat is available through the voice session; ask JARVIS to repeat.","info","Say: repeat that."),
    JarvisFeature(9,"Voice & wake word","Voice output settings","Open text-to-speech settings.","settings","tts"),
    JarvisFeature(10,"Voice & wake word","Microphone permission","Review microphone permission for JARVIS.","settings","app"),
    JarvisFeature(11,"Voice & wake word","Speech recognition settings","Open available speech recognition settings.","settings","voice"),
    JarvisFeature(12,"Voice & wake word","Stop voice assistant","Stop the floating foreground assistant.","stop"),

    // WHATSAPP (13-24)
    JarvisFeature(13,"WhatsApp","Open WhatsApp","Launch WhatsApp if installed.","app","com.whatsapp"),
    JarvisFeature(14,"WhatsApp","Open WhatsApp Business","Launch WhatsApp Business if installed.","app","com.whatsapp.w4b"),
    JarvisFeature(15,"WhatsApp","Start a WhatsApp chat","Open WhatsApp chat link; choose recipient yourself.","url","https://wa.me/"),
    JarvisFeature(16,"WhatsApp","Message a phone number","Open WhatsApp chat for a number; review before sending.","prompt_url","https://wa.me/"),
    JarvisFeature(17,"WhatsApp","Share text to WhatsApp","Open Android share sheet; select WhatsApp and review.","share",""),
    JarvisFeature(18,"WhatsApp","Share a link to WhatsApp","Open Android share sheet for a link.","share",""),
    JarvisFeature(19,"WhatsApp","WhatsApp status","Open WhatsApp; create and post status manually.","app","com.whatsapp"),
    JarvisFeature(20,"WhatsApp","WhatsApp calls","Open WhatsApp; choose a contact and start call manually.","app","com.whatsapp"),
    JarvisFeature(21,"WhatsApp","WhatsApp groups","Open WhatsApp groups list.","app","com.whatsapp"),
    JarvisFeature(22,"WhatsApp","WhatsApp settings","Open WhatsApp settings through the app.","app","com.whatsapp"),
    JarvisFeature(23,"WhatsApp","WhatsApp privacy","Open WhatsApp; review privacy controls manually.","app","com.whatsapp"),
    JarvisFeature(24,"WhatsApp","WhatsApp notifications","Open Android app notification settings for WhatsApp.","app_settings","com.whatsapp"),

    // YOUTUBE (25-34)
    JarvisFeature(25,"YouTube","Open YouTube","Launch YouTube if installed.","app","com.google.android.youtube"),
    JarvisFeature(26,"YouTube","Search YouTube","Search YouTube for a topic.","youtube_search",""),
    JarvisFeature(27,"YouTube","YouTube Shorts","Open YouTube Shorts.","url","https://www.youtube.com/shorts"),
    JarvisFeature(28,"YouTube","YouTube subscriptions","Open YouTube subscriptions page.","url","https://www.youtube.com/feed/subscriptions"),
    JarvisFeature(29,"YouTube","YouTube history","Open YouTube watch history.","url","https://www.youtube.com/feed/history"),
    JarvisFeature(30,"YouTube","YouTube library","Open YouTube library.","url","https://www.youtube.com/feed/library"),
    JarvisFeature(31,"YouTube","YouTube Music","Launch YouTube Music if installed.","app","com.google.android.apps.youtube.music"),
    JarvisFeature(32,"YouTube","Share YouTube video","Open YouTube; use its share control to choose a video.","app","com.google.android.youtube"),
    JarvisFeature(33,"YouTube","YouTube creator studio","Open YouTube Studio if installed.","app","com.google.android.apps.youtube.creator"),
    JarvisFeature(34,"YouTube","YouTube settings","Open YouTube settings through the app.","app","com.google.android.youtube"),

    // PHONE CONTROL (35-49)
    JarvisFeature(35,"Phone control","Open dialer","Open dialer; confirm the call yourself.","dial"),
    JarvisFeature(36,"Phone control","Call a contact","Open dialer; select the contact and confirm.","dial"),
    JarvisFeature(37,"Phone control","Compose SMS","Open SMS composer; review recipient and message before sending.","sms"),
    JarvisFeature(38,"Phone control","Open camera","Launch the camera app.","camera"),
    JarvisFeature(39,"Phone control","Wi-Fi settings","Open Android Wi-Fi settings.","settings","wifi"),
    JarvisFeature(40,"Phone control","Bluetooth settings","Open Android Bluetooth settings.","settings","bluetooth"),
    JarvisFeature(41,"Phone control","Location settings","Open Android location settings.","settings","location"),
    JarvisFeature(42,"Phone control","Display settings","Open Android display settings.","settings","display"),
    JarvisFeature(43,"Phone control","Sound settings","Open Android sound settings.","settings","sound"),
    JarvisFeature(44,"Phone control","Battery settings","Open Android battery settings.","settings","battery"),
    JarvisFeature(45,"Phone control","App settings","Open JARVIS app settings.","settings","app"),
    JarvisFeature(46,"Phone control","Notification settings","Open Android notification settings.","settings","notifications"),
    JarvisFeature(47,"Phone control","Accessibility settings","Open accessibility settings.","settings","accessibility"),
    JarvisFeature(48,"Phone control","Set an alarm","Open the clock app to create an alarm.","alarm"),
    JarvisFeature(49,"Phone control","Set a timer","Open the clock app to create a timer.","timer"),

    // AI INTELLIGENCE (50-61)
    JarvisFeature(50,"AI intelligence","Gemini AI chat","Use the JARVIS text chat panel below.","info","Use the chat panel above this directory."),
    JarvisFeature(51,"AI intelligence","Conversation context","AI chat sends recent messages as context during the session.","info","Text chat conversation context is session-based in this build."),
    JarvisFeature(52,"AI intelligence","Saved preferences","Set speech language and AI endpoint in settings.","settings","app"),
    JarvisFeature(53,"AI intelligence","AI endpoint settings","Configure the secure HTTPS AI Worker endpoint.","settings","app"),
    JarvisFeature(54,"AI intelligence","Ask a question","Enter a question in the JARVIS text chat panel.","info","Use the chat panel above this directory."),
    JarvisFeature(55,"AI intelligence","Summarise text","Share or paste text into the JARVIS chat and ask for a summary.","info","Text summarisation is handled by the connected AI model."),
    JarvisFeature(56,"AI intelligence","Translate text","Ask the connected AI to translate text.","info","Translation is handled by the connected AI model."),
    JarvisFeature(57,"AI intelligence","Explain a topic","Ask the connected AI for a step-by-step explanation.","info","Topic explanations are handled by the connected AI model."),
    JarvisFeature(58,"AI intelligence","Brainstorm ideas","Ask the connected AI to generate ideas.","info","Idea generation is handled by the connected AI model."),
    JarvisFeature(59,"AI intelligence","Writing assistance","Ask the connected AI to draft or edit text.","info","Writing assistance is handled by the connected AI model."),
    JarvisFeature(60,"AI intelligence","Automatic language replies","Choose Auto in speech language settings.","settings","app"),
    JarvisFeature(61,"AI intelligence","AI connection status","Open AI settings to review the configured endpoint.","settings","app"),

    // GOOGLE & WEB (62-71)
    JarvisFeature(62,"Google & web","Google search","Search the web with Google.","search"),
    JarvisFeature(63,"Google & web","Google Maps","Open Google Maps.","url","https://maps.google.com"),
    JarvisFeature(64,"Google & web","Google Translate","Open Google Translate.","url","https://translate.google.com"),
    JarvisFeature(65,"Google & web","Gmail","Launch Gmail if installed.","app","com.google.android.gm"),
    JarvisFeature(66,"Google & web","Google Drive","Launch Google Drive if installed.","app","com.google.android.apps.docs"),
    JarvisFeature(67,"Google & web","Google Calendar","Launch Google Calendar if installed.","app","com.google.android.calendar"),
    JarvisFeature(68,"Google & web","Google Photos","Launch Google Photos if installed.","app","com.google.android.apps.photos"),
    JarvisFeature(69,"Google & web","Open a website","Enter a website address to open it.","prompt_url","https://"),
    JarvisFeature(70,"Google & web","Search news","Search Google News.","url","https://news.google.com"),
    JarvisFeature(71,"Google & web","Search images","Search Google Images.","url","https://images.google.com"),

    // REMINDERS & CALENDAR (72-81)
    JarvisFeature(72,"Reminders & calendar","Create calendar event","Open calendar event editor; review and save.","calendar"),
    JarvisFeature(73,"Reminders & calendar","Create reminder","Create a calendar event as a reminder.","calendar"),
    JarvisFeature(74,"Reminders & calendar","View calendar","Open calendar app.","app","com.google.android.calendar"),
    JarvisFeature(75,"Reminders & calendar","Set alarm reminder","Open clock alarm editor.","alarm"),
    JarvisFeature(76,"Reminders & calendar","Set countdown timer","Open clock timer editor.","timer"),
    JarvisFeature(77,"Reminders & calendar","Birthday reminder","Create a calendar event; add birthday details yourself.","calendar"),
    JarvisFeature(78,"Reminders & calendar","Meeting reminder","Create a calendar event; confirm details in calendar.","calendar"),
    JarvisFeature(79,"Reminders & calendar","Daily schedule","Open calendar agenda.","app","com.google.android.calendar"),
    JarvisFeature(80,"Reminders & calendar","Task planning","Ask JARVIS AI to help organise a task list.","info","Task planning is available through AI chat; persistent task storage is not yet included."),
    JarvisFeature(81,"Reminders & calendar","Upcoming events","Open calendar to review upcoming events.","app","com.google.android.calendar"),

    // MUSIC & MEDIA (82-89)
    JarvisFeature(82,"Music & media","Open music player","Launch a music app if installed.","app","com.spotify.music"),
    JarvisFeature(83,"Music & media","Spotify","Launch Spotify if installed.","app","com.spotify.music"),
    JarvisFeature(84,"Music & media","YouTube Music","Launch YouTube Music if installed.","app","com.google.android.apps.youtube.music"),
    JarvisFeature(85,"Music & media","Search music","Search the web for music.","search"),
    JarvisFeature(86,"Music & media","Open audio files","Open Android audio file picker.","audio"),
    JarvisFeature(87,"Music & media","Open video files","Open Android video file picker.","video"),
    JarvisFeature(88,"Music & media","Media volume settings","Open sound settings to adjust volume.","settings","sound"),
    JarvisFeature(89,"Music & media","Media app selection","Open Android app settings to choose your media app.","settings","app"),

    // FILES & DOCUMENTS (90-99)
    JarvisFeature(90,"Files & documents","Browse files","Open Android document picker.","files"),
    JarvisFeature(91,"Files & documents","Open images","Open Android image picker.","images"),
    JarvisFeature(92,"Files & documents","Open PDF or document","Open Android document picker.","files"),
    JarvisFeature(93,"Files & documents","Create or edit text","Open a text editor if installed; otherwise choose a compatible app.","share",""),
    JarvisFeature(94,"Files & documents","Share a file","Open Android file picker; choose a file and share it.","share_file"),
    JarvisFeature(95,"Files & documents","Share selected text","Open Android share sheet for text.","share"),
    JarvisFeature(96,"Files & documents","Download manager","Open the downloads folder.","downloads"),
    JarvisFeature(97,"Files & documents","Storage settings","Open Android storage settings.","settings","storage"),
    JarvisFeature(98,"Files & documents","Document scanning","Open camera; use a scanner app if installed.","camera"),
    JarvisFeature(99,"Files & documents","Cloud drive","Open Google Drive if installed.","app","com.google.android.apps.docs"),

    // MAPS & NAVIGATION (100-107)
    JarvisFeature(100,"Maps & navigation","Open navigation","Open Google Maps.","url","https://maps.google.com"),
    JarvisFeature(101,"Maps & navigation","Search a place","Search Google Maps for a place.","maps_search"),
    JarvisFeature(102,"Maps & navigation","Navigate to a destination","Enter destination; review route in Maps.","maps_search"),
    JarvisFeature(103,"Maps & navigation","Find nearby places","Open Maps nearby search.","url","https://www.google.com/maps/search/nearby"),
    JarvisFeature(104,"Maps & navigation","Find petrol stations","Search Maps for petrol stations.","maps_query","petrol stations"),
    JarvisFeature(105,"Maps & navigation","Find hospitals","Search Maps for hospitals.","maps_query","hospitals"),
    JarvisFeature(106,"Maps & navigation","Find restaurants","Search Maps for restaurants.","maps_query","restaurants"),
    JarvisFeature(107,"Maps & navigation","Find ATMs","Search Maps for ATMs.","maps_query","ATMs")
)

@Composable
fun FeatureHub(context: Context) {
    var filter by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }
    var feedback by remember { mutableStateOf("107 capability entry points") }
    var prompt by remember { mutableStateOf("") }

    val groups = listOf("All") + jarvisFeatures.map { it.group }.distinct()
    val visible = jarvisFeatures.filter {
        (selectedGroup == "All" || it.group == selectedGroup) &&
            (filter.isBlank() ||
                it.title.contains(filter, true) ||
                it.detail.contains(filter, true) ||
                it.group.contains(filter, true))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("JARVIS FEATURE SYSTEM", color = Color(0xFF53D9FF), fontSize = 20.sp)
        Text("107 capability entry points • Android permission and app support applies",
            color = Color.LightGray, fontSize = 12.sp)

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Search features") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { selectedGroup = "All" }) { Text("All 107") }
            Button(onClick = { selectedGroup = "Voice & wake word" }) { Text("Voice") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { selectedGroup = "WhatsApp" }) { Text("WhatsApp") }
            Button(onClick = { selectedGroup = "YouTube" }) { Text("YouTube") }
            Button(onClick = { selectedGroup = "Phone control" }) { Text("Phone") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { selectedGroup = "AI intelligence" }) { Text("AI") }
            Button(onClick = { selectedGroup = "Google & web" }) { Text("Web") }
            Button(onClick = { selectedGroup = "Reminders & calendar" }) { Text("Tasks") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { selectedGroup = "Music & media" }) { Text("Media") }
            Button(onClick = { selectedGroup = "Files & documents" }) { Text("Files") }
            Button(onClick = { selectedGroup = "Maps & navigation" }) { Text("Maps") }
        }

        Text(feedback, color = Color(0xFF72DFFF), fontSize = 12.sp)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visible, key = { it.number }) { feature ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101E31))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("#${feature.number}  ${feature.title}",
                            color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Text(feature.group, color = Color(0xFF53D9FF), fontSize = 11.sp)
                        Text(feature.detail, color = Color.LightGray, fontSize = 12.sp)
                        Button(
                            onClick = {
                                try {
                                    val extra = prompt.trim()
                                    if (feature.action == "prompt_url" && extra.isBlank()) {
                                        feedback = "Enter a number, search phrase or website in the field below."
                                    } else {
                                        feedback = runFeature(context, feature, extra)
                                    }
                                } catch (e: Exception) {
                                    feedback = "Couldn't open this feature: ${e.message ?: "unsupported on this device"}"
                                }
                            }
                        ) { Text("Open") }
                    }
                }
            }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Optional input: search, number or destination") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text("Safety: JARVIS opens the official app or Android confirmation screen. It does not auto-send messages, place calls, or silently change protected settings.",
            color = Color.Gray, fontSize = 11.sp)
    }
}

private fun runFeature(context: Context, feature: JarvisFeature, input: String): String {
    val intent = when (feature.action) {
        "info" -> return feature.argument
        "voice" -> return "Tap the floating JARVIS orb to start voice recognition."
        "tts" -> Intent(Settings.ACTION_TEXT_TO_SPEECH_SETTINGS)
        "language" -> {
            context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                .edit().putString("speech_language", feature.argument).apply()
            return "${feature.title} selected. Restart the floating assistant to apply it."
        }
        "stop" -> {
            context.stopService(Intent(context, OverlayService::class.java))
            return "Floating assistant stop requested."
        }
        "app" -> {
            val launch = context.packageManager.getLaunchIntentForPackage(feature.argument)
                ?: return "That app is not installed."
            launch
        }
        "app_settings" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, feature.argument)
        "settings" -> when (feature.argument) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "app" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"))
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "voice" -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        "dial" -> Intent(Intent.ACTION_DIAL)
        "sms" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        "alarm" -> Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        "timer" -> Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        "calendar" -> Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, feature.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, "Created with JARVIS. Review and save.")
        "search" -> Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(input.ifBlank { feature.title })))
        "youtube_search" -> Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(input.ifBlank { "music" })))
        "maps_search" -> Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/" + Uri.encode(input.ifBlank { "places" })))
        "maps_query" -> Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/" + Uri.encode(feature.argument)))
        "url" -> Intent(Intent.ACTION_VIEW, Uri.parse(feature.argument))
        "prompt_url" -> {
            val target = if (feature.argument == "https://wa.me/") {
                val digits = input.filter { it.isDigit() }
                if (digits.isBlank()) return "Enter the recipient number with country code."
                "https://wa.me/$digits"
            } else {
                val raw = if (input.startsWith("http")) input else "https://$input"
                raw
            }
            Intent(Intent.ACTION_VIEW, Uri.parse(target))
        }
        "share" -> Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, input.ifBlank { "Shared from JARVIS" }),
            "Share using"
        )
        "share_file" -> Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        "files" -> Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        "images" -> Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        "audio" -> Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
        "video" -> Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("video/*").addCategory(Intent.CATEGORY_OPENABLE)
        "downloads" -> Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"), "resource/folder")
        else -> return "This feature is not available yet."
    }

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        "Opened: ${feature.title}. Complete or confirm the action in the selected app."
    } catch (_: ActivityNotFoundException) {
        "No compatible app is installed for this action."
    }
}
