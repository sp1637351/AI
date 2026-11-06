package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SuaVoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "sua_background_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_LISTENING = "com.example.service.ACTION_START_LISTENING"
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_SERVICE"
        const val ACTION_EXECUTE_COMMAND = "com.example.service.ACTION_EXECUTE_COMMAND"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"

        fun startService(context: Context) {
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

    override fun onCreate() {
        super.onCreate()
        prefs = SuaPreferences(this)
        brain = SuaBrain(prefs)
        controller = SuaDeviceController(this)
        SuaVoiceSpeaker.init(this)
        createNotificationChannel()
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                shouldKeepListening = false
                releaseWakeLock()
                stopListeningInternal()
                SuaVoiceSpeaker.stopSpeaking()
                SuaStateRepository.setBackgroundServiceRunning(false)
                SuaStateRepository.updateStatus(SuaStatus.IDLE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_EXECUTE_COMMAND -> {
                val cmdText = intent.getStringExtra(EXTRA_COMMAND_TEXT).orEmpty().trim()
                if (cmdText.isNotEmpty()) {
                    processRecognizedCommand(cmdText)
                }
            }

            else -> {
                shouldKeepListening = true
                acquireWakeLock()
                SuaStateRepository.setBackgroundServiceRunning(true)
                SuaStateRepository.updateLiveTranscript("ARC CORE ACTIVE // LISTENING FOR VOICE...")
                SuaStateRepository.updateLastReply("SUA ONLINE // Background voice engine engaged. Speak your command in Hindi or English.")
                startForegroundWithNotification("ARC CORE ONLINE // Listening for voice commands in background...")

                SuaVoiceSpeaker.speak("नमस्ते सर, सुआ बैकग्राउंड में ऑनलाइन है। बताइए मैं क्या काम करूँ?") {
                    if (shouldKeepListening) {
                        scheduleNextListen(250L)
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
                    "SUA:TacticalVoiceWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L) // Up to 4 hours continuous background guard
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
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMicPerm) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SUA // TACTICAL VOICE CORE ACTIVE")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_media_pause,
                "DISENGAGE SUA",
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

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isCurrentlyListening = true
            SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            SuaStateRepository.updateLiveTranscript("LISTENING // SPEAK COMMAND NOW...")
        }

        override fun onBeginningOfSpeech() {
            SuaStateRepository.updateStatus(SuaStatus.LISTENING)
        }

        override fun onRmsChanged(rmsdB: Float) {
            SuaStateRepository.updateAudioRms(rmsdB)
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
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT
                ) {
                    initSpeechRecognizer()
                    scheduleNextListen(900L)
                } else {
                    scheduleNextListen(450L)
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
                processRecognizedCommand(spokenText)
            } else if (shouldKeepListening && prefs.isContinuousBackgroundEnabled()) {
                scheduleNextListen(350L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotEmpty()) {
                SuaStateRepository.updateLiveTranscript(brain.sanitizeForHud(partial))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListeningInternal() {
        mainHandler.post {
            if (!shouldKeepListening) return@post
            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                SuaStateRepository.updateLiveTranscript("MIC PERMISSION REQUIRED // OPEN SETTINGS -> GRANT ALL")
                return@post
            }

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
                }
                speechRecognizer?.startListening(recognizerIntent)
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
            } catch (_: Exception) {
                SuaStateRepository.updateStatus(SuaStatus.LISTENING)
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

    private fun processRecognizedCommand(commandText: String) {
        stopListeningInternal()
        val hudTranscript = brain.sanitizeForHud(commandText)
        SuaStateRepository.updateLiveTranscript(hudTranscript)
        SuaStateRepository.updateStatus(SuaStatus.PROCESSING)

        serviceScope.launch {
            val parsed = brain.interpretCommand(commandText)
            val execResult = controller.executeCommand(parsed)

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
                    scheduleNextListen(350L)
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
