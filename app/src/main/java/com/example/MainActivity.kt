package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SuaStateRepository
import com.example.service.ScreenCaptureManager
import com.example.service.SuaVoiceSpeaker
import com.example.ui.SettingsScreen
import com.example.ui.SuaDashboardScreen
import com.example.ui.SuaViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SuaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenCaptureManager.attachActivity(this)
        SuaVoiceSpeaker.init(this)
        handleUnlockIntentIfNeeded(intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val brightnessPercent by viewModel.brightnessPercent.collectAsStateWithLifecycle()

                LaunchedEffect(brightnessPercent) {
                    val lp = window.attributes
                    lp.screenBrightness = (brightnessPercent / 100f).coerceIn(0.05f, 1.0f)
                    window.attributes = lp
                }

                var showSettings by rememberSaveable { mutableStateOf(false) }

                Crossfade(targetState = showSettings, label = "sua_navigation") { isSettings ->
                    if (isSettings) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { showSettings = false }
                        )
                    } else {
                        SuaDashboardScreen(
                            viewModel = viewModel,
                            onOpenSettings = {
                                viewModel.refreshPermissionsAndHardware()
                                showSettings = true
                            },
                            onRequestBiometricKeyguardDismiss = {
                                requestSystemKeyguardDismiss()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUnlockIntentIfNeeded(intent)
    }

    private fun handleUnlockIntentIfNeeded(intent: Intent?) {
        if (intent?.getBooleanExtra("request_biometric_unlock", false) == true) {
            intent.removeExtra("request_biometric_unlock")
            requestSystemKeyguardDismiss()
        }
    }

    fun requestSystemKeyguardDismiss() {
        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km.isKeyguardLocked) {
                km.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            SuaStateRepository.setScreenLockedOverlay(false)
                            SuaStateRepository.updateLastReply("SECURITY // Biometric/PIN verified — Device keyguard unlocked.")
                            SuaVoiceSpeaker.speak("बायोमेट्रिक या पिन वेरीफाई हो गया है, स्क्रीन अनलॉक कर दी गई है।")
                        }

                        override fun onDismissCancelled() {
                            SuaStateRepository.updateLastReply("SECURITY // Biometric/PIN unlock cancelled by user.")
                        }

                        override fun onDismissError() {
                            SuaStateRepository.updateLastReply("SECURITY // Could not dismiss OS keyguard. Please unlock with your fingerprint or PIN.")
                        }
                    }
                )
            } else {
                SuaStateRepository.setScreenLockedOverlay(false)
            }
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        ScreenCaptureManager.attachActivity(this)
        SuaStateRepository.setAppInForeground(true)
        viewModel.refreshPermissionsAndHardware()
    }

    override fun onPause() {
        SuaStateRepository.setAppInForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        ScreenCaptureManager.detachActivity(this)
        super.onDestroy()
    }
}
