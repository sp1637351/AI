package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.data.SuaStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object SuaVoiceSpeaker : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speakerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSpeechJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(7, TimeUnit.SECONDS)
        .build()

    private var currentOnComplete: (() -> Unit)? = null

    @Volatile
    var isSpeakingNow: Boolean = false
        private set

    @Volatile
    var lastSpeechEndTimeMs: Long = 0L
        private set

    @Volatile
    var lastSpokenText: String = ""
        private set

    private val recentSpokenHistory = ArrayDeque<String>(6)

    private val selfEchoPhrases = listOf(
        "माफ़ कीजिए",
        "maph kijie",
        "maaf kijiye",
        "gemini sadasy",
        "जेमिनी एआई",
        "जी बॉस",
        "ji boss",
        "मोको ऑनलाइन",
        "moko online",
        "स्लीप मोड में हूँ",
        "एपीआई की मान्य नहीं",
        "developed by mr. shashank",
        "किया जा रहा है",
        "कर दिया गया है",
        "चलाया जा रहा है",
        "लगाया जा रहा है",
        "खोली जा रही हैं",
        "भेजा जा रहा है",
        "ऑनलाइन हो गई हूँ",
        "लगातार बात कर सकते हैं",
        "स्टैंडबाय में चला जाएगा",
        "मैं आपकी प्यारी"
    )

    fun isInPostSpeechCooldown(cooldownMs: Long = 1400L): Boolean {
        if (isSpeakingNow) return true
        val elapsed = System.currentTimeMillis() - lastSpeechEndTimeMs
        return lastSpeechEndTimeMs > 0L && elapsed in 0..cooldownMs
    }

    private fun extractComparableTokens(text: String): Set<String> {
        val roman = com.example.util.HindiTransliterator.toRoman(text)
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
        return roman.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()
    }

    /**
     * Prevents the microphone from picking up Moko's own speaker output while allowing
     * genuine user mid-sentence interruptions (e.g., "नहीं नहीं प्राइम मिनिस्टर कौन है").
     */
    fun isLikelySelfEcho(recognizedText: String): Boolean {
        val clean = recognizedText.trim().lowercase()
        if (clean.isEmpty()) return false

        // 1. If the user starts with a natural barge-in / correction phrase ("नहीं नहीं...", "अरे...", "साउंड कम करो", "स्टॉप"),
        // it is NEVER self-echo — let the user interrupt Moko immediately!
        if (com.example.util.HindiTransliterator.isUserBargeInPhrase(clean)) {
            return false
        }

        // 2. Check assistant-only signature phrases that only Moko says
        for (phrase in selfEchoPhrases) {
            if (clean.contains(phrase)) return true
        }

        // 3. Compare word & phonetic overlap against Moko's recent spoken sentences
        val recTokens = extractComparableTokens(clean)
        val candidates = synchronized(recentSpokenHistory) {
            (listOf(lastSpokenText) + recentSpokenHistory).filter { it.isNotBlank() }.distinct()
        }

        for (spoken in candidates) {
            val spokenLower = spoken.trim().lowercase()
            if (spokenLower.length >= 5 && (spokenLower.contains(clean) || clean.contains(spokenLower))) {
                return true
            }
            val spokenTokens = extractComparableTokens(spokenLower)
            if (recTokens.isNotEmpty() && spokenTokens.isNotEmpty()) {
                val matchedCount = recTokens.count { rt ->
                    spokenTokens.any { st ->
                        st == rt ||
                            (rt.length >= 4 && st.length >= 4 &&
                                com.example.util.HindiTransliterator.levenshteinDistance(rt, st) <= 1)
                    }
                }
                val overlapRatio = matchedCount.toFloat() / recTokens.size.toFloat()
                // If >= 45% of spoken words match what Moko just said (or >= 2 words while actively speaking), it's speaker echo!
                if (overlapRatio >= 0.45f || (isInPostSpeechCooldown(2400L) && matchedCount >= 2)) {
                    return true
                }
            }
        }

        // 4. While speaker is actively playing, ignore very short 1-word ambient noise that isn't a barge-in command
        if (isSpeakingNow && recTokens.size <= 1) {
            return true
        }

        return false
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        if (tts == null) {
            try {
                tts = TextToSpeech(appContext, this)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ctx = appContext
            val prefs = if (ctx != null) SuaPreferences(ctx) else null
            val localeTag = prefs?.getVoiceLocale() ?: "hi-IN"
            applyLocale(localeTag)

            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.setSpeechRate(prefs?.getVoiceSpeed() ?: 1.0f)
            tts?.setPitch(prefs?.getVoicePitch() ?: 1.12f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeakingNow = true
                    SuaStateRepository.updateStatus(SuaStatus.SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechFinishedInternal()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechFinishedInternal()
                }
            })

            isInitialized = true
        }
    }

    private fun onSpeechFinishedInternal() {
        isSpeakingNow = false
        lastSpeechEndTimeMs = System.currentTimeMillis()
        mainHandler.postDelayed({
            releaseAudioFocus()
            val cb = currentOnComplete
            currentOnComplete = null
            if (cb != null) {
                cb.invoke()
            } else {
                SuaStateRepository.updateStatus(
                    if (SuaStateRepository.isBackgroundServiceRunning.value) {
                        SuaStatus.LISTENING
                    } else {
                        SuaStatus.IDLE
                    }
                )
            }
        }, 900L)
    }

    fun applyLocale(localeTag: String) {
        val targetLocale = Locale.forLanguageTag(localeTag)
        val result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val hiRes = tts?.setLanguage(Locale.forLanguageTag("hi-IN"))
            if (hiRes == TextToSpeech.LANG_MISSING_DATA || hiRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.forLanguageTag("en-IN"))
            }
        }
    }

    /**
     * Speaks text using Kalpa Labs Neural Voice API (`https://api.kalpalabs.ai/v1/tts/{voice_id}`)
     * with the user's Kalpa API key, and falls back to local TTS only if offline.
     */
    fun speak(textToSpeak: String, onComplete: (() -> Unit)? = null) {
        val cleanText = textToSpeak.trim()
        if (cleanText.isEmpty()) {
            onComplete?.invoke()
            return
        }

        lastSpokenText = cleanText
        synchronized(recentSpokenHistory) {
            if (recentSpokenHistory.size >= 6) {
                recentSpokenHistory.removeFirst()
            }
            recentSpokenHistory.addLast(cleanText)
        }
        val ctx = appContext
        if (ctx == null) {
            onComplete?.invoke()
            return
        }

        stopCurrentPlaybackOnly()
        isSpeakingNow = true
        currentOnComplete = onComplete
        SuaStateRepository.updateStatus(SuaStatus.SPEAKING)

        val prefs = SuaPreferences(ctx)
        val kalpaKey = prefs.getKalpaApiKey()
        val voiceId = prefs.getKalpaVoiceId()

        activeSpeechJob?.cancel()
        activeSpeechJob = speakerScope.launch {
            val wavFile = if (kalpaKey.isNotBlank()) {
                fetchKalpaNeuralVoiceWav(ctx, cleanText, kalpaKey, voiceId, prefs)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                if (wavFile != null && wavFile.exists() && wavFile.length() > 128) {
                    playKalpaWavFile(ctx, wavFile)
                } else {
                    speakWithLocalTtsFallback(ctx, cleanText, prefs)
                }
            }
        }
    }

    private fun fetchKalpaNeuralVoiceWav(
        context: Context,
        text: String,
        apiKey: String,
        voiceId: String,
        prefs: SuaPreferences
    ): File? {
        // 1. Try primary Kalpa Labs endpoint: POST https://api.kalpalabs.ai/v1/tts/{voice_id}
        val primaryBytes = executeKalpaTtsRequest(
            url = "https://api.kalpalabs.ai/v1/tts/$voiceId",
            apiKey = apiKey,
            payload = JSONObject().apply {
                put("text", text)
                put("model", "kalpa-tts-1")
                put(
                    "params",
                    JSONObject().apply {
                        put("temperature", 0.85)
                        put("audio_quality", "high")
                    }
                )
            }
        )
        if (primaryBytes != null && primaryBytes.size > 128) {
            return writeTempWavFile(context, primaryBytes)
        }

        // 2. Discover valid voice ID from GET https://api.kalpalabs.ai/v1/voices if default voice ID changed
        val discoveredVoiceId = discoverKalpaVoiceId(apiKey)
        if (!discoveredVoiceId.isNullOrBlank() && discoveredVoiceId != voiceId) {
            prefs.setKalpaVoiceId(discoveredVoiceId)
            val retryBytes = executeKalpaTtsRequest(
                url = "https://api.kalpalabs.ai/v1/tts/$discoveredVoiceId",
                apiKey = apiKey,
                payload = JSONObject().apply {
                    put("text", text)
                    put("model", "kalpa-tts-1")
                }
            )
            if (retryBytes != null && retryBytes.size > 128) {
                return writeTempWavFile(context, retryBytes)
            }
        }

        // 3. Try base endpoint POST https://api.kalpalabs.ai/v1/tts with speaker "0"
        val baseBytes = executeKalpaTtsRequest(
            url = "https://api.kalpalabs.ai/v1/tts",
            apiKey = apiKey,
            payload = JSONObject().apply {
                put("text", text)
                put("speaker", "0")
                put("model", "kalpa-tts-1")
            }
        )
        if (baseBytes != null && baseBytes.size > 128) {
            return writeTempWavFile(context, baseBytes)
        }

        return null
    }

    private fun executeKalpaTtsRequest(
        url: String,
        apiKey: String,
        payload: JSONObject
    ): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val contentType = resp.header("Content-Type", "").orEmpty().lowercase()
                if (contentType.contains("audio/") || contentType.contains("octet-stream")) {
                    return resp.body?.bytes()
                }
                val bodyStr = resp.body?.string().orEmpty()
                if (bodyStr.isBlank()) return null

                val root = JSONObject(bodyStr)
                val b64 = root.optJSONObject("audio")?.optString("data_b64", "")
                    ?.takeIf { it.isNotEmpty() }
                    ?: root.optString("data_b64", "")
                        .takeIf { it.isNotEmpty() }
                    ?: root.optString("audio_base64", "")
                        .takeIf { it.isNotEmpty() }
                    ?: ""

                if (b64.isNotEmpty()) {
                    Base64.decode(b64, Base64.DEFAULT)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun discoverKalpaVoiceId(apiKey: String): String? {
        return try {
            val req = Request.Builder()
                .url("https://api.kalpalabs.ai/v1/voices")
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("data") ?: return null
                var firstId: String? = null
                for (i in 0 until arr.length()) {
                    val v = arr.getJSONObject(i)
                    val id = v.optString("id", "")
                    val gender = v.optString("gender", "").lowercase()
                    if (id.isNotEmpty() && firstId == null) {
                        firstId = id
                    }
                    if (id.isNotEmpty() && (gender.contains("fem") || v.optString("name").contains("Kiara", ignoreCase = true))) {
                        return id
                    }
                }
                firstId
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeTempWavFile(context: Context, audioBytes: ByteArray): File? {
        return try {
            val outFile = File(context.cacheDir, "kalpa_moko_tts.wav")
            outFile.writeBytes(audioBytes)
            outFile
        } catch (_: Exception) {
            null
        }
    }

    private fun playKalpaWavFile(context: Context, wavFile: File) {
        try {
            ensureAudibleVolume(context)
            requestAudioFocus(context)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(wavFile.absolutePath)
                setOnCompletionListener { mp ->
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                    onSpeechFinishedInternal()
                }
                setOnErrorListener { mp, _, _ ->
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                    onSpeechFinishedInternal()
                    true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            speakWithLocalTtsFallback(context, lastSpokenText, SuaPreferences(context))
        }
    }

    private fun speakWithLocalTtsFallback(
        ctx: Context,
        cleanText: String,
        prefs: SuaPreferences
    ) {
        if (!isInitialized || tts == null) {
            init(ctx)
            mainHandler.postDelayed({
                onSpeechFinishedInternal()
            }, 800L)
            return
        }

        try {
            // Choose English TTS locale if the reply is in English, else Hindi
            val hasDevanagari = cleanText.any { it.code in 0x0900..0x097F }
            applyLocale(if (hasDevanagari) "hi-IN" else "en-IN")
            tts?.setPitch(prefs.getVoicePitch())
            tts?.setSpeechRate(prefs.getVoiceSpeed())
            ensureAudibleVolume(ctx)
            requestAudioFocus(ctx)

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val utteranceId = "moko_voice_${System.currentTimeMillis()}"
            val res = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (res == TextToSpeech.ERROR) {
                onSpeechFinishedInternal()
            }
        } catch (_: Exception) {
            onSpeechFinishedInternal()
        }
    }

    private fun stopCurrentPlaybackOnly() {
        try {
            activeSpeechJob?.cancel()
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
            mediaPlayer = null
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun stopSpeaking() {
        try {
            stopCurrentPlaybackOnly()
            isSpeakingNow = false
            lastSpeechEndTimeMs = System.currentTimeMillis()
            currentOnComplete = null
            releaseAudioFocus()
        } catch (_: Exception) {
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun ensureAudibleVolume(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            if (cur == 0) {
                val safeVol = (max * 0.45f).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, safeVol, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun requestAudioFocus(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseAudioFocus() {
        try {
            val ctx = appContext ?: return
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }
}
