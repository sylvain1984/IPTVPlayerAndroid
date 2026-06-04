package com.iptv.player.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RTCTokenCredentials(
    val appId: String,
    val token: String
)

// Generates RTC tokens locally — no Cloudflare dependency.
object RTCTokenGenerator {
    private const val APP_ID  = "6a13b1373d860b0617f988aa"
    private const val APP_KEY = "221fb57fe116497b9201c3c635f1b23c"

    fun generate(roomId: String, userId: String, expireSeconds: Int = 86400): RTCTokenCredentials {
        val issuedAt = (System.currentTimeMillis() / 1000).toInt()
        val expireAt = issuedAt + expireSeconds
        val nonce    = SecureRandom().nextInt()

        val roomBytes = roomId.toByteArray(Charsets.UTF_8)
        val userBytes = userId.toByteArray(Charsets.UTF_8)
        // 4+4+4 + (2+room) + (2+user) + 2 + 6*(2+4)
        val msgSize = 12 + 2 + roomBytes.size + 2 + userBytes.size + 2 + 36
        val msgBuf = ByteBuffer.allocate(msgSize).order(ByteOrder.LITTLE_ENDIAN)
        msgBuf.putInt(nonce)
        msgBuf.putInt(issuedAt)
        msgBuf.putInt(expireAt)
        msgBuf.putShort(roomBytes.size.toShort())
        msgBuf.put(roomBytes)
        msgBuf.putShort(userBytes.size.toShort())
        msgBuf.put(userBytes)
        msgBuf.putShort(6.toShort())
        for (i in 0 until 6) {
            msgBuf.putShort(i.toShort())
            msgBuf.putInt(expireAt)
        }
        val msg = msgBuf.array()

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(APP_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = mac.doFinal(msg)

        val contentBuf = ByteBuffer.allocate(2 + msg.size + 2 + sig.size).order(ByteOrder.LITTLE_ENDIAN)
        contentBuf.putShort(msg.size.toShort())
        contentBuf.put(msg)
        contentBuf.putShort(sig.size.toShort())
        contentBuf.put(sig)

        val token = "001$APP_ID${Base64.getEncoder().encodeToString(contentBuf.array())}"
        return RTCTokenCredentials(APP_ID, token)
    }
}
