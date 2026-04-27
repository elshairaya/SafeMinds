package com.safeminds.watch.sessionTransfer

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.safeminds.watch.logging.AppLogger

class WearConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "WearConnectionManager"
    }

    fun getReachablePhoneNode(): Node? {
        return try {
            logAllCapabilityNodes()

            val capabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(context).getCapability(
                    SessionTransferContract.PHONE_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE
                )
            )

            val candidate = capabilityInfo.nodes
                .sortedByDescending { it.isNearby }
                .firstOrNull()

            if (candidate == null) {
                AppLogger.w(TAG, "No reachable phone app found for capability ${SessionTransferContract.PHONE_CAPABILITY}")
            } else {
                AppLogger.i(TAG, "Reachable phone node found: ${candidate.displayName} (${candidate.id})")
            }
            candidate
        } catch (exception: Exception) {
            AppLogger.e(TAG, "Failed to resolve phone node", exception)
            null
        }
    }

    fun logAllCapabilityNodes() {
        try {
            val capabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(context)
                    .getCapability(
                        SessionTransferContract.PHONE_CAPABILITY,
                        CapabilityClient.FILTER_ALL
                    )
            )

            AppLogger.d(TAG, "ALL nodes for capability: ${capabilityInfo.nodes.size}")

            capabilityInfo.nodes.forEach { node ->
                AppLogger.d(TAG, "Node: ${node.displayName}, nearby=${node.isNearby}, id=${node.id}")
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch ALL capability nodes", e)
        }
    }


}
