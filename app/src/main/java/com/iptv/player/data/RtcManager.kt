package com.iptv.player.data

import android.content.Context
import android.util.Log
import android.view.TextureView
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

enum class RtcState { IDLE, CONNECTING, LIVE, ERROR }

class RtcManager(private val context: Context) {

    private var rtcVideo:  RTCVideo? = null
    private var rtcRoom:   RTCRoom?  = null
    private var currentRoomId: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state     = MutableStateFlow(RtcState.IDLE)
    val state: StateFlow<RtcState> = _state.asStateFlow()

    private val _remoteUid = MutableStateFlow<String?>(null)
    val remoteUid: StateFlow<String?> = _remoteUid.asStateFlow()

    fun join(roomId: String) {
        Log.d("RtcManager", "join: roomId=$roomId state=${_state.value}")
        if (_state.value != RtcState.IDLE) return
        _state.value = RtcState.CONNECTING
        currentRoomId = roomId
        val userId = "viewer_android_${(1000..9999).random()}"
        val credentials = RTCTokenGenerator.generate(roomId, userId)

        scope.launch {
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
        Log.d("RtcManager", "renderRemote: uid=$uid room=$currentRoomId tv=$textureView hasAvailableSurface=${textureView.isAvailable}")
        val streamKey = RemoteStreamKey(currentRoomId, uid, StreamIndex.STREAM_INDEX_MAIN)
        val result = rtcVideo?.setRemoteVideoCanvas(streamKey, VideoCanvas(textureView, VideoCanvas.RENDER_MODE_FIT))
        Log.d("RtcManager", "setRemoteVideoCanvas result=$result")
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
            Log.d("RtcManager", "onRoomStateChanged: room=$roomId uid=$uid state=$state")
            if (state != 0) _state.value = RtcState.ERROR
        }

        override fun onUserPublishStream(uid: String, type: MediaStreamType) {
            Log.d("RtcManager", "onUserPublishStream: uid=$uid type=$type")
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
