package com.safeminds.watch.sessionTransfer

import java.security.MessageDigest

object SessionIntegrity {
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
