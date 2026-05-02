package com.safeminds.watch.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val LOG_DIRECTORY = "safeminds/logs"
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun d(tag: String, message: String) {
        log("D", tag, message, null)
    }

    fun i(tag: String, message: String) {
        log("I", tag, message, null)
    }

    fun w(tag: String, message: String) {
        log("W", tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("E", tag, message, throwable)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val fullMessage = buildString {
            append(message)
            if (throwable != null) {
                append(" | ")
                append(Log.getStackTraceString(throwable))
            }
        }

        when (level) {
            "D" -> Log.d(tag, fullMessage)
            "I" -> Log.i(tag, fullMessage)
            "W" -> Log.w(tag, fullMessage)
            else -> Log.e(tag, fullMessage)
        }

        val context = appContext ?: return
        synchronized(lock) {
            try {
                val directory = File(context.filesDir, LOG_DIRECTORY)
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val logFile = File(directory, "app_$day.log")
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                logFile.appendText("$time $level/$tag: $message\n")
                if (throwable != null) {
                    logFile.appendText(Log.getStackTraceString(throwable))
                    logFile.appendText("\n")
                }
            } catch (_: Exception) {
                // Do not crash app because of logging failure.
            }
        }
    }
}
