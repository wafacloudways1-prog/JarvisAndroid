package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val root = rootInActiveWindow ?: return
            val parts = mutableListOf<String>()
            fun walk(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null || parts.size >= 80) return
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { parts += it }
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts += it }
                for (i in 0 until node.childCount) walk(node.getChild(i))
            }
            walk(root)
            val output = parts.distinct().joinToString(". ").take(3500)
            sendBroadcast(Intent("com.example.jarvis.SCREEN_TEXT").setPackage(packageName).putExtra("screen_text", output))
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(receiver, IntentFilter("com.example.jarvis.READ_SCREEN"), RECEIVER_NOT_EXPORTED)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
}
