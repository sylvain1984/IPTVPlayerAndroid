package com.iptv.player.data

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RTCTokenGenerator {

    fun generateViewerToken(
        appId: String, appKey: String, roomId: String, userId: String,
        expireSeconds: Int = 86400
    ): String {
        val expireAt = (System.currentTimeMillis() / 1000).toInt() + expireSeconds
        val privs = listOf(0, 1, 2, 3, 4, 5).map { it to expireAt }
        return build(appId, appKey, roomId, userId, privs, expireAt)
    }

    private fun build(
        appId: String, appKey: String, roomId: String, userId: String,
        privileges: List<Pair<Int, Int>>, expireAt: Int
    ): String {
        val nonce    = (Math.random() * 0xFFFFFFFFL).toLong().toInt()
        val issuedAt = (System.currentTimeMillis() / 1000).toInt()

        // msg — all little-endian
        val roomIdBytes  = roomId.toByteArray(Charsets.UTF_8)
        val userIdBytes  = userId.toByteArray(Charsets.UTF_8)
        val msgSize      = 4 + 4 + 4 + 2 + roomIdBytes.size + 2 + userIdBytes.size +
                           2 + privileges.size * 6
        val msg = ByteBuffer.allocate(msgSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(nonce); putInt(issuedAt); putInt(expireAt)
            putShort(roomIdBytes.size.toShort()); put(roomIdBytes)
            putShort(userIdBytes.size.toShort()); put(userIdBytes)
            putShort(privileges.size.toShort())
            privileges.forEach { (k, v) -> putShort(k.toShort()); putInt(v) }
        }.array()

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(appKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = mac.doFinal(msg)

        // content = uint16LE(msgLen) + msg + uint16LE(sigLen) + sig
        val content = ByteBuffer.allocate(2 + msg.size + 2 + sig.size)
            .order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(msg.size.toShort()); put(msg)
                putShort(sig.size.toShort()); put(sig)
            }.array()

        return "001$appId${Base64.encodeToString(content, Base64.NO_WRAP)}"
    }
}
