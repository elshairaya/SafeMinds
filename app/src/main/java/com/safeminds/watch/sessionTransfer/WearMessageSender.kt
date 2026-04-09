package com.safeminds.watch.sessionTransfer

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.safeminds.watch.logging.AppLogger
import com.safeminds.watch.storage.SafeMindsStorage
import org.json.JSONObject

class WearMessageSender(
    private val context: Context,
    private val storage: SafeMindsStorage,
    private val connectionManager: WearConnectionManager = WearConnectionManager(context)
) : Sender {

    companion object {
        private const val TAG = "WearMessageSender"
    }

    override suspend fun sendSession(sessionID: String): TransferResults {
        AppLogger.i(TAG, "Preparing session transfer for $sessionID")

        val sessionFile = storage.getSessionFile(sessionID)
            ?: return TransferResults.UncoverableError("Session file not found for $sessionID")

        val sessionBytes = try {
            sessionFile.readBytes()
        } catch (exception: Exception) {
            return TransferResults.CoverableError("Failed to read session file: ${exception.message}")
        }

        if (sessionBytes.isEmpty()) {
            return TransferResults.UncoverableError("Session file is empty")
        }

        try {
            JSONObject(sessionBytes.toString(Charsets.UTF_8))
        } catch (exception: Exception) {
            return TransferResults.UncoverableError("Session file is not valid JSON")
        }

        val requestBytes = SessionPayloadCodec.createRequestPayload(sessionID, sessionBytes)
        if (requestBytes.size > SessionTransferContract.MAX_RPC_PAYLOAD_BYTES) {
            return TransferResults.UncoverableError(
                "Session payload too large for RPC transport: ${requestBytes.size} bytes"
            )
        }

        val node = connectionManager.getReachablePhoneNode()
            ?: return TransferResults.CoverableError("No reachable paired phone app")

        return try {
            val response = Tasks.await(
                Wearable.getMessageClient(context).sendRequest(
                    node.id,
                    SessionTransferContract.SESSION_RPC_PATH,
                    requestBytes
                )
            )

            val ack = SessionPayloadCodec.decodeAckPayload(response)
            val localChecksum = SessionIntegrity.sha256(sessionBytes)

            when {
                !ack.accepted -> {
                    AppLogger.w(TAG, "Phone rejected session $sessionID: ${ack.reason}")
                    TransferResults.CoverableError(ack.reason ?: "Phone rejected session")
                }

                ack.sessionId != sessionID -> {
                    AppLogger.w(TAG, "Ack session mismatch for $sessionID: ${ack.sessionId}")
                    TransferResults.CoverableError("Ack session ID mismatch")
                }

                ack.checksum != localChecksum -> {
                    AppLogger.w(TAG, "Checksum mismatch for $sessionID. local=$localChecksum remote=${ack.checksum}")
                    TransferResults.CoverableError("Checksum mismatch detected")
                }

                ack.size != sessionBytes.size -> {
                    AppLogger.w(TAG, "Size mismatch for $sessionID. local=${sessionBytes.size} remote=${ack.size}")
                    TransferResults.CoverableError("Payload size mismatch detected")
                }

                else -> {
                    AppLogger.i(TAG, "Session transfer confirmed for $sessionID")
                    TransferResults.Success
                }
            }
        } catch (exception: Exception) {
            AppLogger.e(TAG, "RPC send failed for $sessionID", exception)
            TransferResults.CoverableError("Transfer request failed: ${exception.message}")
        }
    }
}
