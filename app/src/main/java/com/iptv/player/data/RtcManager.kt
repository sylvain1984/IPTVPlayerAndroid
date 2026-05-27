package com.iptv.player.data

import android.content.Context
import android.view.TextureView
import com.iptv.player.BuildConfig
import com.ss.bytertc.engine.RTCRoom
import com.ss.bytertc.engine.RTCRoomConfig
import com.ss.bytertc.engine.RTCVideo
import com.ss.bytertc.engine.UserInfo
import com.ss.bytertc.engine.VideoCanvas
import com.ss.bytertc.engine.data.RemoteStreamKey
import com.ss.bytertc.engine.data.StreamIndex
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler
import com.ss.bytertc.engine.type.ChannelProfile
import com.ss.bytertc.engine.type.MediaStreamType
import com.ss.bytertc.engine.type.RTCRoomStats
import com.ss.bytertc.engine.type.StreamRemoveReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

enum class RtcState { IDLE, CONNECTING, LIVE, ERROR }

class RtcManager(private val context: Context) {

    private var rtcVideo:  RTCVideo? = null
    private var rtcRoom:   RTCRoom?  = null
    private var currentRoomId: String = ""
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state     = MutableStateFlow(RtcState.IDLE)
    val state: StateFlow<RtcState> = _state.asStateFlow()

    private val _remoteUid = MutableStateFlow<String?>(null)
    val remoteUid: StateFlow<String?> = _remoteUid.asStateFlow()

    fun join(roomId: String) {
        if (_state.value != RtcState.IDLE) return
        _state.value = RtcState.CONNECTING
        currentRoomId = roomId
        val appId = BuildConfig.RTC_APP_ID
        if (appId.isBlank() || BuildConfig.RTC_TOKEN_URL.isBlank()) {
            _state.value = RtcState.ERROR
            return
        }
        val userId = "viewer_android_${(1000..9999).random()}"

        scope.launch {
            val credentials = try {
                RTCTokenService.fetch(client, roomId, userId, "viewer")
            } catch (_: Exception) {
                _state.value = RtcState.ERROR
                return@launch
            }

            if (rtcVideo == null) {
                rtcVideo = RTCVideo.createRTCVideo(context, credentials.appId, videoHandler, null, null)
            }

            rtcRoom = rtcVideo!!.createRTCRoom(roomId).also {
                it.setRTCRoomEventHandler(roomHandler)
            }

            val config = RTCRoomConfig(
                ChannelProfile.CHANNEL_PROFILE_COMMUNICATION,
                false, true, true
            )
            rtcRoom!!.joinRoom(credentials.token, UserInfo(userId, ""), config)
        }
    }

    fun renderRemote(uid: String, textureView: TextureView) {
        val streamKey = RemoteStreamKey(currentRoomId, uid, StreamIndex.STREAM_INDEX_MAIN)
        rtcVideo?.setRemoteVideoCanvas(
            streamKey,
            VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN)
        )
    }

    fun leave() {
        rtcRoom?.leaveRoom()
        rtcRoom?.destroy()
        rtcRoom        = null
        _state.value   = RtcState.IDLE
        _remoteUid.value = null
    }

    fun release() {
        leave()
        scope.cancel()
        RTCVideo.destroyRTCVideo()
        rtcVideo = null
    }

    private val videoHandler = object : IRTCVideoEventHandler() {
        override fun onError(err: Int) { _state.value = RtcState.ERROR }
    }

    private val roomHandler = object : IRTCRoomEventHandler() {
        override fun onRoomStateChanged(roomId: String, uid: String, state: Int, extraInfo: String) {
            if (state != 0) _state.value = RtcState.ERROR
        }

        override fun onUserPublishStream(uid: String, type: MediaStreamType) {
            rtcRoom?.subscribeStream(uid, MediaStreamType.RTC_MEDIA_STREAM_TYPE_BOTH)
            _remoteUid.value = uid
            _state.value     = RtcState.LIVE
        }

        override fun onUserUnpublishStream(uid: String, type: MediaStreamType, reason: StreamRemoveReason) {
            if (uid == _remoteUid.value) {
                _remoteUid.value = null
                _state.value     = RtcState.CONNECTING
            }
        }

        override fun onLeaveRoom(stats: RTCRoomStats) {
            _state.value = RtcState.IDLE
        }
    }
}
