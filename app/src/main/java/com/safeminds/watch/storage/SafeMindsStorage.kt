package com.safeminds.watch.storage

import android.content.Context
import android.content.SharedPreferences
import com.safeminds.watch.logging.AppLogger
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SafeMindsStorage(private val context: Context) {

    companion object {
        private const val TAG = "SafeMindsStorage"
        private const val INDEX_PREFS = "SafeMindsStorageIndex"
    }

    private val indexPreferences: SharedPreferences =
        context.getSharedPreferences(INDEX_PREFS, Context.MODE_PRIVATE)

    private fun getTodayFolder(): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val baseDir = File(context.filesDir, "safeminds")
        val todayDir = File(baseDir, date)

        if (!todayDir.exists()) {
            todayDir.mkdirs()
        }

        return todayDir
    }

    private fun saveSessionPath(sessionId: String, absolutePath: String) {
        indexPreferences.edit()
            .putString(sessionId, absolutePath)
            .apply()
        AppLogger.d(TAG, "Indexed session file for $sessionId -> $absolutePath")
    }

    fun writeNightSession(sessionId: String, data: JSONObject): File {
        val folder = getTodayFolder()
        val file = File(folder, "night_session_$sessionId.json")
        file.writeText(data.toString(2))
        saveSessionPath(sessionId, file.absolutePath)
        AppLogger.i(TAG, "Night session saved: ${file.absolutePath}")
        return file
    }

    fun writeHourlyCheck(sessionId: String, data: JSONObject): File {
        val folder = getTodayFolder()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val file = File(folder, "hourly_check_${currentHour}_$sessionId.json")
        file.writeText(data.toString(2))
        saveSessionPath(sessionId, file.absolutePath)
        AppLogger.i(TAG, "Hourly session saved: ${file.absolutePath}")
        return file
    }

    fun getSessionFile(sessionId: String): File? {
        val indexedPath = indexPreferences.getString(sessionId, null)
        if (indexedPath != null) {
            val indexedFile = File(indexedPath)
            if (indexedFile.exists()) {
                return indexedFile
            }
        }

        val root = File(context.filesDir, "safeminds")
        if (!root.exists()) {
            return null
        }

        val fallback = root.walkTopDown()
            .firstOrNull { it.isFile && it.name.contains(sessionId) }

        if (fallback != null) {
            saveSessionPath(sessionId, fallback.absolutePath)
        }
        return fallback
    }
}
