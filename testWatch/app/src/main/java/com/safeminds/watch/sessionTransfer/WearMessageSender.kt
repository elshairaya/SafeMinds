package com.safeminds.watch.sessionTransfer

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.safeminds.watch.logging.AppLogger
import com.safeminds.watch.storage.SafeMindsStorage
import kotlinx.coroutines.tasks.await
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
            return TransferResults.CoverableError(
                "Failed to read session file: ${exception.message}"
            )
        }

        if (sessionBytes.isEmpty()) {
            return TransferResults.UncoverableError("Session file is empty")
        }

        try {
            JSONObject(sessionBytes.toString(Charsets.UTF_8))
        } catch (exception: Exception) {
            return TransferResults.UncoverableError("Session file is not valid JSON")
        }

        val requestBytes = SessionPayloadCodec.createRequestPayload(
            sessionID,
            sessionBytes
        )

        if (requestBytes.size > SessionTransferContract.MAX_RPC_PAYLOAD_BYTES) {
            return TransferResults.UncoverableError(
                "Session payload too large for RPC transport: ${requestBytes.size} bytes"
            )
        }

        val node = connectionManager.getReachablePhoneNode()
            ?: return TransferResults.CoverableError("No reachable paired phone app")

        return try {
            Wearable.getMessageClient(context)
                .sendMessage(
                    node.id,
                    SessionTransferContract.SESSION_RPC_PATH,
                    requestBytes
                )
                .await()

            AppLogger.i(TAG, "Session message sent for $sessionID")
            TransferResults.Success

        } catch (exception: Exception) {
            AppLogger.e(TAG, "Message send failed for $sessionID", exception)
            TransferResults.CoverableError(
                "Transfer message failed: ${exception.message}"
            )
        }
    }
}