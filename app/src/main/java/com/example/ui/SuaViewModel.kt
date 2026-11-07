package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SuaBrain
import com.example.control.SuaDeviceController
import com.example.data.AiProviderType
import com.example.data.CommandLogEntry
import com.example.data.CustomVoiceCommand
import com.example.data.MokoMemoryItem
import com.example.data.PendingCommsConfirmation
import com.example.data.PermissionStatusItem
import com.example.data.QuickContact
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.data.SuaStatus
import com.example.data.VoicePreset
import com.example.service.SuaVoiceService
import com.example.service.SuaVoiceSpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SuaViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    val prefs = SuaPreferences(appContext)
    val controller = SuaDeviceController(appContext)
    private val brain = SuaBrain(prefs, controller)

    val status: StateFlow<SuaStatus> = SuaStateRepository.status
    val isBackgroundServiceRunning: StateFlow<Boolean> = SuaStateRepository.isBackgroundServiceRunning
    val liveTranscript: StateFlow<String> = SuaStateRepository.liveTranscript
    val lastSuaReply: StateFlow<String> = SuaStateRepository.lastSuaReply
    val isFlashlightOn: StateFlow<Boolean> = SuaStateRepository.isFlashlightOn
    val brightnessPercent: StateFlow<Int> = SuaStateRepository.brightnessPercent
    val volumePercent: StateFlow<Int> = SuaStateRepository.volumePercent
    val isMusicPlaying: StateFlow<Boolean> = SuaStateRepository.isMusicPlaying
    val isScreenLockedOverlay: StateFlow<Boolean> = SuaStateRepository.isScreenLockedOverlay
    val commandLogs: StateFlow<List<CommandLogEntry>> = SuaStateRepository.commandLogs
    val memoryItems: StateFlow<List<MokoMemoryItem>> = SuaStateRepository.memoryItems
    val pendingComms: StateFlow<PendingCommsConfirmation?> = SuaStateRepository.pendingComms
    val audioRmsDb: StateFlow<Float> = SuaStateRepository.audioRmsDb

    private val _userName = MutableStateFlow(prefs.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _assistantName = MutableStateFlow(prefs.getAssistantName())
    val assistantName: StateFlow<String> = _assistantName.asStateFlow()

    private val _savedApiKey = MutableStateFlow(prefs.getSavedUserApiKey())
    val savedApiKey: StateFlow<String> = _savedApiKey.asStateFlow()

    private val _hasEffectiveApiKey = MutableStateFlow(prefs.getEffectiveApiKey().isNotEmpty())
    val hasEffectiveApiKey: StateFlow<Boolean> = _hasEffectiveApiKey.asStateFlow()

    private val _aiProvider = MutableStateFlow(prefs.getAiProvider())
    val aiProvider: StateFlow<AiProviderType> = _aiProvider.asStateFlow()

    private val _customApiUrl = MutableStateFlow(prefs.getCustomApiUrl())
    val customApiUrl: StateFlow<String> = _customApiUrl.asStateFlow()

    private val _geminiModel = MutableStateFlow(prefs.getGeminiModel())
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    private val _kalpaApiKey = MutableStateFlow(prefs.getKalpaApiKey())
    val kalpaApiKey: StateFlow<String> = _kalpaApiKey.asStateFlow()

    private val _kalpaVoiceId = MutableStateFlow(prefs.getKalpaVoiceId())
    val kalpaVoiceId: StateFlow<String> = _kalpaVoiceId.asStateFlow()

    private val _continuousBgEnabled = MutableStateFlow(prefs.isContinuousBackgroundEnabled())
    val continuousBgEnabled: StateFlow<Boolean> = _continuousBgEnabled.asStateFlow()

    private val _wakeWordRequired = MutableStateFlow(prefs.isWakeWordRequiredInBackground())
    val wakeWordRequired: StateFlow<Boolean> = _wakeWordRequired.asStateFlow()

    private val _lowBatteryWakeMode = MutableStateFlow(prefs.isLowBatteryWakeMode())
    val lowBatteryWakeMode: StateFlow<Boolean> = _lowBatteryWakeMode.asStateFlow()

    private val _voiceLocale = MutableStateFlow(prefs.getVoiceLocale())
    val voiceLocale: StateFlow<String> = _voiceLocale.asStateFlow()

    private val _voicePreset = MutableStateFlow(prefs.getVoicePreset())
    val voicePreset: StateFlow<VoicePreset> = _voicePreset.asStateFlow()

    private val _voicePitch = MutableStateFlow(prefs.getVoicePitch())
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()

    private val _voiceSpeed = MutableStateFlow(prefs.getVoiceSpeed())
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    private val _customCommands = MutableStateFlow(prefs.getCustomCommands())
    val customCommands: StateFlow<List<CustomVoiceCommand>> = _customCommands.asStateFlow()

    private val _permissionStatuses = MutableStateFlow(controller.getPermissionStatusList())
    val permissionStatuses: StateFlow<List<PermissionStatusItem>> = _permissionStatuses.asStateFlow()

    private val _quickContacts = MutableStateFlow(prefs.getQuickContacts())
    val quickContacts: StateFlow<List<QuickContact>> = _quickContacts.asStateFlow()

    private val _settingsBannerMessage = MutableStateFlow<String?>(null)
    val settingsBannerMessage: StateFlow<String?> = _settingsBannerMessage.asStateFlow()

    private val _batteryPercent = MutableStateFlow(controller.getBatteryPercentage())
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    init {
        SuaVoiceSpeaker.init(appContext)
        SuaStateRepository.setInitialLogs(prefs.loadCommandLogs())
        SuaStateRepository.setInitialMemories(prefs.loadMemoryItems())
        refreshPermissionsAndHardware()
    }

    fun refreshPermissionsAndHardware() {
        controller.syncHardwareTelemetry()
        _batteryPercent.value = controller.getBatteryPercentage()
        _permissionStatuses.value = controller.getPermissionStatusList()
        _userName.value = prefs.getUserName()
        _assistantName.value = prefs.getAssistantName()
        _savedApiKey.value = prefs.getSavedUserApiKey()
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        _aiProvider.value = prefs.getAiProvider()
        _customApiUrl.value = prefs.getCustomApiUrl()
        _geminiModel.value = prefs.getGeminiModel()
        _kalpaApiKey.value = prefs.getKalpaApiKey()
        _kalpaVoiceId.value = prefs.getKalpaVoiceId()
        _wakeWordRequired.value = prefs.isWakeWordRequiredInBackground()
        _lowBatteryWakeMode.value = prefs.isLowBatteryWakeMode()
        _voicePreset.value = prefs.getVoicePreset()
        _voicePitch.value = prefs.getVoicePitch()
        _voiceSpeed.value = prefs.getVoiceSpeed()
        _customCommands.value = prefs.getCustomCommands()
    }

    fun toggleBackgroundVoiceService() {
        if (isBackgroundServiceRunning.value) {
            activateSleepMode()
        } else {
            activateOnlineMode()
        }
    }

    fun activateOnlineMode() {
        prefs.setContinuousBackgroundEnabled(true)
        _continuousBgEnabled.value = true
        SuaVoiceService.startService(appContext)
    }

    fun activateSleepMode() {
        val botName = prefs.getAssistantName()
        val uName = prefs.getUserName()
        SuaVoiceService.stopService(appContext)
        SuaStateRepository.updateLiveTranscript("SLEEP MODE // Background listening is OFF. Tap ONLINE to wake $botName.")
        SuaStateRepository.updateLastReply("$botName IN SLEEP MODE // Background microphone is OFF. Tap ONLINE or Sleep button to go back Online.")
        SuaVoiceSpeaker.speak("जी $uName, $botName अब स्लीप मोड में है। बैकग्राउंड में माइक बंद रहेगा।")
    }

    fun onMicPermissionDenied() {
        val botName = prefs.getAssistantName()
        val uName = prefs.getUserName()
        SuaStateRepository.setBackgroundServiceRunning(false)
        SuaStateRepository.updateStatus(SuaStatus.ERROR)
        SuaStateRepository.updateLiveTranscript("MIC PERMISSION DENIED // Cannot start Online Mode")
        SuaStateRepository.updateLastReply("PERMISSION DENIED // Microphone access was denied. Open Settings -> GRANT ALL SYSTEM PERMISSIONS to enable $botName voice.")
        SuaVoiceSpeaker.speak("माफ़ कीजिए $uName, आपने माइक परमिशन मना कर दी है। इसके बिना मैं आपकी आवाज़ नहीं सुन सकती।")
    }

    fun executeCommandText(commandText: String) {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) return

        if (isBackgroundServiceRunning.value) {
            SuaVoiceService.sendCommandToService(appContext, trimmed)
        } else {
            val hudCommand = brain.sanitizeForHud(trimmed)
            SuaStateRepository.updateLiveTranscript(hudCommand)
            SuaStateRepository.updateStatus(SuaStatus.PROCESSING)
            viewModelScope.launch {
                val parsed = brain.interpretCommand(trimmed)
                val result = controller.executeCommand(parsed)
                _userName.value = prefs.getUserName()
                _assistantName.value = prefs.getAssistantName()
                SuaStateRepository.updateLastReply(result.displayReply)
                SuaStateRepository.addCommandLog(
                    userCommand = hudCommand,
                    suaResponse = result.displayReply,
                    actionType = parsed.action,
                    actionSuccess = result.success,
                    prefs = prefs
                )
                SuaStateRepository.updateStatus(SuaStatus.IDLE)
                SuaVoiceSpeaker.speak(result.finalSpokenReply)
            }
        }
    }

    fun saveIdentityProfile(newUserName: String, newAssistantName: String) {
        prefs.setUserName(newUserName)
        prefs.setAssistantName(newAssistantName)
        _userName.value = prefs.getUserName()
        _assistantName.value = prefs.getAssistantName()
        _settingsBannerMessage.value = "IDENTITY SAVED // User: ${_userName.value} | Assistant Wake-Word: 'Ok ${_assistantName.value}' | Developer: ${SuaPreferences.DEVELOPER_NAME}"
        SuaVoiceSpeaker.speak("जी ${_userName.value}, आपका नाम और मेरा नाम ${_assistantName.value} सेव हो गया है! अब आप ओके ${_assistantName.value} बोलकर मुझे बुला सकते हैं।")
    }

    fun saveKalpaVoiceConfig(apiKey: String, voiceId: String) {
        prefs.saveKalpaApiKey(apiKey)
        prefs.setKalpaVoiceId(voiceId)
        _kalpaApiKey.value = prefs.getKalpaApiKey()
        _kalpaVoiceId.value = prefs.getKalpaVoiceId()
        val voiceLabel = SuaPreferences.KALPA_VOICE_OPTIONS.firstOrNull { it.id == _kalpaVoiceId.value }?.name ?: "Kalpa Neural Voice"
        _settingsBannerMessage.value = "KALPA VOICE SAVED // Active Voice: $voiceLabel (kalpalabs.ai)"
        SuaVoiceSpeaker.speak("नमस्ते ${_userName.value}! कल्पा लैब्स की नई मधुर आवाज़ अब एक्टिव हो गई है।")
    }

    fun selectKalpaVoice(voiceId: String) {
        prefs.setKalpaVoiceId(voiceId)
        _kalpaVoiceId.value = prefs.getKalpaVoiceId()
        val voiceLabel = SuaPreferences.KALPA_VOICE_OPTIONS.firstOrNull { it.id == _kalpaVoiceId.value }?.name ?: "Kalpa Neural Voice"
        _settingsBannerMessage.value = "KALPA VOICE UPDATED // Selected: $voiceLabel"
        SuaVoiceSpeaker.speak("नमस्ते ${_userName.value}! अब मैं कल्पा लैब्स की इसी प्यारी आवाज़ में आपसे बात करूँगी।")
    }

    fun deleteCommandLogItem(logId: Long) {
        SuaStateRepository.deleteCommandLog(logId, prefs)
    }

    fun pinChatToMemory(entry: CommandLogEntry) {
        SuaStateRepository.pinCommandLogToMemory(entry, prefs)
        SuaVoiceSpeaker.speak("जी ${_userName.value}, मैंने यह बातचीत अपनी मेमोरी में सेव कर ली है।")
    }

    fun addManualMemoryFact(factText: String) {
        val cleaned = factText.trim()
        if (cleaned.isEmpty()) return
        SuaStateRepository.addMemoryFact(cleaned, isAuto = false, prefs = prefs)
        _settingsBannerMessage.value = "MEMORY SAVED // Added \"$cleaned\" to Memory Vault."
        SuaVoiceSpeaker.speak("जी ${_userName.value}, यह बात मेरी मेमोरी में सेव हो गई है।")
    }

    fun deleteMemoryFact(memoryId: Long) {
        SuaStateRepository.deleteMemoryItem(memoryId, prefs)
    }

    fun clearAllMemories() {
        SuaStateRepository.clearMemories(prefs)
    }

    fun testVoiceGreeting() {
        val bot = _assistantName.value
        val uName = _userName.value
        SuaStateRepository.updateLastReply("$bot KALPA NEURAL VOICE // Developed by ${SuaPreferences.DEVELOPER_NAME} for $uName.")
        SuaVoiceSpeaker.speak("नमस्ते मेरे प्यारे $uName! मेरा नाम $bot है और मुझे मिस्टर शशांक ने डेवलप किया है। जब भी आप हिंदी या इंग्लिश में बोलेंगे, मैं कल्पा लैब्स की असली आवाज़ में तुरंत आपका काम करूँगी।")
    }

    fun setAiProvider(provider: AiProviderType) {
        prefs.setAiProvider(provider)
        _aiProvider.value = provider
    }

    fun saveApiConfiguration(inputKey: String, inputUrl: String) {
        val cleanedKey = inputKey.trim()
        val cleanedUrl = inputUrl.trim()
        prefs.setCustomApiUrl(cleanedUrl)
        _customApiUrl.value = cleanedUrl

        if (cleanedKey.isEmpty() && cleanedUrl.isEmpty()) {
            _settingsBannerMessage.value = "ERROR // Please enter a valid API Key or Custom API Endpoint Link."
            SuaVoiceSpeaker.speak("कृपया सही एपीआई की या वेबसाइट लिंक दर्ज करें।")
            return
        }
        if (cleanedKey.isNotEmpty()) {
            prefs.saveUserApiKey(cleanedKey)
            _savedApiKey.value = cleanedKey
        }
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        val activeProv = prefs.resolveActiveAiProvider().displayName
        val linkNote = if (cleanedUrl.isNotEmpty()) "Custom URL: $cleanedUrl" else "Auto Official Endpoint"
        _settingsBannerMessage.value = "API LINKED // Provider: $activeProv ($linkNote)"
        SuaVoiceSpeaker.speak("जी ${_userName.value}, आपकी एपीआई की और सेटिंग सुरक्षित रूप से सेव हो गई है।")
    }

    fun saveApiKey(inputKey: String) {
        saveApiConfiguration(inputKey, _customApiUrl.value)
    }

    fun deleteApiKey() {
        prefs.deleteUserApiKey()
        prefs.setCustomApiUrl("")
        _savedApiKey.value = ""
        _customApiUrl.value = ""
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        _settingsBannerMessage.value = "DELETED // Saved API Key & Custom Endpoint cleared."
        SuaVoiceSpeaker.speak("जी ${_userName.value}, एपीआई की हटा दी गई है।")
    }

    fun setGeminiModel(modelId: String) {
        prefs.setGeminiModel(modelId)
        _geminiModel.value = prefs.getGeminiModel()
        _settingsBannerMessage.value = "MODEL UPDATED // Active model set to ${_geminiModel.value}"
    }

    fun clearSettingsBanner() {
        _settingsBannerMessage.value = null
    }

    fun setContinuousBackground(enabled: Boolean) {
        prefs.setContinuousBackgroundEnabled(enabled)
        _continuousBgEnabled.value = enabled
        val msg = if (enabled) "जी ${_userName.value}, बैकग्राउंड वॉइस मोड चालू कर दिया गया है।" else "बैकग्राउंड वॉइस मोड बंद कर दिया गया है।"
        SuaVoiceSpeaker.speak(msg)
    }

    fun setWakeWordRequired(required: Boolean) {
        prefs.setWakeWordRequiredInBackground(required)
        _wakeWordRequired.value = required
        val bot = _assistantName.value
        val msg = if (required) {
            "जी ${_userName.value}, अब बैकग्राउंड में कमांड देने से पहले ओके $bot या हाय $bot बोलना होगा।"
        } else {
            "जी ${_userName.value}, वेक-वर्ड बंद कर दिया गया है।"
        }
        SuaVoiceSpeaker.speak(msg)
    }

    fun setLowBatteryWakeMode(enabled: Boolean) {
        prefs.setLowBatteryWakeMode(enabled)
        _lowBatteryWakeMode.value = enabled
        val msg = if (enabled) {
            "जी ${_userName.value}, ओके गूगल की तरह लो-बैटरी बैकग्राउंड वेक मोड चालू हो गया है।"
        } else {
            "लो-बैटरी मोड बंद कर दिया गया है।"
        }
        SuaVoiceSpeaker.speak(msg)
    }

    fun setVoiceLanguage(localeTag: String) {
        prefs.setVoiceLocale(localeTag)
        _voiceLocale.value = localeTag
        SuaVoiceSpeaker.applyLocale(localeTag)
        _settingsBannerMessage.value = "VOICE ENGINE // Output voice language updated to $localeTag"
        SuaVoiceSpeaker.speak("जी ${_userName.value}, मेरी आवाज़ की भाषा अपडेट हो गई है।")
    }

    fun selectVoicePreset(preset: VoicePreset) {
        prefs.setVoicePreset(preset)
        _voicePreset.value = preset
        _voicePitch.value = prefs.getVoicePitch()
        _voiceSpeed.value = prefs.getVoiceSpeed()
        _settingsBannerMessage.value = "VOICE STYLE UPDATED // ${preset.label} (Pitch ${_voicePitch.value}, Speed ${_voiceSpeed.value})"
        SuaVoiceSpeaker.speak("नमस्ते मेरे प्यारे ${_userName.value}! अब मैं आपसे इसी प्यारी आवाज़ में बात करूँगी।")
    }

    fun updateVoicePitch(pitch: Float) {
        prefs.setVoicePitch(pitch)
        _voicePitch.value = prefs.getVoicePitch()
    }

    fun updateVoiceSpeed(speed: Float) {
        prefs.setVoiceSpeed(speed)
        _voiceSpeed.value = prefs.getVoiceSpeed()
    }

    fun addCustomVoiceCommand(trigger: String, reply: String) {
        val cleanTrigger = trigger.trim()
        val cleanReply = reply.trim()
        if (cleanTrigger.isEmpty() || cleanReply.isEmpty()) return
        val updated = _customCommands.value.toMutableList()
        updated.add(
            0,
            CustomVoiceCommand(
                id = System.currentTimeMillis(),
                triggerPhrase = cleanTrigger,
                spokenResponse = cleanReply
            )
        )
        prefs.saveCustomCommands(updated)
        _customCommands.value = updated
        _settingsBannerMessage.value = "CUSTOM COMMAND ADDED // Say \"$cleanTrigger\" -> \"$cleanReply\""
        SuaVoiceSpeaker.speak("जी ${_userName.value}, आपका नया वॉइस कमांड सेव हो गया है।")
    }

    fun removeCustomVoiceCommand(id: Long) {
        val updated = _customCommands.value.filterNot { it.id == id }
        prefs.saveCustomCommands(updated)
        _customCommands.value = updated
    }

    fun addOrUpdateQuickContact(name: String, aliasTag: String, phone: String) {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.isEmpty() || cleanPhone.isEmpty()) return
        val current = _quickContacts.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val newContact = QuickContact(
            name = cleanName,
            aliasTag = aliasTag.trim().ifEmpty { "SPEED DIAL // ${cleanName.uppercase()}" },
            phoneNumber = cleanPhone
        )
        if (existingIdx >= 0) {
            current[existingIdx] = newContact
        } else {
            current.add(0, newContact)
        }
        prefs.saveQuickContacts(current)
        _quickContacts.value = current
        _settingsBannerMessage.value = "CONTACT SAVED // ${newContact.name} ($cleanPhone) added to Speed Dial."
        SuaVoiceSpeaker.speak("जी ${_userName.value}, ${newContact.name} का नंबर सेव कर लिया गया है।")
    }

    fun removeQuickContact(name: String) {
        val updated = _quickContacts.value.filterNot { it.name.equals(name, ignoreCase = true) }
        prefs.saveQuickContacts(updated)
        _quickContacts.value = updated
    }

    fun toggleFlashlightDirect() {
        val next = !isFlashlightOn.value
        val res = controller.setFlashlight(next)
        SuaStateRepository.updateLastReply(res.displayReply)
        SuaVoiceSpeaker.speak(res.finalSpokenReply)
    }

    fun adjustBrightnessDirect(newPercent: Int, speakFeedback: Boolean = false) {
        val res = controller.adjustBrightness(delta = 0, explicit = newPercent)
        SuaStateRepository.updateLastReply(res.displayReply)
        if (speakFeedback) {
            SuaVoiceSpeaker.speak(res.finalSpokenReply)
        }
    }

    fun adjustVolumeDirect(newPercent: Int, speakFeedback: Boolean = false) {
        val res = controller.adjustVolume(deltaPercent = 0, explicitPercent = newPercent, mute = newPercent == 0)
        SuaStateRepository.updateLastReply(res.displayReply)
        if (speakFeedback) {
            SuaVoiceSpeaker.speak(res.finalSpokenReply)
        }
    }

    fun lockScreenDirect() {
        val res = controller.lockPhoneScreen()
        SuaStateRepository.updateLastReply(res.displayReply)
        SuaVoiceSpeaker.speak(res.finalSpokenReply)
    }

    fun unlockScreenOverlay() {
        SuaStateRepository.setScreenLockedOverlay(false)
        SuaStateRepository.updateLastReply("SECURITY // Screen unlocked.")
        SuaVoiceSpeaker.speak("जी ${_userName.value}, स्क्रीन अनलॉक कर दी गई है।")
    }

    fun clearCommandHistory() {
        SuaStateRepository.clearLogs(prefs)
    }
}
