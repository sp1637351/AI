package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.SuaBrain
import com.example.control.SuaDeviceController
import com.example.data.DeviceActionType
import com.example.data.MokoOperatingMode
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.service.SuaVoiceService
import com.example.service.SuaVoiceSpeaker
import com.example.util.HindiTransliterator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MokoAssistantTest {

    private lateinit var context: Context
    private lateinit var prefs: SuaPreferences
    private lateinit var controller: SuaDeviceController
    private lateinit var brain: SuaBrain

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = SuaPreferences(context)
        prefs.setVoicePrivacyConsentAccepted(false)
        prefs.deleteUserApiKey()
        prefs.saveKalpaApiKey("")
        SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
        SuaStateRepository.updateServiceError(null)
        controller = SuaDeviceController(context)
        brain = SuaBrain(prefs, controller)
    }

    @Test
    fun kalpaKey_isAutoLoadedOnInstall_andCanBeChangedOrRestoredEncrypted() {
        // 1. On fresh install (when preference is not set), getKalpaApiKey() automatically pre-loads the built-in key
        val restoredDefault = prefs.restoreDefaultKalpaApiKey()
        assertTrue(
            "Built-in Kalpa API key must be automatically available on install without manual entry",
            restoredDefault.isNotEmpty()
        )
        assertEquals(restoredDefault, prefs.getKalpaApiKey())

        // 2. After installing, the user can change/update the key anytime and it is stored encrypted
        prefs.saveKalpaApiKey("sk-test-custom-user-key")
        assertEquals("sk-test-custom-user-key", prefs.getKalpaApiKey())

        // Verify the raw SharedPreferences encrypts the key (never plain-text)
        val rawPrefs = context.getSharedPreferences("sua_assistant_prefs", Context.MODE_PRIVATE)
        val rawStored = rawPrefs.getString("kalpa_voice_api_key_enc_v2", "") ?: ""
        assertFalse("Encrypted preference must not equal plain-text key", rawStored.contains("sk-test-custom-user-key"))

        // 3. User can also restore the built-in default Kalpa key anytime
        prefs.restoreDefaultKalpaApiKey()
        assertEquals(restoredDefault, prefs.getKalpaApiKey())
    }

    @Test
    fun personalData_isEncryptedAndCappedAtMaxLimit() {
        prefs.setUserName("Shashank Boss")
        assertEquals("Shashank Boss", prefs.getUserName())

        val rawPrefs = context.getSharedPreferences("sua_assistant_prefs", Context.MODE_PRIVATE)
        val rawUser = rawPrefs.getString("custom_user_name_enc_v2", "") ?: ""
        assertFalse("Encrypted user name must not be stored in plain text", rawUser.contains("Shashank Boss"))

        // Verify bounded log retention (MAX_COMMAND_LOGS = 50)
        SuaStateRepository.clearLogs(prefs)
        for (i in 1..65) {
            SuaStateRepository.addCommandLog(
                userCommand = "Test Command $i",
                suaResponse = "Test Response $i",
                actionType = DeviceActionType.AI_CHAT,
                actionSuccess = true,
                prefs = prefs
            )
        }
        assertEquals(
            "Command logs must be strictly capped at MAX_COMMAND_LOGS (50)",
            SuaPreferences.MAX_COMMAND_LOGS,
            SuaStateRepository.commandLogs.value.size
        )
        assertEquals(
            "Persisted logs must also be capped at MAX_COMMAND_LOGS (50)",
            SuaPreferences.MAX_COMMAND_LOGS,
            prefs.loadCommandLogs().size
        )
    }

    @Test
    fun voicePrivacyConsent_gatesMicrophoneServiceActivation() {
        prefs.setVoicePrivacyConsentAccepted(false)
        SuaVoiceService.startServiceInMode(context, MokoOperatingMode.ONLINE)

        // Must remain OFFLINE and surface privacy consent error + UI action until user accepts
        assertEquals(MokoOperatingMode.OFFLINE, SuaStateRepository.operatingMode.value)
        assertNotNull(SuaStateRepository.serviceErrorMessage.value)
        assertTrue(
            SuaStateRepository.serviceErrorMessage.value!!.contains("PRIVACY CONSENT REQUIRED")
        )
        assertEquals("SHOW_PRIVACY_CONSENT", SuaStateRepository.pendingUiNavigationAction.value)
    }

    @Test
    fun unlockPhoneScreen_neverFalselyClaimsOsLockBypassed() {
        SuaStateRepository.setScreenLockedOverlay(true)
        val result = controller.unlockPhoneScreen()
        assertTrue(result.success)
        assertFalse(SuaStateRepository.isScreenLockedOverlay.value)
        assertTrue(
            "Unlock reply must honestly distinguish in-app overlay vs biometric/PIN lock",
            result.displayReply.contains("OVERLAY", ignoreCase = true) ||
                result.displayReply.contains("BIOMETRIC", ignoreCase = true)
        )
    }

    @Test
    fun voiceClick_distinguishesInAppNavigationAndAccessibilityService() {
        val navResult = controller.executeVoiceClick("Open Settings")
        assertTrue(navResult.success)
        assertEquals("OPEN_SETTINGS", SuaStateRepository.pendingUiNavigationAction.value)

        // Cross-app target when AccessibilityService is not enabled in JVM test should give honest guidance
        val crossAppResult = controller.executeVoiceClick("Subscribe Button")
        assertFalse(crossAppResult.success)
        assertTrue(
            crossAppResult.displayReply.contains("Accessibility", ignoreCase = true)
        )
    }

    @Test
    fun youtubeMusic_resolvesCleanEnglishCuratedQuery_andNeverSearchesRawHindiSentence() = runBlocking {
        val rawCommand = "एक अच्छा सा हिंदी म्यूजिक बजाओ"
        val parsed = brain.interpretCommand(rawCommand)
        assertEquals(DeviceActionType.PLAY_YOUTUBE, parsed.action)
        assertTrue(
            "Query must be 100% ASCII English: ${parsed.target}",
            parsed.target.all { ch: Char -> ch.code < 128 }
        )
        assertFalse("Query must not contain 'ek achcha'", parsed.target.lowercase().contains("ek achcha"))
        assertFalse("Query must not contain 'bajao'", parsed.target.lowercase().contains("bajao"))
    }

    @Test
    fun emotionAwareMusic_mapsSadMoodAndArtistCommandsToEnglishQueries() = runBlocking {
        val sadParsed = brain.interpretCommand("मेरा मूड बहुत खराब है एक सैड सॉन्ग लगाओ")
        assertEquals(DeviceActionType.PLAY_YOUTUBE, sadParsed.action)
        assertTrue(sadParsed.target.contains("Arijit Singh", ignoreCase = true))

        val bhojpuriParsed = brain.interpretCommand("पवन सिंह का गाना बजाओ")
        assertEquals(DeviceActionType.PLAY_YOUTUBE, bhojpuriParsed.action)
        assertTrue(bhojpuriParsed.target.contains("Pawan Singh", ignoreCase = true))
    }

    @Test
    fun selfEchoAndBargeInDetection_filtersSpeakerEcho_andAllowsUserCorrection() {
        SuaVoiceSpeaker.speak("जी बॉस, मोको ऑनलाइन हो गई हूँ।")
        assertTrue(
            "Assistant signature phrase must be detected as self-echo",
            SuaVoiceSpeaker.isLikelySelfEcho("जी बॉस मोको ऑनलाइन हो गई हूँ")
        )
        assertFalse(
            "User correction must never be blocked as self-echo",
            SuaVoiceSpeaker.isLikelySelfEcho("नहीं नहीं प्राइम मिनिस्टर कौन है")
        )
        assertTrue(
            "User barge-in phrase must be detected",
            HindiTransliterator.isUserBargeInPhrase("नहीं नहीं प्राइम मिनिस्टर कौन है", "Moko")
        )
    }

    @Test
    fun developerIdentity_alwaysAttributesMrShashank() = runBlocking {
        val devParsed = brain.interpretCommand("तुम्हें किसने डेवलप किया है")
        assertTrue(devParsed.displayReply.contains("MR. SHASHANK"))
        assertTrue(devParsed.spokenReply.contains("शशांक"))
    }
}
