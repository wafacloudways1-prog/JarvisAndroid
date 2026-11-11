package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val ACTION_READ_SCREEN =
            "com.example.jarvis.READ_SCREEN"

        private const val ACTION_SCREEN_TEXT =
            "com.example.jarvis.SCREEN_TEXT"
    }

    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val root = rootInActiveWindow

            if (root == null) {
                sendScreenText("No readable screen is currently available.")
                return
            }

            val parts = mutableListOf<String>()

            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null || parts.size >= 80) return

                node.text?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parts.add(it) }

                node.contentDescription?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parts.add(it) }

                for (i in 0 until node.childCount) {
                    if (parts.size >= 80) break
                    walk(node.getChild(i))
                }
            }

            try {
                walk(root)

                val output = parts
                    .distinct()
                    .joinToString(". ")
                    .take(3500)

                sendScreenText(
                    output.ifBlank { "No readable text found." }
                )
            } finally {
                root.recycle()
            }
        }
    }

    private fun sendScreenText(text: String) {
        val result = Intent(ACTION_SCREEN_TEXT)
            .setPackage(packageName)
            .putExtra("screen_text", text)

        sendBroadcast(result)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val filter = IntentFilter(ACTION_READ_SCREEN)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen content is read only when JARVIS explicitly requests it.
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }

        receiverRegistered = false
        super.onDestroy()
    }
}
