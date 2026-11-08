package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.SuaBrain
import com.example.data.DeviceActionType
import com.example.data.MokoOperatingMode
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `verify SUA app name and core Hindi voice commands`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Moko", appName)

        val prefs = SuaPreferences(context)
        prefs.saveUserApiKey("AIzaSyTestKey12345")
        assertEquals("AIzaSyTestKey12345", prefs.getSavedUserApiKey())
        prefs.deleteUserApiKey()
        assertEquals("", prefs.getSavedUserApiKey())

        val brain = SuaBrain(prefs)

        val callCmd = brain.parseHardwareIntentLocally("खुशी को कॉल लगाओ")
        assertNotNull(callCmd)
        assertEquals(DeviceActionType.CALL, callCmd?.action)
        assertEquals("खुशी", callCmd?.target)

        val flashOffCmd = brain.parseHardwareIntentLocally("फ्लैशलाइट बंद करो")
        assertNotNull(flashOffCmd)
        assertEquals(DeviceActionType.FLASHLIGHT_OFF, flashOffCmd?.action)

        val lockCmd = brain.parseHardwareIntentLocally("फोन को लॉक करो")
        assertNotNull(lockCmd)
        assertEquals(DeviceActionType.LOCK_PHONE, lockCmd?.action)

        val brightCmd = brain.parseHardwareIntentLocally("ब्राइटनेस बढ़ाओ")
        assertNotNull(brightCmd)
        assertEquals(DeviceActionType.BRIGHTNESS_UP, brightCmd?.action)

        // Verify Bluetooth, Internet, and 3-mode voice switching
        val btOffCmd = brain.parseHardwareIntentLocally("Turn off Bluetooth")
        assertNotNull(btOffCmd)
        assertEquals(DeviceActionType.BLUETOOTH_OFF, btOffCmd?.action)

        val netOffCmd = brain.parseHardwareIntentLocally("इंटरनेट बंद करो")
        assertNotNull(netOffCmd)
        assertEquals(DeviceActionType.INTERNET_OFF, netOffCmd?.action)

        val sleepModeCmd = brain.parseHardwareIntentLocally("Go to sleep mode")
        assertNotNull(sleepModeCmd)
        assertEquals(DeviceActionType.SWITCH_MODE, sleepModeCmd?.action)
        assertEquals("SLEEP", sleepModeCmd?.target)

        // Verify 60-second continuous conversation window & 3-mode repository state
        SuaStateRepository.setOperatingMode(MokoOperatingMode.SLEEP)
        assertEquals(MokoOperatingMode.SLEEP, SuaStateRepository.operatingMode.value)
        SuaStateRepository.markWakeWordDetected()
        assertTrue(SuaStateRepository.isWithinWakeConversationWindow())
        assertTrue(SuaStateRepository.wakeSessionRemainingSeconds.value in 59..60)

        SuaStateRepository.setOperatingMode(MokoOperatingMode.OFFLINE)
        assertEquals(MokoOperatingMode.OFFLINE, SuaStateRepository.operatingMode.value)
        assertEquals(0, SuaStateRepository.wakeSessionRemainingSeconds.value)

        // Verify Screen Unlock, Screen Share Analyze, Voice Click, and Offline System Settings
        val unlockCmd = brain.parseHardwareIntentLocally("लॉक खोलो")
        assertNotNull(unlockCmd)
        assertEquals(DeviceActionType.UNLOCK_PHONE, unlockCmd?.action)

        val screenShareCmd = brain.parseHardwareIntentLocally("स्क्रीन पर क्या है")
        assertNotNull(screenShareCmd)
        assertEquals(DeviceActionType.SCREEN_SHARE_ANALYZE, screenShareCmd?.action)

        val voiceClickCmd = brain.parseHardwareIntentLocally("सेटिंग पर क्लिक करो")
        assertNotNull(voiceClickCmd)
        assertEquals(DeviceActionType.VOICE_CLICK, voiceClickCmd?.action)

        val hotspotCmd = brain.parseHardwareIntentLocally("हॉटस्पॉट चालू करो")
        assertNotNull(hotspotCmd)
        assertEquals(DeviceActionType.OPEN_SYSTEM_SETTING, hotspotCmd?.action)
        assertEquals("HOTSPOT", hotspotCmd?.target)

        val silentCmd = brain.parseHardwareIntentLocally("फोन साइलेंट करो")
        assertNotNull(silentCmd)
        assertEquals(DeviceActionType.RINGER_MODE_SET, silentCmd?.action)
        assertEquals("SILENT", silentCmd?.target)

        // Verify YouTube Music Playback ("यूट्यूब पर म्यूजिक चलाओ", "यूट्यूब पर केसरिया गाना बजाओ")
        val ytMusicCmd = brain.parseHardwareIntentLocally("यूट्यूब पर म्यूजिक चलाओ")
        assertNotNull(ytMusicCmd)
        assertEquals(DeviceActionType.PLAY_YOUTUBE, ytMusicCmd?.action)

        val ytSongCmd = brain.parseHardwareIntentLocally("यूट्यूब पर केसरिया गाना बजाओ")
        assertNotNull(ytSongCmd)
        assertEquals(DeviceActionType.PLAY_YOUTUBE, ytSongCmd?.action)

        // Verify "यूट्यूब खोलो एक अच्छा सा गाना लगाओ" & "एक अच्छा सा हिंदी म्यूजिक बजाओ" never put Hindi or filler words in YouTube query
        val ytGoodSongCmd = brain.parseHardwareIntentLocally("यूट्यूब खोलो एक अच्छा सा गाना लगाओ")
        assertNotNull(ytGoodSongCmd)
        assertEquals(DeviceActionType.PLAY_YOUTUBE, ytGoodSongCmd?.action)
        assertEquals("Kesariya Arijit Singh", ytGoodSongCmd?.target)

        val ytGoodHindiMusicCmd = brain.parseHardwareIntentLocally("एक अच्छा सा हिंदी म्यूजिक बजाओ")
        assertNotNull(ytGoodHindiMusicCmd)
        assertEquals(DeviceActionType.PLAY_YOUTUBE, ytGoodHindiMusicCmd?.action)
        assertEquals("Kesariya Arijit Singh", ytGoodHindiMusicCmd?.target)

        // Verify Emotion-Aware Sad Song Selection in 100% English
        val ytSadSongCmd = brain.parseHardwareIntentLocally("थोड़ा उदास हूँ एक अच्छा सा सैड सॉन्ग लगाओ")
        assertNotNull(ytSadSongCmd)
        assertEquals(DeviceActionType.PLAY_YOUTUBE, ytSadSongCmd?.action)
        assertEquals("Tum Hi Ho Arijit Singh Sad Song", ytSadSongCmd?.target)

        // Verify "म्यूजिक का साउंड कम करो" adjusts volume instead of opening YouTube
        val musicVolDownCmd = brain.parseHardwareIntentLocally("म्यूजिक का साउंड कम करो")
        assertNotNull(musicVolDownCmd)
        assertEquals(DeviceActionType.VOLUME_DOWN, musicVolDownCmd?.action)

        // Verify Instant Stop Command ("स्टॉप", "रुको", "Stop")
        val stopCmdHi = brain.parseHardwareIntentLocally("स्टॉप")
        assertNotNull(stopCmdHi)
        assertEquals(DeviceActionType.STOP_ACTION, stopCmdHi?.action)

        val stopCmdEn = brain.parseHardwareIntentLocally("Stop")
        assertNotNull(stopCmdEn)
        assertEquals(DeviceActionType.STOP_ACTION, stopCmdEn?.action)

        // Verify Conversational Context & Mid-Sentence Correction ("भारत का राष्ट्रपति कौन है" -> "नहीं नहीं प्राइम मिनिस्टर कौन है")
        val presidentReply = brain.parseKnowledgeAndContextLocally("भारत का राष्ट्रपति कौन है")
        assertNotNull(presidentReply)
        assertTrue(presidentReply!!.displayReply.contains("Droupadi Murmu"))

        val pmCorrectionReply = brain.parseKnowledgeAndContextLocally("नहीं नहीं प्राइम मिनिस्टर कौन है")
        assertNotNull(pmCorrectionReply)
        assertTrue(pmCorrectionReply!!.displayReply.contains("Narendra Modi"))

        // Verify WhatsApp Open vs WhatsApp Message
        val openWaCmd = brain.parseHardwareIntentLocally("व्हाट्सएप खोलो")
        assertNotNull(openWaCmd)
        assertEquals(DeviceActionType.OPEN_APP, openWaCmd?.action)
        assertEquals("Whatsapp", openWaCmd?.target)

        val msgWaCmd = brain.parseHardwareIntentLocally("राहुल को व्हाट्सएप पर हेलो भेजो")
        assertNotNull(msgWaCmd)
        assertEquals(DeviceActionType.WHATSAPP_MSG, msgWaCmd?.action)
        assertEquals("राहुल", msgWaCmd?.target)
        assertEquals("हेलो", msgWaCmd?.message)

        SuaStateRepository.setScreenShareActive(true)
        assertTrue(SuaStateRepository.isScreenShareActive.value)
    }
}
