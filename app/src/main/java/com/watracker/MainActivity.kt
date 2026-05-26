package com.watracker

import android.app.AlertDialog
import android.content.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var summaryContainer: LinearLayout
    private lateinit var logContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var todayTitle: TextView

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(WaNotificationListener.PREFS_NAME, MODE_PRIVATE)

        summaryContainer = findViewById(R.id.summaryContainer)
        logContainer     = findViewById(R.id.logContainer)
        emptyText        = findViewById(R.id.emptyText)
        todayTitle       = findViewById(R.id.todayTitle)

        // Seed default members if none set
        if (prefs.getString(WaNotificationListener.KEY_MEMBERS, null) == null) {
            val defaults = JSONArray().apply {
                put("Sonu"); put("Ram"); put("Sunny")
            }
            prefs.edit().putString(WaNotificationListener.KEY_MEMBERS, defaults.toString()).apply()
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { confirmClear() }

        checkPermission()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, IntentFilter(WaNotificationListener.ACTION_REFRESH), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(refreshReceiver, IntentFilter(WaNotificationListener.ACTION_REFRESH))
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(refreshReceiver)
    }

    private fun checkPermission() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        if (!listeners.contains(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("WA Tracker needs Notification Access to read WhatsApp messages.\n\nTap OK → find 'WA Tracker' → Enable it.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun refresh() {
        val today   = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val logsRaw = prefs.getString(WaNotificationListener.KEY_LOGS, "[]") ?: "[]"
        val allLogs = JSONArray(logsRaw)
        val membersRaw = prefs.getString(WaNotificationListener.KEY_MEMBERS, "[]") ?: "[]"
        val members = JSONArray(membersRaw)

        // Filter today's logs
        val todayLogs = mutableListOf<JSONObject>()
        for (i in 0 until allLogs.length()) {
            val obj = allLogs.getJSONObject(i)
            if (obj.getString("date") == today) todayLogs.add(obj)
        }

        // --- Summary cards ---
        summaryContainer.removeAllViews()
        for (i in 0 until members.length()) {
            val name  = members.getString(i)
            val count = todayLogs.count { it.getString("member").equals(name, true) }
            summaryContainer.addView(makeSummaryCard(name, count))
        }

        // --- Today's log entries (newest first) ---
        logContainer.removeAllViews()
        todayTitle.text = "Today's Messages (${todayLogs.size})"

        if (todayLogs.isEmpty()) {
            emptyText.visibility = View.VISIBLE
        } else {
            emptyText.visibility = View.GONE
            for (entry in todayLogs.reversed()) {
                logContainer.addView(makeLogRow(entry))
            }
        }
    }

    private fun makeSummaryCard(name: String, count: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.card_bg)
            val lp = LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(10)
            layoutParams = lp
        }
        card.addView(TextView(this).apply {
            text = count.toString()
            textSize = 28f
            setTextColor(0xFF4FC3F7.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = name
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
        })
        card.addView(TextView(this).apply {
            text = "msgs today"
            textSize = 10f
            setTextColor(0xFF888888.toInt())
        })
        return card
    }

    private fun makeLogRow(entry: JSONObject): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.row_bg)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }

        // Top row: member name + time
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        topRow.addView(TextView(this).apply {
            text = entry.getString("member")
            textSize = 14f
            setTextColor(0xFF4FC3F7.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = entry.getString("time")
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        })
        row.addView(topRow)

        // Group name
        row.addView(TextView(this).apply {
            text = "📌 ${entry.getString("group")}"
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(3)
            layoutParams = lp
        })
        return row
    }

    private fun showSettings() {
        val membersRaw = prefs.getString(WaNotificationListener.KEY_MEMBERS, "[]") ?: "[]"
        val members    = JSONArray(membersRaw)
        val namesList  = (0 until members.length()).map { members.getString(it) }.toMutableList()

        val dialog = AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }

        val listView = ListView(this)
        val adapter  = ArrayAdapter(this, android.R.layout.simple_list_item_1, namesList)
        listView.adapter = adapter
        listView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(200)
        )
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setTitle("Remove ${namesList[pos]}?")
                .setPositiveButton("Remove") { _, _ ->
                    namesList.removeAt(pos)
                    adapter.notifyDataSetChanged()
                    saveMembers(namesList)
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        layout.addView(TextView(this).apply {
            text = "Tracked Members  (long-press to remove)"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, dp(6))
        })
        layout.addView(listView)

        val editText = EditText(this).apply {
            hint = "Add member name..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        layout.addView(editText)

        dialog.setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty() && !namesList.any { it.equals(name, true) }) {
                    namesList.add(name)
                    saveMembers(namesList)
                    refresh()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveMembers(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(WaNotificationListener.KEY_MEMBERS, arr.toString()).apply()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear all logs?")
            .setMessage("This will delete all message history.")
            .setPositiveButton("Clear") { _, _ ->
                prefs.edit().putString(WaNotificationListener.KEY_LOGS, "[]").apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
