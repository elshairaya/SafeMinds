package com.safeminds.watch.storage

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeMindsStorage(private val context: Context) {

    private val TAG = "SafeMindsStorage"

    private fun getTodayFolder(): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val baseDir = File(context.filesDir, "safeminds")
        val todayDir = File(baseDir, date)

        if (!todayDir.exists()) {
            todayDir.mkdirs()
        }

        return todayDir
    }

    fun writeNightSession(data: JSONObject) {
        try {
            val folder = getTodayFolder()
            val file = File(folder, "night_session.json")
            file.writeText(data.toString())
            Log.d(TAG, "Night session saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing night session", e)
        }
    }

    fun writeHourlyCheck(hourIndex: Int, data: JSONObject) {
        try {
            val folder = getTodayFolder()
            val file = File(folder, "hourly_check_$hourIndex.json")
            file.writeText(data.toString())
            Log.d(TAG, "Hourly check saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing hourly check", e)
        }
    }
}