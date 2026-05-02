package com.safeminds.watch.data

import android.content.Context
import android.util.Log
import java.io.File

class SessionReader(private val context: Context) {
    fun getAllJsonFiles(): List<File>{
        val dir = context.filesDir
        val files = dir.walkTopDown().filter {
            it.isFile && it.name.endsWith(".json")
        }.toList()

        Log.d("SafeMinds", "Found ${files.size} JSON files")
        return files
    }

    fun readJson(file: File): String{
        return try {
            file.readText()
        }
        catch (e: Exception){
            Log.e("SafeMinds", "Error reading file: ${file.name}", e)
            ""
        }
    }
}