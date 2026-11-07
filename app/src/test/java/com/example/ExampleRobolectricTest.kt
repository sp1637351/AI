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
    }
}
