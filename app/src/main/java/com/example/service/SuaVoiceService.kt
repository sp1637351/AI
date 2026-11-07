package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.SuaBrain
import com.example.control.SuaDeviceController
import com.example.data.SuaPreferences
import com.example.data.SuaStateRepository
import com.example.data.SuaStatus
import com.example.util.HindiTransliterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SuaVoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "moko_background_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_LISTENING = "com.example.service.ACTION_START_LISTENING"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"
        const val ACTION_EXECUTE_COMMAND = "com.example.service.ACTION_EXECUTE_COMMAND"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"

        fun startService(context: Context) {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMic) {
                SuaStateRepository.setBackgroundServiceRunning(false)
                SuaStateRepository.updateStatus(SuaStatus.ERROR)
                SuaStateRepository.updateLiveTranscript("MIC PERMISSION REQUIRED // Grant Microphone access")
                SuaStateRepository.updateLastReply("PERMISSION ERROR // Microphone permission is required before switching Moko to ONLINE mode.")
                SuaVoiceSpeaker.speak("माफ़ कीजिए बॉस, माइक परमिशन के बिना मोको ऑनलाइन मोड चालू नहीं हो सकता।")
                return
            }

            val intent = Intent(context, SuaVoiceService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SuaVoiceService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {
            }
            SuaVoiceSpeaker.stopSpeaking()
            SuaStateRepository.setBackgroundServiceRunning(false)
            SuaStateRepository.updateStatus(SuaStatus.IDLE)
        }

        fun sendCommandToService(context: Context, commandText: String) {
            val intent = Intent(context, SuaVoiceService::class.java).apply {
                action = ACTION_EXECUTE_COMMAND
                putExtra(EXTRA_COMMAND_TEXT, commandText)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var prefs: SuaPreferences
    private lateinit var brain: SuaBrain
    private lateinit var controller: SuaDeviceController

    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shouldKeepListening = false
    private var isCurrentlyListening = false

    // Keeps Moko's voice recognizer alive when the user locks the phone screen in ONLINE mode
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!shouldKeepListening) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON -> {
                    acquireWakeLock()
                    if (!isCurrentlyListening) {
                        scheduleNextListen(500L)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = SuaPreferences(this)
        brain = SuaBrain(prefs)
        controller = SuaDeviceController(this)
        SuaVoiceSpeaker.init(this)
        createNotificationChannel()
        initSpeechRecognizer()

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                // SLEEP MODE: Completely stop background listening & release WakeLock
                shouldKeepListening = false
                releaseWakeLock()
                stopListeningInternal()
                SuaVoiceSpeaker.stopSpeaking()
                SuaStateRepository.setBackgroundServiceRunning(false)
                SuaStateRepository.updateStatus(SuaStatus.IDLE)
                SuaStateRepository.updateLiveTranscript("SLEEP MODE // Background listening is OFF")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_EXECUTE_COMMAND -> {
                val cmdText = intent.getStringExtra(EXTRA_COMMAND_TEXT).orEmpty().trim()
                if (cmdText.isNotEmpty()) {
                    processRecognizedCommand(cmdText, fromMic = false)
                }
            }

            else -> {
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasMicPerm) {
                    shouldKeepListening = false
                    SuaStateRepository.setBackgroundServiceRunning(false)
                    SuaStateRepository.updateStatus(SuaStatus.ERROR)
                    SuaStateRepository.updateLastReply("MIC PERMISSION REQUIRED // Grant Microphone permission in Side Menu -> Settings.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // ONLINE MODE: Active in background & while phone screen is locked
                shouldKeepListening = true
                acquireWakeLock()
                SuaStateRepository.setBackgroundServiceRunning(true)
                SuaStateRepository.markWakeWordDetected()
                val botName = prefs.getAssistantName()
                val userName = prefs.getUserName()
                val wakeNote = if (prefs.isWakeWordRequiredInBackground()) {
                    "Say 'Ok $botName' or 'Hi $botName' anytime (Low-Battery Wake Engine)"
                } else {
                    "Continuous listening active"
                }
                SuaStateRepository.updateLiveTranscript("ONLINE MODE // $wakeNote")
                SuaStateRepository.updateLastReply("$botName ONLINE // Active in background & lock screen. $wakeNote.")
                startForegroundWithNotification("ONLINE // $wakeNote")

                SuaVoiceSpeaker.speak("नमस्ते $userName! मैं $botName ऑनलाइन हो गई हूँ। फोन लॉक होने पर भी आप ओके $botName या हाय $botName बोलकर कोई भी काम करवा सकते हैं।") {
                    if (shouldKeepListening) {
                        SuaStateRepository.markWakeWordDetected()
                        scheduleNextListen(300L)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Moko:OnlineVoiceWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Hold wake lock for up to 12 hours per Online session so screen-lock doesn't suspend CPU
                wakeLock?.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            shouldKeepListening = false
            SuaStateRepository.setBackgroundServiceRunning(false)
            stopSelf()
        }
    }

    private fun updateNotification(contentText: String) {
        if (!shouldKeepListening) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(contentText))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SuaVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val botName = prefs.getAssistantName()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("$botName ONLINE // Say 'Ok $botName'")
            .setContentText(contentText)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_lock_power_off,
                "SLEEP MODE",
                pendingStop
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(recognitionListener)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private var lastRmsUpdateMs = 0L
    private var lastPartialUpdateMs = 0L

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isCurrentlyListening = true
            SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            val botName = prefs.getAssistantName()
            val wakeActive = !prefs.isWakeWordRequiredInBackground() ||
                SuaStateRepository.isWithinWakeConversationWindow()
            SuaStateRepository.updateLiveTranscript(
                if (wakeActive) "$botName LISTENING // Speak your command now..."
                else "ONLINE // Say 'Ok $botName' or 'Hi $botName'..."
            )
        }

        override fun onBeginningOfSpeech() {
            SuaStateRepository.updateStatus(SuaStatus.LISTENING)
        }

        override fun onRmsChanged(rmsdB: Float) {
            val now = System.currentTimeMillis()
            if (now - lastRmsUpdateMs >= 260L) {
                lastRmsUpdateMs = now
                SuaStateRepository.updateAudioRms(rmsdB)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isCurrentlyListening = false
            SuaStateRepository.updateAudioRms(0f)
        }

        override fun onError(error: Int) {
            isCurrentlyListening = false
            SuaStateRepository.updateAudioRms(0f)
            if (shouldKeepListening && prefs.isContinuousBackgroundEnabled()) {
                acquireWakeLock()
                val lowBatteryDelay = if (prefs.isLowBatteryWakeMode()) 1650L else 1100L
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT
                ) {
                    initSpeechRecognizer()
                    scheduleNextListen(lowBatteryDelay + 300L)
                } else {
                    scheduleNextListen(lowBatteryDelay)
                }
            } else {
                SuaStateRepository.updateStatus(SuaStatus.IDLE)
            }
        }

        override fun onResults(results: Bundle?) {
            isCurrentlyListening = false
            SuaStateRepository.updateAudioRms(0f)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotEmpty()) {
                processRecognizedCommand(spokenText, fromMic = true)
            } else if (shouldKeepListening && prefs.isContinuousBackgroundEnabled()) {
                val nextDelay = if (prefs.isLowBatteryWakeMode()) 1350L else 950L
                scheduleNextListen(nextDelay)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val now = System.currentTimeMillis()
            if (now - lastPartialUpdateMs < 300L) return
            lastPartialUpdateMs = now
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotEmpty() && !SuaVoiceSpeaker.isLikelySelfEcho(partial)) {
                SuaStateRepository.updateLiveTranscript(brain.sanitizeForHud(partial))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListeningInternal() {
        mainHandler.post {
            if (!shouldKeepListening) return@post

            // Never open the microphone while TTS speaker is still playing or cooling down!
            if (SuaVoiceSpeaker.isSpeakingNow || SuaVoiceSpeaker.isInPostSpeechCooldown(800L)) {
                scheduleNextListen(950L)
                return@post
            }

            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                SuaStateRepository.updateLiveTranscript("MIC PERMISSION REQUIRED // Open Side Menu -> Settings")
                return@post
            }

            acquireWakeLock()
            if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(this)) {
                initSpeechRecognizer()
            }

            try {
                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.getVoiceLocale())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && prefs.isLowBatteryWakeMode()) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    }
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1600L
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1200L
                    )
                }
                speechRecognizer?.startListening(recognizerIntent)
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            } catch (_: Exception) {
                initSpeechRecognizer()
                scheduleNextListen(1400L)
            }
        }
    }

    private fun stopListeningInternal() {
        mainHandler.post {
            isCurrentlyListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            SuaStateRepository.updateAudioRms(0f)
        }
    }

    private fun scheduleNextListen(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                startListeningInternal()
            }
        }, delayMs)
    }

    private fun processRecognizedCommand(rawSpokenText: String, fromMic: Boolean) {
        stopListeningInternal()

        // 1. Acoustic Self-Echo Guard: never process TTS speaker output as a user command!
        if (fromMic && SuaVoiceSpeaker.isLikelySelfEcho(rawSpokenText)) {
            if (shouldKeepListening && prefs.isContinuousBackgroundEnabled()) {
                scheduleNextListen(950L)
            }
            return
        }

        val botName = prefs.getAssistantName()
        val userName = prefs.getUserName()
        val (hasWakeWord, strippedAfterWake) = HindiTransliterator.checkAndStripWakeWord(rawSpokenText, botName)
        val wakeRequired = fromMic && prefs.isWakeWordRequiredInBackground()
        val inActiveWindow = SuaStateRepository.isWithinWakeConversationWindow()

        if (wakeRequired && !hasWakeWord && !inActiveWindow) {
            val ignoredHud = brain.sanitizeForHud(rawSpokenText)
            SuaStateRepository.updateLiveTranscript("STANDBY // Ignored ambient: \"$ignoredHud\" (Say 'Ok $botName')")
            if (shouldKeepListening && prefs.isContinuousBackgroundEnabled()) {
                val standbyDelay = if (prefs.isLowBatteryWakeMode()) 1250L else 850L
                scheduleNextListen(standbyDelay)
            }
            return
        }

        if (hasWakeWord) {
            SuaStateRepository.markWakeWordDetected()
        }

        val effectiveCommand = if (hasWakeWord) strippedAfterWake else rawSpokenText.trim()

        // User said "Ok Moko" / "Hi Moko" with no command yet -> wake up & listen affectionately!
        if (effectiveCommand.isEmpty()) {
            SuaStateRepository.markWakeWordDetected()
            SuaStateRepository.updateLiveTranscript("$botName AWAKE // Yes $userName, speak your command...")
            SuaStateRepository.updateLastReply("WAKE-WORD DETECTED // Listening for $userName's command (22s active window)...")
            SuaVoiceSpeaker.speak("हाँ मेरे प्यारे $userName, बोलिए क्या काम करना है?") {
                SuaStateRepository.markWakeWordDetected()
                if (shouldKeepListening) {
                    scheduleNextListen(260L)
                }
            }
            return
        }

        val hudTranscript = brain.sanitizeForHud(effectiveCommand)
        SuaStateRepository.updateLiveTranscript(hudTranscript)
        SuaStateRepository.updateStatus(SuaStatus.PROCESSING)

        serviceScope.launch {
            val parsed = brain.interpretCommand(effectiveCommand)
            val execResult = controller.executeCommand(parsed)

            SuaStateRepository.markWakeWordDetected()

            SuaStateRepository.updateLastReply(execResult.displayReply)
            SuaStateRepository.addCommandLog(
                userCommand = hudTranscript,
                suaResponse = execResult.displayReply,
                actionType = parsed.action,
                actionSuccess = execResult.success,
                prefs = prefs
            )
            updateNotification(execResult.displayReply)

            SuaVoiceSpeaker.speak(execResult.finalSpokenReply) {
                if (shouldKeepListening && prefs.isContinuousBackgroundEnabled()) {
                    scheduleNextListen(450L)
                } else {
                    SuaStateRepository.updateStatus(
                        if (shouldKeepListening) SuaStatus.LISTENING else SuaStatus.IDLE
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        shouldKeepListening = false
        releaseWakeLock()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
        }
        SuaStateRepository.setBackgroundServiceRunning(false)
        SuaStateRepository.updateStatus(SuaStatus.IDLE)
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
