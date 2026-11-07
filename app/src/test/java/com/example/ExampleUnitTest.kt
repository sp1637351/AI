package com.example

import com.example.data.DeviceActionType
import com.example.data.SuaStateRepository
import com.example.util.HindiTransliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun verifyHindiToRomanTransliterationAndContactMatching() {
        assertTrue(HindiTransliterator.namesMatch("राहुल", "Rahul"))
        assertTrue(HindiTransliterator.namesMatch("राहुल", "Rahul Sharma"))
        assertTrue(HindiTransliterator.namesMatch("खुशी", "Khushi"))
        assertTrue(HindiTransliterator.namesMatch("ख़ुशी", "Khushi"))
        assertTrue(HindiTransliterator.namesMatch("रोहित", "Rohit"))
        assertFalse(HindiTransliterator.namesMatch("राहुल", "Priya"))
    }

    @Test
    fun verifyMokoWakeWordDetection() {
        val (woke1, cmd1) = HindiTransliterator.checkAndStripWakeWord("हाय मोको राहुल को कॉल करो")
        assertTrue(woke1)
        assertEquals("राहुल को कॉल करो", cmd1)

        val (woke2, cmd2) = HindiTransliterator.checkAndStripWakeWord("Hi Moko turn off flashlight")
        assertTrue(woke2)
        assertEquals("turn off flashlight", cmd2)

        val (woke3, cmd3) = HindiTransliterator.checkAndStripWakeWord("मोको फोन को लॉक करो")
        assertTrue(woke3)
        assertEquals("फोन को लॉक करो", cmd3)

        val (wokeCustom, cmdCustom) = HindiTransliterator.checkAndStripWakeWord("Ok Chitti open WhatsApp", "Chitti")
        assertTrue(wokeCustom)
        assertEquals("open WhatsApp", cmdCustom)

        assertEquals("MR. SHASHANK", com.example.data.SuaPreferences.DEVELOPER_NAME)

        val (woke4, _) = HindiTransliterator.checkAndStripWakeWord("राहुल को कॉल करो")
        assertFalse(woke4)
    }

    @Test
    fun verifyMokoMemoryAndHistoryDeleteAndKeep() {
        SuaStateRepository.clearLogs()
        SuaStateRepository.clearMemories()

        SuaStateRepository.addCommandLog(
            userCommand = "Mera naam Rahul hai",
            suaResponse = "Remembered your name is Rahul",
            actionType = DeviceActionType.MEMORY_SAVE
        )
        assertEquals(1, SuaStateRepository.commandLogs.value.size)

        val entry = SuaStateRepository.commandLogs.value.first()
        SuaStateRepository.pinCommandLogToMemory(entry)
        assertEquals(1, SuaStateRepository.memoryItems.value.size)
        assertTrue(SuaStateRepository.commandLogs.value.first().isPinnedToMemory)

        // Delete individual history item
        SuaStateRepository.deleteCommandLog(entry.id)
        assertTrue(SuaStateRepository.commandLogs.value.isEmpty())

        // Memory item still preserved until explicitly deleted
        assertEquals(1, SuaStateRepository.memoryItems.value.size)
        val memId = SuaStateRepository.memoryItems.value.first().id
        SuaStateRepository.deleteMemoryItem(memId)
        assertTrue(SuaStateRepository.memoryItems.value.isEmpty())
    }
}
