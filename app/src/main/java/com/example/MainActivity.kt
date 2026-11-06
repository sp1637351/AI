package com.example

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
import com.example.service.SuaVoiceSpeaker
import com.example.ui.SettingsScreen
import com.example.ui.SuaDashboardScreen
import com.example.ui.SuaViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SuaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SuaVoiceSpeaker.init(this)
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
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionsAndHardware()
    }
}
