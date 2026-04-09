package com.safeminds.watch.sessionTransfer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

object TransferManualTest {

    private const val TAG = "TransferManualTest"



    fun testRecoverableError(context: Context) {
        val repository = SharedPrefsRepository(context)
        val controller = TransferController(repository, TestRecoverableErrorSender())

        val sessionId = "recoverable_test_001"

        CoroutineScope(Dispatchers.IO).launch {
            controller.createSessionRequest(sessionId)
            controller.sendAgain(sessionId)

            val record = repository.getTransferredRecord(sessionId)
            Log.d(TAG, "testRecoverableError -> $record")
        }
    }
}