package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SuaStateRepository {
    const val WAKE_CONVERSATION_WINDOW_MS: Long = 60_000L // 1 full minute (60 seconds)

    private val _status = MutableStateFlow(SuaStatus.IDLE)
    val status: StateFlow<SuaStatus> = _status.asStateFlow()

    private val _operatingMode = MutableStateFlow(MokoOperatingMode.OFFLINE)
    val operatingMode: StateFlow<MokoOperatingMode> = _operatingMode.asStateFlow()

    private val _isBackgroundServiceRunning = MutableStateFlow(false)
    val isBackgroundServiceRunning: StateFlow<Boolean> = _isBackgroundServiceRunning.asStateFlow()

    private val _wakeSessionRemainingSeconds = MutableStateFlow(0)
    val wakeSessionRemainingSeconds: StateFlow<Int> = _wakeSessionRemainingSeconds.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isScreenShareActive = MutableStateFlow(false)
    val isScreenShareActive: StateFlow<Boolean> = _isScreenShareActive.asStateFlow()

    private val _pendingUiNavigationAction = MutableStateFlow<String?>(null)
    val pendingUiNavigationAction: StateFlow<String?> = _pendingUiNavigationAction.asStateFlow()

    private val _liveTranscript = MutableStateFlow("Say 'Hi Moko' or type a message below...")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _lastSuaReply = MutableStateFlow(
        "Moko is ready. Select ONLINE for full AI & voice, SLEEP for offline hardware & calls, or OFFLINE to stop background mic."
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

    private val _pendingComms = MutableStateFlow<PendingCommsConfirmation?>(null)
    val pendingComms: StateFlow<PendingCommsConfirmation?> = _pendingComms.asStateFlow()

    private val _serviceErrorMessage = MutableStateFlow<String?>(null)
    val serviceErrorMessage: StateFlow<String?> = _serviceErrorMessage.asStateFlow()

    @Volatile
    var lastResolvedContactName: String = ""

    @Volatile
    var lastResolvedNumbers: List<String> = emptyList()

    @Volatile
    var lastWakeSessionTimestampMs: Long = 0L

    fun updateServiceError(errorMsg: String?) {
        _serviceErrorMessage.value = errorMsg
    }

    fun setScreenShareActive(active: Boolean) {
        _isScreenShareActive.value = active
    }

    fun triggerUiNavigationAction(actionKey: String?) {
        _pendingUiNavigationAction.value = actionKey
    }

    fun buildCurrentScreenSnapshotSummary(
        prefs: SuaPreferences,
        batteryPct: Int,
        activeWindowSummary: String = ""
    ): String {
        val bot = prefs.getAssistantName()
        val user = prefs.getUserName()
        val dev = SuaPreferences.DEVELOPER_NAME
        val mode = _operatingMode.value.label
        val timerSec = _wakeSessionRemainingSeconds.value
        val lockState = if (_isScreenLockedOverlay.value) "IN-APP OVERLAY ACTIVE" else "UNLOCKED"
        val torchState = if (_isFlashlightOn.value) "ON" else "OFF"
        val bright = _brightnessPercent.value
        val vol = _volumePercent.value
        val pending = _pendingComms.value
        val pendingSummary = if (pending != null) {
            "Pending Confirmation Card: ${pending.actionType.label} for ${pending.contactName} (${pending.phoneNumbers.joinToString()})"
        } else {
            "No pending Call/SMS confirmation card"
        }
        val windowPart = if (activeWindowSummary.isNotBlank()) " | Live Window Nodes: $activeWindowSummary" else ""
        return "SCREEN SHARE VISION // Assistant: $bot (Dev: $dev, User: $user) | Mode: $mode (${timerSec}s active timer) | Screen: $lockState | Battery: $batteryPct% | Torch: $torchState | Brightness: $bright% | Volume: $vol% | $pendingSummary | Center Reply: \"${_lastSuaReply.value.take(100)}\" | User Spoke: \"${_liveTranscript.value.take(80)}\"$windowPart | Clickable Controls: [ONLINE, SLEEP, OFFLINE, SETTINGS, HISTORY, MENU, VOICE, SCREEN SHARE, SEND, UNLOCK]"
    }

    fun buildCurrentScreenSpokenSummaryHi(
        prefs: SuaPreferences,
        batteryPct: Int,
        hasVisionCapture: Boolean = false
    ): String {
        val bot = prefs.getAssistantName()
        val user = prefs.getUserName()
        val mode = _operatingMode.value.label
        val timerSec = _wakeSessionRemainingSeconds.value
        val pending = _pendingComms.value
        val lockPart = if (_isScreenLockedOverlay.value) "इन-ऐप स्क्रीन ओवरले अभी चालू है। " else ""
        val pendingPart = if (pending != null) {
            "स्क्रीन पर अभी ${pending.contactName} के लिए ${pending.actionType.label} का कन्फर्मेशन कार्ड खुला है। "
        } else ""
        val torchPart = if (_isFlashlightOn.value) "फ्लैशलाइट चालू है, " else ""
        val visionNote = if (hasVisionCapture) "मैंने आपकी लाइव स्क्रीन कैप्चर करके एनालाइज कर ली है। " else "ऑफलाइन स्क्रीन स्टेटस के अनुसार: "
        return "जी $user, $visionNote${lockPart}${pendingPart}अभी $bot $mode मोड में है (${timerSec} सेकंड टाइमर), बैटरी $batteryPct प्रतिशत है, ${torchPart}ब्राइटनेस ${_brightnessPercent.value} प्रतिशत और वॉल्यूम ${_volumePercent.value} प्रतिशत है।"
    }

    fun buildCurrentScreenSpokenSummaryEn(prefs: SuaPreferences, batteryPct: Int): String {
        val bot = prefs.getAssistantName()
        val user = prefs.getUserName()
        val mode = _operatingMode.value.label
        val timerSec = _wakeSessionRemainingSeconds.value
        val pending = _pendingComms.value
        val lockPart = if (_isScreenLockedOverlay.value) "The in-app lock overlay is active. " else ""
        val pendingPart = if (pending != null) {
            "A confirmation card for ${pending.contactName} (${pending.actionType.label}) is visible on screen. "
        } else ""
        return "Yes $user, I have analyzed your screen state. ${lockPart}${pendingPart}$bot is in $mode mode with ${timerSec} seconds on the timer, battery is at $batteryPct percent, brightness is ${_brightnessPercent.value} percent, and volume is ${_volumePercent.value} percent."
    }

    fun setOperatingMode(mode: MokoOperatingMode) {
        _operatingMode.value = mode
        _isBackgroundServiceRunning.value = (mode != MokoOperatingMode.OFFLINE)
        if (mode != MokoOperatingMode.OFFLINE) {
            _serviceErrorMessage.value = null
        } else {
            lastWakeSessionTimestampMs = 0L
            _wakeSessionRemainingSeconds.value = 0
            if (_status.value == SuaStatus.LISTENING) {
                _status.value = SuaStatus.IDLE
            }
        }
    }

    fun setPendingComms(pending: PendingCommsConfirmation?) {
        _pendingComms.value = pending
        if (pending != null && pending.phoneNumbers.isNotEmpty()) {
            lastResolvedContactName = pending.contactName
            lastResolvedNumbers = pending.phoneNumbers
        }
    }

    fun recordResolvedContact(name: String, numbers: List<String>) {
        if (numbers.isNotEmpty()) {
            lastResolvedContactName = name
            lastResolvedNumbers = numbers
        }
    }

    fun markWakeWordDetected() {
        lastWakeSessionTimestampMs = System.currentTimeMillis()
        _wakeSessionRemainingSeconds.value = 60
    }

    fun clearWakeConversationWindow() {
        lastWakeSessionTimestampMs = 0L
        _wakeSessionRemainingSeconds.value = 0
    }

    fun refreshWakeCountdownSeconds(): Int {
        if (_operatingMode.value == MokoOperatingMode.OFFLINE) {
            _wakeSessionRemainingSeconds.value = 0
            return 0
        }
        if (_pendingComms.value != null) {
            lastWakeSessionTimestampMs = System.currentTimeMillis()
            _wakeSessionRemainingSeconds.value = 60
            return 60
        }
        val ts = lastWakeSessionTimestampMs
        if (ts <= 0L) {
            _wakeSessionRemainingSeconds.value = 0
            return 0
        }
        val elapsed = System.currentTimeMillis() - ts
        if (elapsed >= WAKE_CONVERSATION_WINDOW_MS) {
            lastWakeSessionTimestampMs = 0L
            _wakeSessionRemainingSeconds.value = 0
            return 0
        }
        val rem = ((WAKE_CONVERSATION_WINDOW_MS - elapsed + 999L) / 1000L).toInt().coerceIn(0, 60)
        _wakeSessionRemainingSeconds.value = rem
        return rem
    }

    fun isWithinWakeConversationWindow(windowMs: Long = WAKE_CONVERSATION_WINDOW_MS): Boolean {
        if (_pendingComms.value != null) return true
        val ts = lastWakeSessionTimestampMs
        if (ts <= 0L) return false
        val elapsed = System.currentTimeMillis() - ts
        val active = elapsed in 0..windowMs
        if (!active) {
            _wakeSessionRemainingSeconds.value = 0
        }
        return active
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun updateStatus(newStatus: SuaStatus) {
        _status.value = newStatus
    }

    fun setBackgroundServiceRunning(running: Boolean) {
        _isBackgroundServiceRunning.value = running
        if (!running) {
            _operatingMode.value = MokoOperatingMode.OFFLINE
            lastWakeSessionTimestampMs = 0L
            _wakeSessionRemainingSeconds.value = 0
            if (_status.value == SuaStatus.LISTENING) {
                _status.value = SuaStatus.IDLE
            }
        } else if (_operatingMode.value == MokoOperatingMode.OFFLINE) {
            _operatingMode.value = MokoOperatingMode.ONLINE
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
            _commandLogs.value = logs.take(SuaPreferences.MAX_COMMAND_LOGS)
        }
    }

    fun setInitialMemories(memories: List<MokoMemoryItem>) {
        if (_memoryItems.value.isEmpty() && memories.isNotEmpty()) {
            _memoryItems.value = memories.take(SuaPreferences.MAX_MEMORY_ITEMS)
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
            userCommand = userCommand.take(SuaPreferences.MAX_LOG_TEXT_LENGTH),
            suaResponse = suaResponse.take(SuaPreferences.MAX_LOG_TEXT_LENGTH),
            actionType = actionType,
            actionSuccess = actionSuccess
        )
        val updated = (listOf(entry) + _commandLogs.value).take(SuaPreferences.MAX_COMMAND_LOGS)
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
        val clean = factText.trim().take(SuaPreferences.MAX_LOG_TEXT_LENGTH)
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
        val updated = (listOf(newItem) + _memoryItems.value).take(SuaPreferences.MAX_MEMORY_ITEMS)
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
