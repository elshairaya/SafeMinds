package com.safeminds.watch.data

import android.content.Context

class DataDeduplicator(context: Context) {
    private val prefs = context.getSharedPreferences("deduplication_watch", Context.MODE_PRIVATE)
    fun isDuplicate(id: String): Boolean{
        return prefs.contains(id)
    }
    fun MarkAsSent(id: String){
        prefs.edit().putBoolean(id, true).apply()
    }
}