package com.example.ai

import com.example.data.DeviceActionType
import com.example.data.ParsedCommand
import com.example.data.SuaPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SuaBrain(private val prefs: SuaPreferences) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun interpretCommand(rawInput: String): ParsedCommand = withContext(Dispatchers.IO) {
        val text = rawInput.trim()
        if (text.isEmpty()) {
            return@withContext ParsedCommand(
                action = DeviceActionType.AI_CHAT,
                displayReply = "SUA READY // Listening for your voice command.",
                spokenReply = "जी सर, मैं सुआ सुन रही हूँ। कृपया अपना कमांड बोलें।"
            )
        }

        // 1. ZERO-LATENCY FAST PATH: Execute unambiguous hardware commands immediately (<5ms)
        val localMatch = parseHardwareIntentLocally(text)
        if (localMatch != null) {
            return@withContext localMatch
        }

        // 2. GEMINI AI CORE PATH: For open-ended questions, complex requests, or conversation
        val apiKey = prefs.getEffectiveApiKey()
        if (apiKey.isNotEmpty()) {
            val geminiResult = callGeminiApi(text, apiKey)
            if (geminiResult != null) {
                return@withContext geminiResult
            }
        }

        // 3. INSTANT CONVERSATIONAL FALLBACK (English HUD display + Hindi voice speech)
        val lower = text.lowercase()
        return@withContext when {
            lower.contains("tumhara naam") || lower.contains("your name") ||
                text.contains("तुम्हारा नाम") || text.contains("कौन हो") || lower.contains("who are you") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "IDENTITY // I am SUA (Smart Universal Assistant), your personal tactical AI system.",
                    spokenReply = "मेरा नाम सुआ है सर। मैं आपकी पर्सनल वॉइस असिस्टेंट हूँ और आपके फोन के सारे काम कर सकती हूँ।"
                )
            }
            lower.contains("jarvis") || text.contains("जार्विस") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "SYSTEM CORRECTION // My designation is SUA, not Jarvis. Ready for your command, Sir.",
                    spokenReply = "मेरा नाम जार्विस नहीं, सुआ है सर! बताइए मुझे क्या काम करना है?"
                )
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") ||
                text.contains("नमस्ते") || text.contains("हेलो") || lower.contains("hey sua") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "SUA ONLINE // Greetings Sir. All systems are at 100% capacity. Awaiting your command.",
                    spokenReply = "नमस्ते सर! मैं सुआ ऑनलाइन हूँ। बताइए आज आपके फोन में क्या काम करूँ?"
                )
            }
            lower.contains("kaise ho") || lower.contains("how are you") || text.contains("कैसे हो") -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "DIAGNOSTICS // All neural and hardware subsystems operating at peak efficiency.",
                    spokenReply = "मैं एकदम बढ़िया हूँ सर, मेरे सारे सिस्टम पूरी तरह से काम कर रहे हैं।"
                )
            }
            apiKey.isEmpty() -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "COMMAND LOGGED // Hardware controls active. Add your Google Gemini API Key in Settings for full AI intelligence.",
                    spokenReply = "जी सर, फोन कंट्रोल कमांड चालू हैं। किसी भी सवाल के जवाब के लिए सेटिंग्स में अपनी गूगल एपीआई की सेव करें।"
                )
            }
            else -> {
                ParsedCommand(
                    action = DeviceActionType.AI_CHAT,
                    displayReply = "SUA CORE // Processed input: \"${sanitizeForHud(text)}\". Standing by.",
                    spokenReply = "जी सर, मैंने आपका कमांड रिसीव कर लिया है। बताइए मैं और क्या मदद करूँ?"
                )
            }
        }
    }

    /**
     * Converts any Devanagari transcript into clean Roman/English label for the HUD if needed.
     */
    fun sanitizeForHud(input: String): String {
        val trim = input.trim()
        // Map common Hindi phrases to clean English/Hinglish for HUD display
        return trim
            .replace("खुशी को कॉल लगाओ", "Call Khushi")
            .replace("फ्लैशलाइट बंद करो", "Turn Off Flashlight")
            .replace("फ्लैशलाइट चालू करो", "Turn On Flashlight")
            .replace("फोन को लॉक करो", "Lock Phone Screen")
            .replace("ब्राइटनेस बढ़ाओ", "Increase Brightness")
            .replace("ब्राइटनेस कम करो", "Decrease Brightness")
            .replace("साउंड फुल करो", "Set Volume 100%")
            .replace("म्यूजिक चलाओ", "Play Music")
            .replace("म्यूजिक बंद करो", "Pause Music")
            .replace("बैटरी कितनी है", "Check Battery Status")
    }

    fun parseHardwareIntentLocally(input: String): ParsedCommand? {
        val text = input.trim()
        val lower = text.lowercase()
        val numberRegex = Regex("(\\d{1,3})")
        val extractedNum = numberRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1

        // 1. FLASHLIGHT OFF / ON
        val hasFlashWord = lower.contains("flashlight") || lower.contains("flash light") ||
            lower.contains("torch") || text.contains("फ्लैशलाइट") || text.contains("फ्लैश") ||
            text.contains("टॉर्च") || text.contains("लाइट")
        if (hasFlashWord) {
            val isOff = lower.contains("off") || lower.contains("band") || lower.contains("bujha") ||
                text.contains("बंद") || text.contains("ऑफ") || text.contains("बुझा")
            return if (isOff) {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_OFF,
                    displayReply = "OPTICS STANDBY // Flashlight beam disengaged.",
                    spokenReply = "जी सर, फ्लैशलाइट बंद कर दी गई है।"
                )
            } else {
                ParsedCommand(
                    action = DeviceActionType.FLASHLIGHT_ON,
                    displayReply = "OPTICS ONLINE // Flashlight beam engaged at 100%.",
                    spokenReply = "जी सर, फ्लैशलाइट चालू कर दी गई है।"
                )
            }
        }

        // 2. LOCK PHONE
        val hasLockWord = lower.contains("lock") || text.contains("लॉक")
        if (hasLockWord && (lower.contains("phone") || lower.contains("screen") ||
                lower.contains("mobile") || text.contains("फोन") || text.contains("स्क्रीन") ||
                text.contains("मोबाइल") || lower.trim() == "lock" || text.contains("लॉक करो"))
        ) {
            return ParsedCommand(
                action = DeviceActionType.LOCK_PHONE,
                displayReply = "SECURITY PROTOCOL // Engaging device screen lock.",
                spokenReply = "जी सर, आपका फोन लॉक किया जा रहा है।"
            )
        }

        // 3. BRIGHTNESS
        val hasBrightnessWord = lower.contains("brightness") || lower.contains("roshni") ||
            text.contains("ब्राइटनेस") || text.contains("रोशनी") || text.contains("चमक")
        if (hasBrightnessWord) {
            val isDown = lower.contains("kam") || lower.contains("down") || lower.contains("decrease") ||
                lower.contains("low") || lower.contains("ghata") || text.contains("कम") ||
                text.contains("घटा") || text.contains("धीमा")
            val isUp = lower.contains("badha") || lower.contains("up") || lower.contains("increase") ||
                lower.contains("high") || lower.contains("full") || lower.contains("zyada") ||
                text.contains("बढ़ा") || text.contains("ज्यादा") || text.contains("तेज") || text.contains("फुल")
            return when {
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = 100,
                    displayReply = "LUMINANCE // Display brightness calibrated to 100%.",
                    spokenReply = "जी सर, ब्राइटनेस 100 प्रतिशत कर दी गई है।"
                )
                extractedNum in 5..100 -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_SET,
                    numericValue = extractedNum,
                    displayReply = "LUMINANCE // Display brightness calibrated to $extractedNum%.",
                    spokenReply = "जी सर, ब्राइटनेस $extractedNum प्रतिशत कर दी गई है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_DOWN,
                    displayReply = "LUMINANCE // Decreasing display brightness by 25%.",
                    spokenReply = "जी सर, स्क्रीन की ब्राइटनेस कम कर दी गई है।"
                )
                isUp -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "LUMINANCE // Increasing display brightness by 25%.",
                    spokenReply = "जी सर, स्क्रीन की ब्राइटनेस बढ़ा दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.BRIGHTNESS_UP,
                    displayReply = "LUMINANCE // Adjusting display brightness.",
                    spokenReply = "जी सर, ब्राइटनेस एडजस्ट कर दी गई है।"
                )
            }
        }

        // 4. SOUND / VOLUME
        val hasVolumeWord = lower.contains("volume") || lower.contains("sound") ||
            lower.contains("awaz") || lower.contains("aawaz") || lower.contains("mute") ||
            text.contains("साउंड") || text.contains("आवाज़") || text.contains("आवाज") ||
            text.contains("वॉल्यूम") || text.contains("म्यूट")
        if (hasVolumeWord) {
            val isMute = lower.contains("mute") || lower.contains("silent") ||
                text.contains("म्यूट") || text.contains("साइलेंट") || text.contains("बंद")
            val isDown = lower.contains("kam") || lower.contains("down") || lower.contains("decrease") ||
                lower.contains("low") || text.contains("कम") || text.contains("घटा") || text.contains("धीमा")
            return when {
                isMute -> ParsedCommand(
                    action = DeviceActionType.VOLUME_MUTE,
                    displayReply = "AUDIO GAIN // System audio muted (0%).",
                    spokenReply = "जी सर, फोन का साउंड म्यूट कर दिया गया है।"
                )
                lower.contains("full") || text.contains("फुल") -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = 100,
                    displayReply = "AUDIO GAIN // Master volume set to 100% maximum.",
                    spokenReply = "जी सर, साउंड फुल सौ प्रतिशत कर दिया गया है।"
                )
                extractedNum in 0..100 -> ParsedCommand(
                    action = DeviceActionType.VOLUME_SET,
                    numericValue = extractedNum,
                    displayReply = "AUDIO GAIN // Master volume set to $extractedNum%.",
                    spokenReply = "जी सर, साउंड $extractedNum प्रतिशत कर दिया गया है।"
                )
                isDown -> ParsedCommand(
                    action = DeviceActionType.VOLUME_DOWN,
                    displayReply = "AUDIO GAIN // Decreasing master volume by 20%.",
                    spokenReply = "जी सर, फोन की आवाज़ कम कर दी गई है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.VOLUME_UP,
                    displayReply = "AUDIO GAIN // Increasing master volume by 20%.",
                    spokenReply = "जी सर, फोन का साउंड बढ़ा दिया गया है।"
                )
            }
        }

        // 5. MUSIC / MEDIA
        val hasMusicWord = lower.contains("music") || lower.contains("song") || lower.contains("gana") ||
            lower.contains("gaana") || text.contains("म्यूजिक") || text.contains("गाना") ||
            text.contains("सॉन्ग") || lower.contains("next track") || lower.contains("play music") ||
            lower.contains("pause music")
        if (hasMusicWord) {
            val isStop = lower.contains("stop") || lower.contains("pause") || lower.contains("band") ||
                lower.contains("roko") || text.contains("बंद") || text.contains("रोको") || text.contains("पॉज")
            val isNext = lower.contains("next") || lower.contains("agla") || text.contains("अगला") || text.contains("नेक्स्ट")
            val isPrev = lower.contains("prev") || lower.contains("pichla") || text.contains("पिछला")
            return when {
                isNext -> ParsedCommand(
                    action = DeviceActionType.MEDIA_NEXT,
                    displayReply = "MEDIA ENGINE // Skipping to next track.",
                    spokenReply = "जी सर, अगला गाना चलाया जा रहा है।"
                )
                isPrev -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PREV,
                    displayReply = "MEDIA ENGINE // Returning to previous track.",
                    spokenReply = "जी सर, पिछला गाना चलाया जा रहा है।"
                )
                isStop -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PAUSE,
                    displayReply = "MEDIA ENGINE // Playback paused.",
                    spokenReply = "जी सर, म्यूजिक रोक दिया गया है।"
                )
                else -> ParsedCommand(
                    action = DeviceActionType.MEDIA_PLAY,
                    displayReply = "MEDIA ENGINE // Engaging audio playback.",
                    spokenReply = "जी सर, म्यूजिक चालू किया जा रहा है।"
                )
            }
        }

        // 6. SMS / MESSAGE
        val hasSmsWord = lower.contains("message") || lower.contains("sms") || lower.contains("msg") ||
            text.contains("मैसेज") || text.contains("संदेश") || text.contains("एसएमएस")
        if (hasSmsWord) {
            val targetName = extractTargetNameForCallOrSms(text)
            val msgContent = extractMessageBody(text)
            return ParsedCommand(
                action = DeviceActionType.SMS,
                target = targetName,
                message = msgContent,
                displayReply = "COMMS SMS // Dispatching message to $targetName: \"$msgContent\"",
                spokenReply = "जी सर, $targetName को मैसेज भेजा जा रहा है।"
            )
        }

        // 7. PHONE CALL
        val hasCallWord = lower.contains("call") || lower.contains("phone laga") ||
            lower.contains("dial") || text.contains("कॉल") || text.contains("फ़ोन लगा") ||
            text.contains("फोन लगा") || text.contains("डायल")
        if (hasCallWord) {
            val targetName = extractTargetNameForCallOrSms(text)
            return ParsedCommand(
                action = DeviceActionType.CALL,
                target = targetName,
                displayReply = "COMMS VOICE // Initiating outgoing call to $targetName...",
                spokenReply = "जी सर, $targetName को कॉल लगाया जा रहा है।"
            )
        }

        // 8. BATTERY
        if (lower.contains("battery") || text.contains("बैटरी") || text.contains("चार्ज")) {
            return ParsedCommand(
                action = DeviceActionType.BATTERY_STATUS,
                displayReply = "",
                spokenReply = ""
            )
        }

        // 9. ALARM
        if (lower.contains("alarm") || text.contains("अलार्म")) {
            val hour = if (extractedNum in 1..23) extractedNum else 7
            return ParsedCommand(
                action = DeviceActionType.SET_ALARM,
                numericValue = hour,
                target = "SUA Tactical Alarm",
                displayReply = "CHRONO ALARM // Setting system alarm for $hour:00.",
                spokenReply = "जी सर, $hour बजे का अलार्म सेट किया जा रहा है।"
            )
        }

        // 10. TIME / DATE
        if (lower.contains("time") || lower.contains("samay") || lower.contains("date") ||
            text.contains("समय") || text.contains("टाइम") || text.contains("तारीख") || text.contains("कितने बजे")
        ) {
            return ParsedCommand(
                action = DeviceActionType.TIME_DATE,
                displayReply = "",
                spokenReply = ""
            )
        }

        // 11. OPEN SETTINGS
        if (lower.contains("open setting") || text.contains("सेटिंग खोल") || text.contains("सेटिंग्स खोल")) {
            return ParsedCommand(
                action = DeviceActionType.OPEN_SETTINGS,
                displayReply = "OS CONSOLE // Opening Android System Settings.",
                spokenReply = "जी सर, फोन की सेटिंग्स खोली जा रही हैं।"
            )
        }

        return null
    }

    private fun extractTargetNameForCallOrSms(text: String): String {
        val lower = text.lowercase()
        if (lower.contains("khushi") || text.contains("खुशी") || text.contains("ख़ुशी")) {
            return "Khushi"
        }
        if (lower.contains("mom") || lower.contains("mummy") || text.contains("मम्मी") || text.contains("माँ")) {
            return "Mom"
        }
        if (lower.contains("home") || lower.contains("ghar") || text.contains("घर")) {
            return "Home"
        }

        val phoneMatch = Regex("\\+?\\d[\\d\\s-]{5,13}\\d").find(text)
        if (phoneMatch != null) {
            return phoneMatch.value.replace(Regex("[^0-9+]"), "")
        }

        val koIndex = text.indexOf(" को ")
        if (koIndex > 0) {
            val beforeKo = text.substring(0, koIndex).trim()
                .removePrefix("मेरा")
                .removePrefix("मेरी")
                .removePrefix("सुआ")
                .removePrefix("sua")
                .trim()
            if (beforeKo.isNotEmpty()) return beforeKo
        }

        val hinglishKo =
            Regex("(?i)(?:mera\\s+|meri\\s+|sua\\s+)?([a-zA-Z0-9_]+)\\s+ko\\s+(?:call|phone|message|sms|msg)").find(text)
        if (hinglishKo != null) {
            return hinglishKo.groupValues[1].trim()
        }

        val englishCall =
            Regex("(?i)(?:call|dial|message|sms|text)\\s+(?:to\\s+)?([a-zA-Z0-9_]+)").find(text)
        if (englishCall != null) {
            return englishCall.groupValues[1].trim()
        }

        return "Khushi"
    }

    private fun extractMessageBody(text: String): String {
        val kiIndex = text.indexOf(" कि ")
        if (kiIndex >= 0 && kiIndex + 4 < text.length) {
            return text.substring(kiIndex + 4).trim()
        }
        val hinglishKi = Regex("(?i)\\bki\\s+(.+)$").find(text)
        if (hinglishKi != null) {
            return hinglishKi.groupValues[1].trim()
        }
        val englishThat = Regex("(?i)(?:message|sms|saying)\\s+(?:to\\s+\\S+\\s+)?(.+)$").find(text)
        if (englishThat != null) {
            return englishThat.groupValues[1].trim()
        }
        return "Hello, I am on my way right now."
    }

    private fun callGeminiApi(userCommand: String, apiKey: String): ParsedCommand? {
        return try {
            val systemInstruction = """
                You are SUA (Smart Universal Assistant), an Iron Man style tactical AI assistant for Android.
                IMPORTANT RULES:
                1. Your name is strictly SUA, NEVER Jarvis.
                2. Understand Hindi, Hinglish, and English voice commands.
                3. Return ONLY a valid JSON object with this exact schema:
                {
                  "action": "CALL" | "SMS" | "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "LOCK_PHONE" | "BRIGHTNESS_UP" | "BRIGHTNESS_DOWN" | "BRIGHTNESS_SET" | "VOLUME_UP" | "VOLUME_DOWN" | "VOLUME_SET" | "VOLUME_MUTE" | "MEDIA_PLAY" | "MEDIA_PAUSE" | "MEDIA_NEXT" | "MEDIA_PREV" | "BATTERY_STATUS" | "SET_ALARM" | "OPEN_SETTINGS" | "TIME_DATE" | "AI_CHAT",
                  "target": "contact name in English or phone number if CALL/SMS, or alarm label, otherwise empty string",
                  "message": "SMS text content in English/Hinglish if action is SMS, otherwise empty string",
                  "numericValue": integer (0-100 for brightness/volume, 0-23 for alarm hour, or -1 if not applicable),
                  "displayReply": "Concise, futuristic Iron Man HUD response strictly in English (NO Devanagari/Hindi script here). Example: 'SUA CORE // Initiating call to Khushi.'",
                  "spokenReply": "Natural, polite spoken response in Hindi (Devanagari) as SUA so TextToSpeech speaks in fluent Hindi. Example: 'जी सर, खुशी को कॉल लगाया जा रहा है।'"
                }
            """.trimIndent()

            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", userCommand)))
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.2)
                        put("responseMimeType", "application/json")
                    }
                )
            }

            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val root = JSONObject(bodyStr)
                val textOut = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "") ?: return null

                val cleanJson = textOut
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val parsedObj = JSONObject(cleanJson)
                val actionStr = parsedObj.optString("action", "AI_CHAT").uppercase()
                val actionType = try {
                    DeviceActionType.valueOf(actionStr)
                } catch (_: Exception) {
                    DeviceActionType.AI_CHAT
                }

                val displayEn = parsedObj.optString("displayReply", "")
                    .ifBlank { "SUA CORE // Command executed." }
                val spokenHi = parsedObj.optString("spokenReply", "")
                    .ifBlank { "जी सर, आपका कमांड पूरा कर दिया गया है।" }

                ParsedCommand(
                    action = actionType,
                    target = parsedObj.optString("target", ""),
                    message = parsedObj.optString("message", ""),
                    numericValue = parsedObj.optInt("numericValue", -1),
                    displayReply = displayEn,
                    spokenReply = spokenHi
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
