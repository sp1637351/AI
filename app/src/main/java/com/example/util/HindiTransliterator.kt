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
     * Cleans common Hindi/English filler words from a spoken contact query so real device contacts match cleanly.
     */
    fun cleanSpokenContactQuery(raw: String): String {
        return raw.trim()
            .replace(
                Regex(
                    "(?i)\\b(call|dial|phone|fone|message|sms|msg|whatsapp|send|to|on|par|pe|ko|ka|ki|ke|number|no|please|jara|zara|bhai|bhaiya|didi|ji|sir|boss|कॉल|फोन|फ़ोन|मैसेज|संदेश|व्हाट्सएप|वाट्सएप|को|का|की|के|नंबर|पर|पे|जरा|ज़रा|प्लीज|जी|भाई|भैया|दीदी|लगाओ|लगा|करो|कर|दो|भेजो|बोलो)\\b"
                ),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }

    /**
     * Returns true if a spoken contact name (in Hindi or English) matches a real device contact name.
     */
    fun namesMatch(spokenQuery: String, contactDisplayName: String): Boolean {
        val qClean = cleanSpokenContactQuery(spokenQuery).ifEmpty { spokenQuery.trim() }
        val cClean = contactDisplayName.trim()
        if (qClean.isEmpty() || cClean.isEmpty()) return false

        // 1. Direct case-insensitive substring match
        if (cClean.contains(qClean, ignoreCase = true) || qClean.contains(cClean, ignoreCase = true)) {
            return true
        }

        // 2. Romanized transliteration & fuzzy token match
        val qRoman = toRoman(qClean).lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
        val cRoman = toRoman(cClean).lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
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
                    if (qSkel.length >= 2 && (qSkel == cSkel || cSkel.startsWith(qSkel) || qSkel.startsWith(cSkel))) {
                        return true
                    }
                    // Fuzzy Levenshtein match for speech recognition variations ("shashank" vs "sashank", "rohit" vs "roheet")
                    if (qt.length >= 4 && ct.length >= 4 && levenshteinDistance(qt, ct) <= 2) {
                        return true
                    }
                    if (qSkel.length >= 3 && cSkel.length >= 3 && levenshteinDistance(qSkel, cSkel) <= 1) {
                        return true
                    }
                }
            }
        }

        // 3. Full phonetic skeleton match
        val qSkelFull = phoneticSkeleton(qClean)
        val cSkelFull = phoneticSkeleton(cClean)
        if (qSkelFull.length >= 2 && (cSkelFull == qSkelFull || cSkelFull.startsWith(qSkelFull) || qSkelFull.startsWith(cSkelFull))) {
            return true
        }

        return false
    }

    /**
     * Converts spoken Hindi/English number words into digits if a user dictates a phone number.
     */
    fun extractSpokenDigits(input: String): String {
        val wordToDigit = mapOf(
            "zero" to "0", "शून्य" to "0", "जीरो" to "0", "ज़ीरो" to "0",
            "one" to "1", "एक" to "1", "वन" to "1",
            "two" to "2", "दो" to "2", "टू" to "2",
            "three" to "3", "तीन" to "3", "थ्री" to "3",
            "four" to "4", "चार" to "4", "फोर" to "4",
            "five" to "5", "पांच" to "5", "पाँच" to "5", "फाइव" to "5",
            "six" to "6", "छह" to "6", "छे" to "6", "सिक्स" to "6",
            "seven" to "7", "सात" to "7", "सेवन" to "7",
            "eight" to "8", "आठ" to "8", "एट" to "8",
            "nine" to "9", "नौ" to "9", "नाइन" to "9"
        )
        var replaced = input.lowercase()
        for ((word, digit) in wordToDigit) {
            replaced = replaced.replace(word, digit)
        }
        return replaced.replace(Regex("[^0-9+]"), "")
    }

    /**
     * Detects if the user spoke an immediate "Stop" / "Ruko" / "Chup" interruption command to halt Moko's speech or task.
     */
    fun isStopCommand(rawSpoken: String, assistantName: String = "Moko"): Boolean {
        val trimmed = rawSpoken.trim()
        if (trimmed.isEmpty()) return false
        val (_, afterWake) = checkAndStripWakeWord(trimmed, assistantName)
        val clean = afterWake.ifEmpty { trimmed }.trim().lowercase()

        // Do not confuse hardware turn-off commands ("bluetooth stop", "music stop", "flashlight band") with stopping Moko
        val hardwareWords = listOf(
            "bluetooth", "ब्लूटूथ", "internet", "इंटरनेट", "wifi", "वाईफाई", "वाई-फाई",
            "flashlight", "फ्लैशलाइट", "torch", "टॉर्च", "light", "लाइट",
            "screen share", "स्क्रीन शेयर", "alarm", "अलार्म"
        )
        if (hardwareWords.any { clean.contains(it) }) return false

        val exactStopPhrases = setOf(
            "stop", "stop it", "stop now", "stop speaking", "stop talking", "stop working",
            "ruko", "ruk jao", "bas", "bas karo", "chup", "chup ho jao", "shant", "shant ho jao",
            "cancel", "abort", "quiet", "shut up", "mat bolo", "bolna band karo", "kaam roko",
            "स्टॉप", "रुको", "रुक जाओ", "बस", "बस करो", "चुप", "चुप हो जाओ",
            "शांत", "शांत हो जाओ", "कैंसिल", "मत बोलो", "बोलना बंद करो", "काम रोको", "बंद करो"
        )
        if (clean in exactStopPhrases) return true

        return Regex("^(stop|ruko|ruk jao|bas karo|chup ho jao|स्टॉप|रुको|रुक जाओ|बस करो|चुप हो जाओ)\\b")
            .containsMatchIn(clean)
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

    /**
     * Fixes common Android SpeechRecognizer misrecognitions in Hindi, Hinglish, and English.
     */
    fun normalizeVoiceMistakes(rawInput: String): String {
        var text = rawInput.trim()
        if (text.isEmpty()) return ""
        val replacements = listOf(
            "एक ट्यूब" to "यूट्यूब",
            "युटुब" to "यूट्यूब",
            "यूटुब" to "यूट्यूब",
            "यू ट्यूब" to "यूट्यूब",
            "यह ट्यूब" to "यूट्यूब",
            "वाट्स अप" to "व्हाट्सएप",
            "व्हाट्स अप" to "व्हाट्सएप",
            "वाट्सएप" to "व्हाट्सएप",
            "वॉट्सऐप" to "व्हाट्सएप",
            "वॉट्सएप" to "व्हाट्सएप",
            "म्युजिक" to "म्यूजिक",
            "मुजिक" to "म्यूजिक",
            "म्यूज़िक" to "म्यूजिक",
            "राष्टपति" to "राष्ट्रपति",
            "रास्ट्रपति" to "राष्ट्रपति",
            "प्राइमिनिस्टर" to "प्राइम मिनिस्टर",
            "बलूटूथ" to "ब्लूटूथ",
            "ब्लुटूथ" to "ब्लूटूथ",
            "फ्लैश लाइट" to "फ्लैशलाइट",
            "टार्च" to "टॉर्च"
        )
        for ((wrong, right) in replacements) {
            text = text.replace(wrong, right, ignoreCase = true)
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Detects and strips mid-sentence conversational correction prefixes such as
     * "नहीं नहीं प्राइम मिनिस्टर कौन है", "अरे नहीं...", "no no who is...", returning Pair(wasCorrection, strippedQuery).
     */
    fun stripConversationalCorrectionPrefix(rawInput: String): Pair<Boolean, String> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return Pair(false, "")
        val correctionRegex = Regex(
            "^(?i)(?:नहीं\\s+नहीं|नही\\s+नही|अरे\\s+नहीं|अरे\\s+यार|वो\\s+नहीं|मैं\\s+पूछ\\s+रहा\\s+हूँ|मेरा\\s+मतलब|नहीं|नही|रुको\\s+रुको|सुनो\\s+सुनो|no\\s+no|no\\s+wait|not\\s+that|i\\s+mean|wait\\s+no|actually)[,\\s]+(.+)$"
        )
        val match = correctionRegex.find(trimmed)
        return if (match != null) {
            val remainder = match.groupValues[1].trim()
            if (remainder.length >= 2) Pair(true, remainder) else Pair(false, trimmed)
        } else {
            Pair(false, trimmed)
        }
    }

    /**
     * Returns true if the spoken text starts with a natural barge-in / correction word ("नहीं नहीं", "अरे", "रुको", "no no", etc.).
     */
    fun isUserBargeInPhrase(rawInput: String, assistantName: String = "Moko"): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return false
        if (isStopCommand(trimmed, assistantName)) return true
        val (hasWake, _) = checkAndStripWakeWord(trimmed, assistantName)
        if (hasWake) return true
        val (isCorr, _) = stripConversationalCorrectionPrefix(trimmed)
        if (isCorr) return true
        val lower = trimmed.lowercase()
        return Regex("^(?i)(नहीं|नही|अरे|सुनो|रुको|प्राइम|प्रधानमंत्री|राष्ट्रपति|साउंड|आवाज़|आवाज|म्यूजिक|गाना|कॉल|no\\b|wait\\b|hey\\b|volume\\b)").containsMatchIn(lower)
    }

    /**
     * Converts any Hindi Devanagari or mixed phrase into 100% clean English (Roman script)
     * so YouTube, Google Search, Maps, and App Search NEVER receive Devanagari Hindi text.
     */
    fun toEnglishQuery(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return ""

        val wordMappings = mapOf(
            "अरिजीत सिंह" to "Arijit Singh",
            "अरिजीत" to "Arijit Singh",
            "केसरिया" to "Kesariya",
            "तुम ही हो" to "Tum Hi Ho",
            "चन्ना मेरेया" to "Channa Mereya",
            "अपना बना ले" to "Apna Bana Le",
            "हनुमान चालीसा" to "Shree Hanuman Chalisa",
            "राम सिया राम" to "Ram Siya Ram",
            "सैड सॉन्ग" to "Hindi Sad Song",
            "सैड" to "Sad",
            "उदास" to "Sad",
            "दुखी" to "Sad Emotional",
            "दर्द" to "Heartbreak Sad",
            "रोमांटिक" to "Romantic Love",
            "प्यार" to "Love Romantic",
            "पार्टी" to "Party Dance",
            "डांस" to "Dance Party",
            "भजन" to "Bhajan Devotional",
            "भक्ति" to "Bhakti Bhajan",
            "पुराना" to "Old Classic 90s",
            "पुराने" to "Old Classic 90s",
            "पंजाबी" to "Punjabi Hit",
            "भोजपुरी" to "Bhojpuri Hit",
            "हिंदी" to "Hindi",
            "सॉन्ग" to "Song",
            "गाना" to "Song",
            "म्यूजिक" to "Music"
        )
        for ((hi, en) in wordMappings) {
            text = text.replace(hi, en, ignoreCase = true)
        }

        if (text.any { it.code in 0x0900..0x097F }) {
            text = toRoman(text)
        }

        return text
            .replace(Regex("[^a-zA-Z0-9\\s'|\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}
