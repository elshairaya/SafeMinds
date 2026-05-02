package com.safeminds.watch.sessionTransfer

import com.safeminds.watch.logging.AppLogger

class TransferController(
    private val rep: TransferSessionRepository,
    private val sender: Sender,
    private val max: Int = 3,
) {
    companion object {
        private const val TAG = "TransferController"
    }

    fun createSessionRequest(sessionID: String) {
        AppLogger.d(TAG, "createSessionRequest() sessionID=$sessionID")

        val isExisting = rep.getTransferredRecord(sessionID)
        if (isExisting != null) {
            AppLogger.d(TAG, "Transfer record already exists for $sessionID")
            return
        }

        val newRecord = TransferEntry(
            sessionID = sessionID,
            status = DataTransferStatus.WAITING
        )
        rep.updateRecord(newRecord)
        AppLogger.i(TAG, "Created transfer record: $newRecord")
    }

    suspend fun sendAgain(sessionID: String) {
        AppLogger.d(TAG, "sendAgain() sessionID=$sessionID")

        val currentTransfer = rep.getTransferredRecord(sessionID) ?: run {
            AppLogger.w(TAG, "No transfer record found for $sessionID")
            return
        }

        if (currentTransfer.status != DataTransferStatus.WAITING) {
            AppLogger.d(TAG, "Transfer $sessionID ignored because status=${currentTransfer.status}")
            return
        }

        val record = currentTransfer.copy(
            status = DataTransferStatus.IN_PROGRESS,
            lastAttemptTime = System.currentTimeMillis(),
            error = null
        )
        rep.updateRecord(record)
        AppLogger.i(TAG, "Transfer moved to IN_PROGRESS: $record")

        when (val transferResult = sender.sendSession(sessionID)) {
            is TransferResults.Success -> {
                val success = record.copy(
                    status = DataTransferStatus.SUCCESS,
                    error = null
                )
                rep.updateRecord(success)
                AppLogger.i(TAG, "Transfer moved to SUCCESS: $success")
            }

            is TransferResults.CoverableError -> {
                val updatedRetryTimes = record.retryTimes + 1

                if (updatedRetryTimes >= max) {
                    val failed = record.copy(
                        status = DataTransferStatus.FAILED,
                        retryTimes = updatedRetryTimes,
                        error = transferResult.errorReason
                    )
                    rep.updateRecord(failed)
                    AppLogger.w(TAG, "Transfer moved to FAILED after retries: $failed")
                } else {
                    val waitingAgain = record.copy(
                        status = DataTransferStatus.WAITING,
                        retryTimes = updatedRetryTimes,
                        error = transferResult.errorReason
                    )
                    rep.updateRecord(waitingAgain)
                    AppLogger.w(TAG, "Recoverable failure. Transfer moved back to WAITING: $waitingAgain")
                }
            }

            is TransferResults.UncoverableError -> {
                val permanentFailed = record.copy(
                    status = DataTransferStatus.FAILED,
                    error = transferResult.errorReason
                )
                rep.updateRecord(permanentFailed)
                AppLogger.e(TAG, "Permanent failure. Transfer moved to FAILED: $permanentFailed")
            }
        }
    }

    suspend fun retryAllWaiting() {
        val waitingTransfers = rep.listWaitingRecords()
        AppLogger.d(TAG, "retryAllWaiting() count=${waitingTransfers.size}")

        for (record in waitingTransfers) {
            AppLogger.d(TAG, "Retrying session ${record.sessionID}")
            sendAgain(record.sessionID)
        }
    }
}
