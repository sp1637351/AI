package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SuaBrain
import com.example.control.SuaDeviceController
import com.example.data.CommandLogEntry
import com.example.data.MokoMemoryItem
import com.example.data.PermissionStatusItem
import com.example.data.QuickContact
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.data.SuaStatus
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
    private val brain = SuaBrain(prefs)

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
    val audioRmsDb: StateFlow<Float> = SuaStateRepository.audioRmsDb

    private val _savedApiKey = MutableStateFlow(prefs.getSavedUserApiKey())
    val savedApiKey: StateFlow<String> = _savedApiKey.asStateFlow()

    private val _hasEffectiveApiKey = MutableStateFlow(prefs.getEffectiveApiKey().isNotEmpty())
    val hasEffectiveApiKey: StateFlow<Boolean> = _hasEffectiveApiKey.asStateFlow()

    private val _geminiModel = MutableStateFlow(prefs.getGeminiModel())
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    private val _continuousBgEnabled = MutableStateFlow(prefs.isContinuousBackgroundEnabled())
    val continuousBgEnabled: StateFlow<Boolean> = _continuousBgEnabled.asStateFlow()

    private val _wakeWordRequired = MutableStateFlow(prefs.isWakeWordRequiredInBackground())
    val wakeWordRequired: StateFlow<Boolean> = _wakeWordRequired.asStateFlow()

    private val _voiceLocale = MutableStateFlow(prefs.getVoiceLocale())
    val voiceLocale: StateFlow<String> = _voiceLocale.asStateFlow()

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
        _savedApiKey.value = prefs.getSavedUserApiKey()
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        _geminiModel.value = prefs.getGeminiModel()
        _wakeWordRequired.value = prefs.isWakeWordRequiredInBackground()
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
        SuaVoiceService.stopService(appContext)
        SuaStateRepository.updateLiveTranscript("SLEEP MODE // Background listening is OFF. Tap ONLINE to wake Moko.")
        SuaStateRepository.updateLastReply("MOKO IN SLEEP MODE // Background microphone is completely off. Tap ONLINE or Sleep button to go back Online.")
        SuaVoiceSpeaker.speak("जी बॉस, मोको अब स्लीप मोड में है। बैकग्राउंड में अब माइक बंद रहेगा।")
    }

    fun onMicPermissionDenied() {
        SuaStateRepository.setBackgroundServiceRunning(false)
        SuaStateRepository.updateStatus(SuaStatus.ERROR)
        SuaStateRepository.updateLiveTranscript("MIC PERMISSION DENIED // Cannot start Online Mode")
        SuaStateRepository.updateLastReply("PERMISSION DENIED // Microphone access was denied. Open Side Menu -> Settings -> ALLOW ALL PERMISSIONS to enable Moko voice.")
        SuaVoiceSpeaker.speak("माफ़ कीजिए बॉस, आपने माइक परमिशन मना कर दी है। इसके बिना मोको आपकी आवाज़ नहीं सुन सकती।")
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

    fun deleteCommandLogItem(logId: Long) {
        SuaStateRepository.deleteCommandLog(logId, prefs)
    }

    fun pinChatToMemory(entry: CommandLogEntry) {
        SuaStateRepository.pinCommandLogToMemory(entry, prefs)
        SuaVoiceSpeaker.speak("जी बॉस, मैंने यह बातचीत अपनी मेमोरी में सेव कर ली है।")
    }

    fun addManualMemoryFact(factText: String) {
        val cleaned = factText.trim()
        if (cleaned.isEmpty()) return
        SuaStateRepository.addMemoryFact(cleaned, isAuto = false, prefs = prefs)
        SuaVoiceSpeaker.speak("जी बॉस, यह बात मोको की मेमोरी में जुड़ गई है।")
    }

    fun deleteMemoryFact(memoryId: Long) {
        SuaStateRepository.deleteMemoryItem(memoryId, prefs)
    }

    fun clearAllMemories() {
        SuaStateRepository.clearMemories(prefs)
    }

    fun testVoiceGreeting() {
        SuaStateRepository.updateLastReply("MOKO VOICE TEST // Speaking Hindi voice greeting.")
        SuaVoiceSpeaker.speak("नमस्ते बॉस! मेरा नाम मोको है। जब भी कोई हाय मोको बोलेगा, मैं तुरंत आपका काम करूँगी।")
    }

    fun saveApiKey(inputKey: String) {
        val cleaned = inputKey.trim()
        if (cleaned.isEmpty()) {
            _settingsBannerMessage.value = "ERROR // Please enter a valid Google Gemini API Key."
            SuaVoiceSpeaker.speak("कृपया सही गूगल एपीआई की दर्ज करें।")
            return
        }
        prefs.saveUserApiKey(cleaned)
        _savedApiKey.value = cleaned
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        _settingsBannerMessage.value = "ENCRYPTED & SAVED // Google Gemini API Key stored securely in Android Keystore."
        SuaVoiceSpeaker.speak("जी बॉस, आपकी गूगल एपीआई की सुरक्षित रूप से सेव हो गई है।")
    }

    fun deleteApiKey() {
        prefs.deleteUserApiKey()
        _savedApiKey.value = ""
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        _settingsBannerMessage.value = "DELETED // Saved Google Gemini API Key removed from encrypted storage."
        SuaVoiceSpeaker.speak("जी बॉस, एपीआई की डिलीट कर दी गई है।")
    }

    fun setGeminiModel(modelId: String) {
        prefs.setGeminiModel(modelId)
        _geminiModel.value = prefs.getGeminiModel()
        _settingsBannerMessage.value = "GEMINI MODEL UPDATED // Active model set to ${_geminiModel.value}"
    }

    fun clearSettingsBanner() {
        _settingsBannerMessage.value = null
    }

    fun setContinuousBackground(enabled: Boolean) {
        prefs.setContinuousBackgroundEnabled(enabled)
        _continuousBgEnabled.value = enabled
        val msg = if (enabled) "जी बॉस, कंटीन्यूअस बैकग्राउंड मोड चालू कर दिया गया है।" else "कंटीन्यूअस बैकग्राउंड मोड बंद कर दिया गया है।"
        SuaVoiceSpeaker.speak(msg)
    }

    fun setWakeWordRequired(required: Boolean) {
        prefs.setWakeWordRequiredInBackground(required)
        _wakeWordRequired.value = required
        val msg = if (required) {
            "जी बॉस, अब बैकग्राउंड में कमांड देने से पहले हाय मोको बोलना ज़रूरी होगा।"
        } else {
            "जी बॉस, वेक-वर्ड बंद कर दिया गया है।"
        }
        SuaVoiceSpeaker.speak(msg)
    }

    fun setVoiceLanguage(localeTag: String) {
        prefs.setVoiceLocale(localeTag)
        _voiceLocale.value = localeTag
        SuaVoiceSpeaker.applyLocale(localeTag)
        _settingsBannerMessage.value = "VOICE ENGINE // Output voice profile updated to $localeTag"
        SuaVoiceSpeaker.speak("जी बॉस, मेरी वॉइस प्रोफाइल अपडेट हो गई है।")
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
        _settingsBannerMessage.value = "CONTACT SAVED // ${newContact.name} ($cleanPhone) added to Moko Speed Dial."
        SuaVoiceSpeaker.speak("जी बॉस, ${newContact.name} का नंबर सेव कर लिया गया है।")
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
        SuaStateRepository.updateLastReply("MOKO SECURITY // Screen unlocked.")
        SuaVoiceSpeaker.speak("जी बॉस, स्क्रीन अनलॉक कर दी गई है।")
    }

    fun clearCommandHistory() {
        SuaStateRepository.clearLogs(prefs)
    }
}
