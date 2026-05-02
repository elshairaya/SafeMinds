package com.safeminds.watch.sessionTransfer

import android.util.Base64
import org.json.JSONObject

object SessionPayloadCodec {

    data class SessionRequest(
        val sessionId: String,
        val sessionBytes: ByteArray,
        val sessionChecksum: String,
        val sessionSize: Int
    )

    data class SessionAck(
        val accepted: Boolean,
        val sessionId: String?,
        val checksum: String?,
        val size: Int?,
        val reason: String?
    )

    fun createRequestPayload(sessionId: String, sessionBytes: ByteArray): ByteArray {
        val checksum = SessionIntegrity.sha256(sessionBytes)
        return JSONObject().apply {
            put("version", 1)
            put("sessionId", sessionId)
            put("sessionChecksum", checksum)
            put("sessionSize", sessionBytes.size)
            put("sessionBase64", Base64.encodeToString(sessionBytes, Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodeRequestPayload(payload: ByteArray): SessionRequest {
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        val sessionId = json.getString("sessionId")
        val sessionChecksum = json.getString("sessionChecksum")
        val sessionSize = json.getInt("sessionSize")
        val sessionBytes = Base64.decode(json.getString("sessionBase64"), Base64.DEFAULT)
        return SessionRequest(
            sessionId = sessionId,
            sessionBytes = sessionBytes,
            sessionChecksum = sessionChecksum,
            sessionSize = sessionSize
        )
    }

    fun createAckPayload(
        accepted: Boolean,
        sessionId: String?,
        checksum: String?,
        size: Int?,
        reason: String?
    ): ByteArray {
        return JSONObject().apply {
            put("accepted", accepted)
            put("sessionId", sessionId)
            put("checksum", checksum)
            put("size", size)
            put("reason", reason)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodeAckPayload(payload: ByteArray): SessionAck {
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        return SessionAck(
            accepted = json.optBoolean("accepted", false),
            sessionId = json.optString("sessionId", null),
            checksum = json.optString("checksum", null),
            size = if (json.has("size") && !json.isNull("size")) json.getInt("size") else null,
            reason = json.optString("reason", null)
        )
    }
}
