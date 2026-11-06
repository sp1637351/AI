package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SuaStateRepository {
    private val _status = MutableStateFlow(SuaStatus.IDLE)
    val status: StateFlow<SuaStatus> = _status.asStateFlow()

    private val _isBackgroundServiceRunning = MutableStateFlow(false)
    val isBackgroundServiceRunning: StateFlow<Boolean> = _isBackgroundServiceRunning.asStateFlow()

    private val _liveTranscript = MutableStateFlow("AWAITING VOICE COMMAND...")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _lastSuaReply = MutableStateFlow(
        "SUA ONLINE // All tactical systems nominal. Tap the Arc Reactor core to engage background voice mode."
    )
    val lastSuaReply: StateFlow<String> = _lastSuaReply.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _brightnessPercent = MutableStateFlow(75)
    val brightnessPercent: StateFlow<Int> = _brightnessPercent.asStateFlow()

    private val _volumePercent = MutableStateFlow(70)
    val volumePercent: StateFlow<Int> = _volumePercent.asStateFlow()

    private val _isMusicPlaying = MutableStateFlow(false)
    val isMusicPlaying: StateFlow<Boolean> = _isMusicPlaying.asStateFlow()

    private val _isScreenLockedOverlay = MutableStateFlow(false)
    val isScreenLockedOverlay: StateFlow<Boolean> = _isScreenLockedOverlay.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    val commandLogs: StateFlow<List<CommandLogEntry>> = _commandLogs.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    fun updateStatus(newStatus: SuaStatus) {
        _status.value = newStatus
    }

    fun setBackgroundServiceRunning(running: Boolean) {
        _isBackgroundServiceRunning.value = running
        if (!running && _status.value == SuaStatus.LISTENING) {
            _status.value = SuaStatus.IDLE
        }
    }

    fun updateLiveTranscript(text: String) {
        _liveTranscript.value = text
    }

    fun updateLastReply(reply: String) {
        _lastSuaReply.value = reply
    }

    fun updateFlashlight(isOn: Boolean) {
        _isFlashlightOn.value = isOn
    }

    fun updateBrightness(percent: Int) {
        _brightnessPercent.value = percent.coerceIn(5, 100)
    }

    fun updateVolume(percent: Int) {
        _volumePercent.value = percent.coerceIn(0, 100)
    }

    fun updateMusicPlaying(playing: Boolean) {
        _isMusicPlaying.value = playing
    }

    fun setScreenLockedOverlay(locked: Boolean) {
        _isScreenLockedOverlay.value = locked
    }

    fun updateAudioRms(rms: Float) {
        _audioRmsDb.value = rms.coerceIn(0f, 12f)
    }

    fun setInitialLogs(logs: List<CommandLogEntry>) {
        if (_commandLogs.value.isEmpty() && logs.isNotEmpty()) {
            _commandLogs.value = logs
        }
    }

    fun addCommandLog(
        userCommand: String,
        suaResponse: String,
        actionType: DeviceActionType,
        actionSuccess: Boolean = true,
        prefs: SuaPreferences? = null
    ) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val entry = CommandLogEntry(
            id = System.currentTimeMillis(),
            timestamp = timeFmt.format(Date()),
            userCommand = userCommand,
            suaResponse = suaResponse,
            actionType = actionType,
            actionSuccess = actionSuccess
        )
        val updated = listOf(entry) + _commandLogs.value.take(39)
        _commandLogs.value = updated
        prefs?.saveCommandLogs(updated)
    }

    fun clearLogs(prefs: SuaPreferences? = null) {
        _commandLogs.value = emptyList()
        prefs?.saveCommandLogs(emptyList())
    }
}
