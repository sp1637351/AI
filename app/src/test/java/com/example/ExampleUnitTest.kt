package com.example

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
    fun verifyWakeWordDetection() {
        val (woke1, cmd1) = HindiTransliterator.checkAndStripWakeWord("सुआ राहुल को कॉल करो")
        assertTrue(woke1)
        assertEquals("राहुल को कॉल करो", cmd1)

        val (woke2, cmd2) = HindiTransliterator.checkAndStripWakeWord("Hey SUA turn off flashlight")
        assertTrue(woke2)
        assertEquals("turn off flashlight", cmd2)

        val (woke3, _) = HindiTransliterator.checkAndStripWakeWord("राहुल को कॉल करो")
        assertFalse(woke3)
    }
}
