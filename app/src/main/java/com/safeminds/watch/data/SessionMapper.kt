package com.safeminds.watch.data

import com.google.gson.Gson
import java.util.UUID

class SessionMapper {
    private val gson = Gson()

    fun mapToSessionData(rawJson: String): SessionData{
        val original = gson.fromJson(rawJson, Map::class.java)
        val summaryJson = gson.toJson(original["summary"])
        val summary = gson.fromJson(summaryJson, com.safeminds.watch.model.SessionSummary::class.java)

        val epochsJson = gson.toJson(original["epochs"])

        return SessionData(
            dataId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sessionType = original["sessionType"]?.toString()?:"UNKNOWN",
            userId = "user_001",

            summary = gson.fromJson(gson.toJson(original["summary"])),
            summary = summary,
            epochs = listOf(epochsJson)

        )
    }
}