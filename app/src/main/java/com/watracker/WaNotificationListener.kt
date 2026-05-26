package com.watracker

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class WaNotificationListener : NotificationListenerService() {

    companion object {
        const val PREFS_NAME = "wa_tracker"
        const val KEY_LOGS = "logs"
        const val KEY_MEMBERS = "members"
        const val ACTION_REFRESH = "com.watracker.REFRESH"
        val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WA_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // WhatsApp group message format → "SenderName: message text"
        // title = Group name, text = "Sender: message"
        val colonIdx = text.indexOf(": ")
        if (colonIdx <= 0) return  // Not a group message, skip

        val sender    = text.substring(0, colonIdx).trim()
        val groupName = title.trim()

        // Load tracked members
        val prefs   = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_MEMBERS, "[]") ?: "[]"
        val members = JSONArray(rawJson)

        // Check if sender matches any tracked member (case-insensitive)
        var matched = false
        for (i in 0 until members.length()) {
            if (members.getString(i).trim().equals(sender, ignoreCase = true)) {
                matched = true
                break
            }
        }
        if (!matched) return

        // Build log entry
        val now = Date()
        val entry = JSONObject().apply {
            put("member",    sender)
            put("group",     groupName)
            put("time",      timeFmt.format(now))
            put("date",      dateFmt.format(now))
            put("timestamp", now.time)
        }

        // Save to prefs (keep latest 1000)
        val logsRaw = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        val logs    = JSONArray(logsRaw)
        logs.put(entry)

        val trimmed = if (logs.length() > 1000) {
            val arr = JSONArray()
            for (i in (logs.length() - 1000) until logs.length()) arr.put(logs.get(i))
            arr
        } else logs

        prefs.edit().putString(KEY_LOGS, trimmed.toString()).apply()

        // Tell MainActivity to refresh
        sendBroadcast(Intent(ACTION_REFRESH))
    }
}
