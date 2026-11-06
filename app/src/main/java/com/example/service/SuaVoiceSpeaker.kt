package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.data.SuaStatus
import java.util.Locale

object SuaVoiceSpeaker : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingSpeechText: String? = null
    private var pendingOnComplete: (() -> Unit)? = null
    private var currentOnComplete: (() -> Unit)? = null

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
            tts?.setSpeechRate(1.04f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    SuaStateRepository.updateStatus(SuaStatus.SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
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
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        releaseAudioFocus()
                        val cb = currentOnComplete
                        currentOnComplete = null
                        cb?.invoke()
                    }
                }
            })

            isInitialized = true

            // Speak any queued utterance immediately
            val queued = pendingSpeechText
            val queuedCb = pendingOnComplete
            pendingSpeechText = null
            pendingOnComplete = null
            if (!queued.isNullOrBlank()) {
                speak(queued, queuedCb)
            }
        }
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

    fun speak(textToSpeak: String, onComplete: (() -> Unit)? = null) {
        val cleanText = textToSpeak.trim()
        if (cleanText.isEmpty()) {
            onComplete?.invoke()
            return
        }

        val ctx = appContext
        if (!isInitialized || tts == null) {
            pendingSpeechText = cleanText
            pendingOnComplete = onComplete
            if (ctx != null && tts == null) {
                init(ctx)
            } else {
                mainHandler.postDelayed({
                    onComplete?.invoke()
                }, 800L)
            }
            return
        }

        mainHandler.post {
            try {
                if (ctx != null) {
                    val prefs = SuaPreferences(ctx)
                    applyLocale(prefs.getVoiceLocale())
                    ensureAudibleVolume(ctx)
                    requestAudioFocus(ctx)
                }

                currentOnComplete = onComplete
                SuaStateRepository.updateStatus(SuaStatus.SPEAKING)

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                val utteranceId = "sua_voice_${System.currentTimeMillis()}"
                val res = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (res == TextToSpeech.ERROR) {
                    currentOnComplete = null
                    onComplete?.invoke()
                }
            } catch (_: Exception) {
                currentOnComplete = null
                onComplete?.invoke()
            }
        }
    }

    fun stopSpeaking() {
        try {
            currentOnComplete = null
            pendingSpeechText = null
            tts?.stop()
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
