package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SuaBrain
import com.example.control.SuaDeviceController
import com.example.data.CommandLogEntry
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
    val audioRmsDb: StateFlow<Float> = SuaStateRepository.audioRmsDb

    private val _savedApiKey = MutableStateFlow(prefs.getSavedUserApiKey())
    val savedApiKey: StateFlow<String> = _savedApiKey.asStateFlow()

    private val _hasEffectiveApiKey = MutableStateFlow(prefs.getEffectiveApiKey().isNotEmpty())
    val hasEffectiveApiKey: StateFlow<Boolean> = _hasEffectiveApiKey.asStateFlow()

    private val _continuousBgEnabled = MutableStateFlow(prefs.isContinuousBackgroundEnabled())
    val continuousBgEnabled: StateFlow<Boolean> = _continuousBgEnabled.asStateFlow()

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
        refreshPermissionsAndHardware()
    }

    fun refreshPermissionsAndHardware() {
        controller.syncHardwareTelemetry()
        _batteryPercent.value = controller.getBatteryPercentage()
        _permissionStatuses.value = controller.getPermissionStatusList()
        _savedApiKey.value = prefs.getSavedUserApiKey()
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
    }

    fun toggleBackgroundVoiceService() {
        if (isBackgroundServiceRunning.value) {
            SuaVoiceService.stopService(appContext)
            SuaStateRepository.updateLiveTranscript("ARC CORE STANDBY // BACKGROUND SERVICE STOPPED")
            SuaStateRepository.updateLastReply("SUA STANDBY // Background voice loop disengaged.")
            SuaVoiceSpeaker.speak("जी सर, सुआ बैकग्राउंड मोड बंद कर दिया गया है।")
        } else {
            SuaVoiceService.startService(appContext)
        }
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
                SuaVoiceSpeaker.speak(result.finalSpokenReply)
            }
        }
    }

    fun testVoiceGreeting() {
        SuaStateRepository.updateLastReply("VOICE DIAGNOSTIC // Speaking Hindi audio test via SUA Voice Synthesis Core.")
        SuaVoiceSpeaker.speak("नमस्ते सर! मैं सुआ हूँ, आपकी आयरन मैन स्टाइल स्मार्ट असिस्टेंट। मेरे सारे सिस्टम पूरी तरह तैयार हैं।")
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
        _settingsBannerMessage.value = "VERIFIED // Google Gemini API Key saved and linked to SUA Neural Core."
        SuaVoiceSpeaker.speak("जी सर, आपकी गूगल एपीआई की सफलतापूर्वक सेव हो गई है।")
    }

    fun deleteApiKey() {
        prefs.deleteUserApiKey()
        _savedApiKey.value = ""
        _hasEffectiveApiKey.value = prefs.getEffectiveApiKey().isNotEmpty()
        _settingsBannerMessage.value = "PURGED // Saved Google Gemini API Key removed from memory."
        SuaVoiceSpeaker.speak("जी सर, एपीआई की डिलीट कर दी गई है।")
    }

    fun clearSettingsBanner() {
        _settingsBannerMessage.value = null
    }

    fun setContinuousBackground(enabled: Boolean) {
        prefs.setContinuousBackgroundEnabled(enabled)
        _continuousBgEnabled.value = enabled
        val msg = if (enabled) "जी सर, कंटीन्यूअस बैकग्राउंड मोड चालू कर दिया गया है।" else "कंटीन्यूअस बैकग्राउंड मोड बंद कर दिया गया है।"
        SuaVoiceSpeaker.speak(msg)
    }

    fun setVoiceLanguage(localeTag: String) {
        prefs.setVoiceLocale(localeTag)
        _voiceLocale.value = localeTag
        SuaVoiceSpeaker.applyLocale(localeTag)
        _settingsBannerMessage.value = "VOICE ENGINE // Output voice profile updated to $localeTag"
        SuaVoiceSpeaker.speak("जी सर, मेरी वॉइस प्रोफाइल अपडेट हो गई है।")
    }

    fun addOrUpdateQuickContact(name: String, aliasTag: String, phone: String) {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.isEmpty() || cleanPhone.isEmpty()) return
        val current = _quickContacts.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val newContact = QuickContact(
            name = cleanName,
            aliasTag = aliasTag.trim().ifEmpty { "PRIORITY // ${cleanName.uppercase()}" },
            phoneNumber = cleanPhone
        )
        if (existingIdx >= 0) {
            current[existingIdx] = newContact
        } else {
            current.add(0, newContact)
        }
        prefs.saveQuickContacts(current)
        _quickContacts.value = current
        _settingsBannerMessage.value = "CONTACT REGISTERED // ${newContact.name} ($cleanPhone) added to speed dial."
        SuaVoiceSpeaker.speak("जी सर, ${newContact.name} का नंबर सेव कर लिया गया है।")
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
        SuaStateRepository.updateLastReply("SECURITY PROTOCOL // Biometric & manual override verified. HUD unlocked.")
        SuaVoiceSpeaker.speak("जी सर, स्क्रीन अनलॉक कर दी गई है।")
    }

    fun clearCommandHistory() {
        SuaStateRepository.clearLogs(prefs)
    }
}
