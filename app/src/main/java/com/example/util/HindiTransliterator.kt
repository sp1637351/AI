package com.example.util

object HindiTransliterator {

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l"
    )

    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ं' to "n", 'ँ' to "n", 'ः' to "h"
    )

    private const val HALANT = '्'
    private const val NUKTA = '़'

    /**
     * Converts Hindi Devanagari text (e.g. "राहुल", "खुशी", "रोहित") into Roman script ("rahul", "khushi", "rohit").
     */
    fun toRoman(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""

        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]

            if (independentVowels.containsKey(ch)) {
                sb.append(independentVowels[ch])
                i++
                continue
            }

            if (consonants.containsKey(ch)) {
                var base = consonants[ch] ?: ""
                var nextIdx = i + 1

                // Handle nukta (e.g., ख़ -> kh, ज़ -> z, फ़ -> f, ड़ -> d/r)
                if (nextIdx < text.length && text[nextIdx] == NUKTA) {
                    base = when (ch) {
                        'ज' -> "z"
                        'फ' -> "f"
                        'ड' -> "r"
                        'ढ' -> "rh"
                        else -> base
                    }
                    nextIdx++
                }

                sb.append(base)

                if (nextIdx < text.length) {
                    val nextChar = text[nextIdx]
                    when {
                        nextChar == HALANT -> {
                            nextIdx++
                        }
                        matras.containsKey(nextChar) -> {
                            sb.append(matras[nextChar])
                            nextIdx++
                            // Check if nasalization (ं / ँ) follows the matra
                            if (nextIdx < text.length && (text[nextIdx] == 'ं' || text[nextIdx] == 'ँ')) {
                                sb.append("n")
                                nextIdx++
                            }
                        }
                        else -> {
                            // Inherent 'a' vowel unless at end of word
                            val isEndOfWord = nextChar.isWhitespace() || !isDevanagari(nextChar)
                            if (!isEndOfWord) {
                                sb.append("a")
                            }
                        }
                    }
                }
                i = nextIdx
                continue
            }

            if (matras.containsKey(ch)) {
                sb.append(matras[ch])
                i++
                continue
            }

            if (ch != HALANT && ch != NUKTA) {
                sb.append(ch.lowercaseChar())
            }
            i++
        }

        return sb.toString().trim()
    }

    private fun isDevanagari(ch: Char): Boolean {
        return ch.code in 0x0900..0x097F
    }

    /**
     * Generates a normalized phonetic key so "राहुल" -> "rhl" and "Rahul" -> "rhl",
     * "ख़ुशी" -> "ksh" and "Khushi" -> "ksh", "विकास" -> "vks" and "Vikas" -> "vks".
     */
    fun phoneticSkeleton(input: String): String {
        val roman = toRoman(input).lowercase()
            .replace("ph", "f")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("chh", "c")
            .replace("ch", "c")
            .replace("jh", "j")
            .replace("th", "t")
            .replace("dh", "d")
            .replace("bh", "b")
            .replace("sh", "s")
            .replace("w", "v")
            .replace("z", "j")
            .replace("q", "k")
            .replace(Regex("[^a-z0-9]"), "")

        if (roman.isEmpty()) return ""

        // Keep first letter if vowel, strip interior vowels, collapse consecutive duplicates
        val sb = StringBuilder()
        for (idx in roman.indices) {
            val c = roman[idx]
            val isVowel = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'
            if (idx == 0 || !isVowel) {
                if (sb.isEmpty() || sb.last() != c) {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Returns true if a spoken contact name (in Hindi or English) matches a saved contact name.
     */
    fun namesMatch(spokenQuery: String, contactDisplayName: String): Boolean {
        val qClean = spokenQuery.trim()
        val cClean = contactDisplayName.trim()
        if (qClean.isEmpty() || cClean.isEmpty()) return false

        // 1. Direct case-insensitive substring match
        if (cClean.contains(qClean, ignoreCase = true) || qClean.contains(cClean, ignoreCase = true)) {
            return true
        }

        // 2. Romanized transliteration match
        val qRoman = toRoman(qClean).lowercase()
        val cRoman = toRoman(cClean).lowercase()
        if (qRoman.length >= 2 && cRoman.length >= 2) {
            if (cRoman.contains(qRoman) || qRoman.contains(cRoman)) {
                return true
            }
            // Check individual words in contact display name (e.g., "Rahul Sharma" vs "राहुल")
            val cTokens = cRoman.split(Regex("\\s+")).filter { it.length >= 2 }
            val qTokens = qRoman.split(Regex("\\s+")).filter { it.length >= 2 }
            for (qt in qTokens) {
                for (ct in cTokens) {
                    if (ct == qt || ct.startsWith(qt) || qt.startsWith(ct)) {
                        return true
                    }
                    val qSkel = phoneticSkeleton(qt)
                    val cSkel = phoneticSkeleton(ct)
                    if (qSkel.length >= 2 && qSkel == cSkel) {
                        return true
                    }
                }
            }
        }

        // 3. Full phonetic skeleton match
        val qSkelFull = phoneticSkeleton(qClean)
        val cSkelFull = phoneticSkeleton(cClean)
        if (qSkelFull.length >= 2 && (cSkelFull == qSkelFull || cSkelFull.startsWith(qSkelFull))) {
            return true
        }

        return false
    }

    private val wakeWordPatterns = listOf(
        "hey moko", "hi moko", "hello moko", "ok moko", "okay moko", "suno moko", "oye moko",
        "hey moco", "hi moco", "hello moco", "hi munh ko", "hey munh ko",
        "हे मोको", "हाय मोको", "हेलो मोको", "ओके मोको", "सुनो मोको", "अरे मोको",
        "हाय मुको", "हे मुको", "हाय मुंह को",
        "मोको", "मुको", "मोकोजी", "moko", "moco", "moku",
        // Keep legacy fallback if user says hey sua
        "hey sua", "सुआ", "sua"
    )

    /**
     * Checks if the spoken text contains the configured wake word (e.g. "Hi Moko", "Ok Moko",
     * or whatever custom name the user set in Settings) and returns Pair(hasWakeWord, commandAfterWakeWord).
     */
    fun checkAndStripWakeWord(
        rawSpoken: String,
        customAssistantName: String = "Moko"
    ): Pair<Boolean, String> {
        val trimmed = rawSpoken.trim()
        if (trimmed.isEmpty()) return Pair(false, "")
        val lower = trimmed.lowercase()

        val dynamicName = customAssistantName.trim().lowercase().ifEmpty { "moko" }
        val dynamicRoman = toRoman(dynamicName).lowercase().ifEmpty { dynamicName }

        val dynamicPatterns = buildList {
            for (n in listOf(dynamicName, dynamicRoman).distinct()) {
                if (n.isNotEmpty()) {
                    add("ok $n")
                    add("okay $n")
                    add("hey $n")
                    add("hi $n")
                    add("hello $n")
                    add("suno $n")
                    add("oye $n")
                    add("ओके $n")
                    add("हाय $n")
                    add("हे $n")
                    add("हेलो $n")
                    add("सुनो $n")
                    add(n)
                }
            }
            addAll(wakeWordPatterns)
        }.distinct()

        for (wake in dynamicPatterns) {
            val idx = lower.indexOf(wake)
            if (idx >= 0) {
                val beforeOk = idx == 0 || !lower[idx - 1].isLetterOrDigit()
                val endIdx = idx + wake.length
                val afterOk = endIdx >= lower.length || !lower[endIdx].isLetterOrDigit()
                if (beforeOk && afterOk) {
                    val remainder = (trimmed.substring(0, idx) + " " + trimmed.substring(endIdx))
                        .replace(Regex("^[\\s,.:;!-]+"), "")
                        .replace(Regex("^(जी|ji|please|प्लीज|जरा|ज़रा)\\s+", RegexOption.IGNORE_CASE), "")
                        .trim()
                    return Pair(true, remainder)
                }
            }
        }
        return Pair(false, trimmed)
    }

    /**
     * Converts Roman/Hinglish words into Hindi Devanagari when the user asks to type a WhatsApp/SMS in Hindi.
     */
    fun toHindiDevanagari(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        // If already contains Devanagari characters, return as-is
        if (text.any { it.code in 0x0900..0x097F }) return text

        val dictionary = mapOf(
            "namaste" to "नमस्ते",
            "hello" to "हेलो",
            "hi" to "हाय",
            "kaise" to "कैसे",
            "kaisa" to "कैसा",
            "kaisi" to "कैसी",
            "ho" to "हो",
            "hai" to "है",
            "hain" to "हैं",
            "main" to "मैं",
            "mai" to "मैं",
            "aap" to "आप",
            "tum" to "तुम",
            "kahan" to "कहाँ",
            "kaha" to "कहाँ",
            "kab" to "कब",
            "kya" to "क्या",
            "aa" to "आ",
            "raha" to "रहा",
            "rahi" to "रही",
            "hu" to "हूँ",
            "hoon" to "हूँ",
            "ghar" to "घर",
            "par" to "पर",
            "pe" to "पे",
            "abhi" to "अभी",
            "baad" to "बाद",
            "me" to "में",
            "mein" to "में",
            "call" to "कॉल",
            "karo" to "करो",
            "karna" to "करना",
            "jaldi" to "जल्दी",
            "aao" to "आओ",
            "milte" to "मिलते",
            "kal" to "कल",
            "aaj" to "आज",
            "haan" to "हाँ",
            "nahi" to "नहीं",
            "ok" to "ओके",
            "theek" to "ठीक",
            "thik" to "ठीक",
            "bhai" to "भाई",
            "dost" to "दोस्त",
            "good" to "गुड",
            "morning" to "मॉर्निंग",
            "night" to "नाइट"
        )

        return text.split(Regex("\\s+")).joinToString(" ") { word ->
            val clean = word.lowercase().replace(Regex("[^a-z]"), "")
            dictionary[clean] ?: word
        }
    }

    /**
     * Formats a phone number digit-by-digit for clear voice readout ("नंबर बोलो" / "speak both numbers").
     */
    fun formatPhoneForSpeech(phoneNumber: String, inEnglish: Boolean): String {
        val hindiDigits = mapOf(
            '0' to "ज़ीरो", '1' to "वन", '2' to "टू", '3' to "थ्री", '4' to "फोर",
            '5' to "फाइव", '6' to "सिक्स", '7' to "सेवन", '8' to "एट", '9' to "नाइन", '+' to "प्लस"
        )
        return phoneNumber.trim().map { ch ->
            if (inEnglish) {
                ch.toString()
            } else {
                hindiDigits[ch] ?: ch.toString()
            }
        }.joinToString(" ")
    }

    /**
     * Detects whether the user spoke in English vs Hindi/Hinglish so Moko replies in the same language.
     */
    fun isPrimarilyEnglish(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty()) return false
        // Any Devanagari character means Hindi
        if (text.any { it.code in 0x0900..0x097F }) return false

        val lower = text.lowercase()
        val hinglishMarkers = listOf(
            "karo", "kholo", "laga", "lagao", "batao", "bhejo", "bhej", "mera", "meri",
            "tumhara", "tera", "kya", "kaise", "kahan", "kitna", "kitni", "chalu", "band",
            "badhao", "ghatao", "yaad", "rakhna", "rakho", "kisne", "banaya", "kaun",
            "bolna", "bolo", "dono", "wala", "wali", "mein", "haan", "nahi", "ko ", " se ", " par "
        )
        if (hinglishMarkers.any { lower.contains(it) }) return false

        return true
    }
}
