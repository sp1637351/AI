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

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _liveTranscript = MutableStateFlow("Say 'Hi Moko' or type a message below...")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _lastSuaReply = MutableStateFlow(
        "Moko is ready. Switch to ONLINE mode for background & lock-screen listening, or tap SLEEP to pause background mic."
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

    private val _memoryItems = MutableStateFlow<List<MokoMemoryItem>>(emptyList())
    val memoryItems: StateFlow<List<MokoMemoryItem>> = _memoryItems.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    @Volatile
    var lastWakeSessionTimestampMs: Long = 0L

    fun markWakeWordDetected() {
        lastWakeSessionTimestampMs = System.currentTimeMillis()
    }

    fun isWithinWakeConversationWindow(windowMs: Long = 22_000L): Boolean {
        val elapsed = System.currentTimeMillis() - lastWakeSessionTimestampMs
        return lastWakeSessionTimestampMs > 0L && elapsed in 0..windowMs
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

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

    fun setInitialMemories(memories: List<MokoMemoryItem>) {
        if (_memoryItems.value.isEmpty() && memories.isNotEmpty()) {
            _memoryItems.value = memories
        }
    }

    fun addCommandLog(
        userCommand: String,
        suaResponse: String,
        actionType: DeviceActionType,
        actionSuccess: Boolean = true,
        prefs: SuaPreferences? = null
    ) {
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
        val entry = CommandLogEntry(
            id = System.currentTimeMillis(),
            timestamp = timeFmt.format(Date()),
            userCommand = userCommand,
            suaResponse = suaResponse,
            actionType = actionType,
            actionSuccess = actionSuccess
        )
        val updated = listOf(entry) + _commandLogs.value.take(59)
        _commandLogs.value = updated
        prefs?.saveCommandLogs(updated)
    }

    fun deleteCommandLog(logId: Long, prefs: SuaPreferences? = null) {
        val updated = _commandLogs.value.filterNot { it.id == logId }
        _commandLogs.value = updated
        prefs?.saveCommandLogs(updated)
    }

    fun pinCommandLogToMemory(entry: CommandLogEntry, prefs: SuaPreferences? = null) {
        val updatedLogs = _commandLogs.value.map {
            if (it.id == entry.id) it.copy(isPinnedToMemory = true) else it
        }
        _commandLogs.value = updatedLogs
        prefs?.saveCommandLogs(updatedLogs)

        val factSummary = "User said: \"${entry.userCommand}\" -> Moko: \"${entry.suaResponse}\""
        addMemoryFact(factSummary, isAuto = false, prefs = prefs)
    }

    fun clearLogs(prefs: SuaPreferences? = null) {
        _commandLogs.value = emptyList()
        prefs?.saveCommandLogs(emptyList())
    }

    fun addMemoryFact(
        factText: String,
        isAuto: Boolean = true,
        prefs: SuaPreferences? = null
    ) {
        val clean = factText.trim()
        if (clean.isEmpty()) return
        // Avoid exact duplicate memories
        if (_memoryItems.value.any { it.factText.equals(clean, ignoreCase = true) }) return

        val timeFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
        val newItem = MokoMemoryItem(
            id = System.currentTimeMillis(),
            factText = clean,
            timestamp = timeFmt.format(Date()),
            isAutoExtracted = isAuto
        )
        val updated = listOf(newItem) + _memoryItems.value.take(59)
        _memoryItems.value = updated
        prefs?.saveMemoryItems(updated)
    }

    fun deleteMemoryItem(memoryId: Long, prefs: SuaPreferences? = null) {
        val updated = _memoryItems.value.filterNot { it.id == memoryId }
        _memoryItems.value = updated
        prefs?.saveMemoryItems(updated)
    }

    fun clearMemories(prefs: SuaPreferences? = null) {
        _memoryItems.value = emptyList()
        prefs?.saveMemoryItems(emptyList())
    }
}
