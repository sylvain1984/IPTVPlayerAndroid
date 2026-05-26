package com.iptv.player.data

import android.content.Context
import android.view.TextureView
import com.ss.bytertc.engine.RTCRoom
import com.ss.bytertc.engine.RTCRoomConfig
import com.ss.bytertc.engine.RTCVideo
import com.ss.bytertc.engine.UserInfo
import com.ss.bytertc.engine.VideoCanvas
import com.ss.bytertc.engine.data.StreamIndex
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler
import com.ss.bytertc.engine.type.ChannelProfile
import com.ss.bytertc.engine.type.MediaStreamType
import com.ss.bytertc.engine.type.StreamRemoveReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val APP_ID        = "6a13b1373d860b0617f988aa"
private const val APP_KEY       = "221fb57fe116497b9201c3c635f1b23c"
private const val VIEWER_UID    = "viewer_001"

enum class RtcState { IDLE, CONNECTING, LIVE, ERROR }

class RtcManager(private val context: Context) {

    private var rtcVideo: RTCVideo? = null
    private var rtcRoom:  RTCRoom?  = null

    private val _state     = MutableStateFlow(RtcState.IDLE)
    val state: StateFlow<RtcState> = _state.asStateFlow()

    private val _remoteUid = MutableStateFlow<String?>(null)
    val remoteUid: StateFlow<String?> = _remoteUid.asStateFlow()

    fun join(roomId: String) {
        if (_state.value != RtcState.IDLE) return
        _state.value = RtcState.CONNECTING

        if (rtcVideo == null) {
            rtcVideo = RTCVideo.createRTCVideo(context, APP_ID, videoHandler, null, null)
        }

        val token = RTCTokenGenerator.generateViewerToken(APP_ID, APP_KEY, roomId, VIEWER_UID)

        rtcRoom = rtcVideo!!.createRTCRoom(roomId).also {
            it.setRTCRoomEventHandler(roomHandler)
        }

        val config = RTCRoomConfig(
            ChannelProfile.CHANNEL_PROFILE_COMMUNICATION,
            /* isAutoPublish      = */ false,
            /* isAutoSubscribeAudio = */ true,
            /* isAutoSubscribeVideo = */ true
        )
        rtcRoom!!.joinRoom(token, UserInfo(VIEWER_UID, ""), config)
    }

    fun renderRemote(uid: String, textureView: TextureView) {
        rtcVideo?.setRemoteVideoCanvas(
            uid, StreamIndex.STREAM_INDEX_MAIN,
            VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, "")
        )
    }

    fun leave() {
        rtcRoom?.leaveRoom()
        rtcRoom?.destroy()
        rtcRoom       = null
        _state.value  = RtcState.IDLE
        _remoteUid.value = null
    }

    fun release() {
        leave()
        RTCVideo.destroyRTCVideo()
        rtcVideo = null
    }

    // ── handlers ──────────────────────────────────────────────────────────────

    private val videoHandler = object : IRTCVideoEventHandler() {
        override fun onError(err: Int) { _state.value = RtcState.ERROR }
    }

    private val roomHandler = object : IRTCRoomEventHandler() {
        override fun onRoomStateChanged(roomId: String, uid: String, state: Int, extraInfo: String) {
            _state.value = if (state == 0) RtcState.CONNECTING else RtcState.ERROR
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

        override fun onLeaveRoom(stats: com.ss.bytertc.engine.RTCRoomStats) {
            _state.value = RtcState.IDLE
        }
    }
}
