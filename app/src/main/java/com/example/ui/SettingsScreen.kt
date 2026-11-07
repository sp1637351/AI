package com.example.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AiProviderType
import com.example.data.CommandLogEntry
import com.example.data.MokoMemoryItem
import com.example.data.PermissionStatusItem
import com.example.data.SuaPreferences
import com.example.data.VoicePreset
import com.example.ui.theme.SuaAmber
import com.example.ui.theme.SuaBorder
import com.example.ui.theme.SuaCrimson
import com.example.ui.theme.SuaCyan
import com.example.ui.theme.SuaDeepNavy
import com.example.ui.theme.SuaElectricBlue
import com.example.ui.theme.SuaEmerald
import com.example.ui.theme.SuaObsidian
import com.example.ui.theme.SuaSurfaceCard
import com.example.ui.theme.SuaTextPrimary
import com.example.ui.theme.SuaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SuaViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val assistantName by viewModel.assistantName.collectAsStateWithLifecycle()
    val savedApiKey by viewModel.savedApiKey.collectAsStateWithLifecycle()
    val hasEffectiveApiKey by viewModel.hasEffectiveApiKey.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val customApiUrl by viewModel.customApiUrl.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val kalpaApiKey by viewModel.kalpaApiKey.collectAsStateWithLifecycle()
    val kalpaVoiceId by viewModel.kalpaVoiceId.collectAsStateWithLifecycle()
    val permissionStatuses by viewModel.permissionStatuses.collectAsStateWithLifecycle()
    val wakeWordRequired by viewModel.wakeWordRequired.collectAsStateWithLifecycle()
    val lowBatteryWakeMode by viewModel.lowBatteryWakeMode.collectAsStateWithLifecycle()
    val voiceLocale by viewModel.voiceLocale.collectAsStateWithLifecycle()
    val voicePreset by viewModel.voicePreset.collectAsStateWithLifecycle()
    val voicePitch by viewModel.voicePitch.collectAsStateWithLifecycle()
    val voiceSpeed by viewModel.voiceSpeed.collectAsStateWithLifecycle()
    val customCommands by viewModel.customCommands.collectAsStateWithLifecycle()
    val quickContacts by viewModel.quickContacts.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val memoryItems by viewModel.memoryItems.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.settingsBannerMessage.collectAsStateWithLifecycle()

    var userNameInput by remember(userName) { mutableStateOf(userName) }
    var assistantNameInput by remember(assistantName) { mutableStateOf(assistantName) }

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var customUrlInput by remember(customApiUrl) { mutableStateOf(customApiUrl) }
    var showApiKeyPlain by remember { mutableStateOf(false) }

    var kalpaKeyInput by remember(kalpaApiKey) { mutableStateOf(kalpaApiKey) }
    var showKalpaKeyPlain by remember { mutableStateOf(false) }

    var customTriggerInput by remember { mutableStateOf("") }
    var customReplyInput by remember { mutableStateOf("") }

    var historySubTab by remember { mutableStateOf(0) } // 0 = Chat History, 1 = Memory Vault
    var newMemoryText by remember { mutableStateOf("") }

    var newContactName by remember { mutableStateOf("") }
    var newContactTag by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    val violetGlassBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                SuaCyan.copy(alpha = 0.78f),
                SuaElectricBlue.copy(alpha = 0.6f),
                SuaBorder.copy(alpha = 0.8f)
            )
        )
    }

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndHardware()
    }

    fun launchWriteSettingsIntent() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            systemSettingsLauncher.launch(intent)
        } catch (_: Exception) {
        }
    }

    fun launchDeviceAdminIntent() {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    viewModel.controller.adminComponent
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enable Device Admin to physically lock your phone screen on voice command."
                )
            }
            systemSettingsLauncher.launch(intent)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("BatteryLife")
    fun launchBatteryOptIntent() {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            systemSettingsLauncher.launch(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                systemSettingsLauncher.launch(fallback)
            } catch (_: Exception) {
            }
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissionsAndHardware()
        if (!viewModel.controller.canWriteSystemSettings()) {
            launchWriteSettingsIntent()
        } else if (!viewModel.controller.isDeviceAdminActive()) {
            launchDeviceAdminIntent()
        } else if (!viewModel.controller.isIgnoringBatteryOptimizations()) {
            launchBatteryOptIntent()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionsAndHardware()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SuaObsidian, SuaDeepNavy, SuaObsidian)
                )
            )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "${assistantName.uppercase()} // SETTINGS & HISTORY",
                                style = MaterialTheme.typography.titleLarge,
                                color = SuaTextPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "DEVELOPED BY ${SuaPreferences.DEVELOPER_NAME} • USER: ${userName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = SuaCyan
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    // Feedback Banner
                    item {
                        AnimatedVisibility(visible = !bannerMessage.isNullOrBlank()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.clearSettingsBanner() },
                                colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, SuaEmerald, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Status",
                                        tint = SuaEmerald
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = bannerMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SuaTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 1: KALPA LABS NEURAL VOICE API (kalpalabs.ai)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(SuaDeepNavy)
                                                .border(1.5.dp, SuaCyan, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = "Kalpa Labs Voice API",
                                                tint = SuaCyan
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "01 // KALPA LABS NEURAL VOICE API",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = SuaTextPrimary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Powered by kalpalabs.ai (Natural Hindi & English Voice)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SuaCyan
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SuaEmerald.copy(alpha = 0.16f))
                                            .border(1.dp, SuaEmerald, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuaEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = "SELECT KALPA NEURAL VOICE PROFILE:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuaCyan,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuaPreferences.KALPA_VOICE_OPTIONS.forEach { option ->
                                        FilterChip(
                                            selected = kalpaVoiceId == option.id,
                                            onClick = { viewModel.selectKalpaVoice(option.id) },
                                            label = {
                                                Text(
                                                    text = "${option.name} • ${option.subtitle}",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SuaCyan,
                                                selectedLabelColor = SuaObsidian
                                            )
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = kalpaKeyInput,
                                    onValueChange = { kalpaKeyInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("kalpa_api_key_input"),
                                    label = { Text("Kalpa Labs API Key (sk-live-...)") },
                                    placeholder = { Text("Paste your Kalpa Labs API key") },
                                    singleLine = true,
                                    visualTransformation = if (showKalpaKeyPlain) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val clip = clipboardManager.getText()?.text
                                                    if (!clip.isNullOrBlank()) {
                                                        kalpaKeyInput = clip.trim()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentPaste,
                                                    contentDescription = "Paste Kalpa Key",
                                                    tint = SuaCyan
                                                )
                                            }
                                            IconButton(onClick = { showKalpaKeyPlain = !showKalpaKeyPlain }) {
                                                Icon(
                                                    imageVector = if (showKalpaKeyPlain) {
                                                        Icons.Default.VisibilityOff
                                                    } else {
                                                        Icons.Default.Visibility
                                                    },
                                                    contentDescription = "Show or Hide Kalpa Key",
                                                    tint = SuaTextPrimary
                                                )
                                            }
                                        }
                                    },
                                    colors = violetTextFieldColors()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.saveKalpaVoiceConfig(kalpaKeyInput, kalpaVoiceId)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("save_kalpa_key_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SuaCyan,
                                            contentColor = SuaObsidian
                                        )
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("SAVE KALPA KEY", fontWeight = FontWeight.ExtraBold)
                                    }

                                    Button(
                                        onClick = { viewModel.testVoiceGreeting() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("test_kalpa_voice_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SuaElectricBlue,
                                            contentColor = SuaTextPrimary
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TEST VOICE", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: DEVELOPER (MR. SHASHANK), USER NAME & CUSTOM WAKE-WORD NAME
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "02 // IDENTITY, USER & WAKE-WORD NAME",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SuaTextPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Official Developer: ${SuaPreferences.DEVELOPER_NAME}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SuaEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "Whenever anyone asks 'Who developed you?' in Hindi or English, the assistant proudly answers: ${SuaPreferences.DEVELOPER_NAME}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuaTextSecondary
                                )

                                OutlinedTextField(
                                    value = userNameInput,
                                    onValueChange = { userNameInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("user_name_input"),
                                    label = { Text("Your User Name (Active User)") },
                                    placeholder = { Text("e.g. Shashank") },
                                    singleLine = true,
                                    colors = violetTextFieldColors()
                                )

                                OutlinedTextField(
                                    value = assistantNameInput,
                                    onValueChange = { assistantNameInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("assistant_name_input"),
                                    label = { Text("Assistant Name / Wake-Word (e.g. Moko)") },
                                    placeholder = { Text("Say 'Ok Moko' or 'Hi <Name>' to wake") },
                                    singleLine = true,
                                    colors = violetTextFieldColors()
                                )

                                Button(
                                    onClick = {
                                        viewModel.saveIdentityProfile(userNameInput, assistantNameInput)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("save_identity_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaCyan,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SAVE USER NAME & WAKE-WORD ('OK ${assistantNameInput.uppercase()}')",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 3: UNIVERSAL MULTI-AI API HUB (Gemini, ChatGPT, Claude, or Custom API Link + Key)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(SuaDeepNavy)
                                                .border(1.5.dp, SuaCyan, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = "Universal AI API Hub",
                                                tint = SuaCyan
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "03 // UNIVERSAL MULTI-AI API HUB",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = SuaTextPrimary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Supports Gemini, ChatGPT, Claude, Groq & Custom Links",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SuaCyan
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SuaDeepNavy)
                                            .border(
                                                1.dp,
                                                if (hasEffectiveApiKey) SuaEmerald else SuaAmber,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (hasEffectiveApiKey) "AI LINKED" else "OFFLINE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (hasEffectiveApiKey) SuaEmerald else SuaAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = "SELECT AI PROVIDER (OR LEAVE ON AUTO-DETECT):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuaCyan,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AiProviderType.entries.forEach { prov ->
                                        FilterChip(
                                            selected = aiProvider == prov,
                                            onClick = { viewModel.setAiProvider(prov) },
                                            label = {
                                                Text(
                                                    text = prov.displayName,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SuaCyan,
                                                selectedLabelColor = SuaObsidian
                                            )
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("api_key_input"),
                                    label = { Text("API Key (Gemini AIza... / ChatGPT sk-... / Claude sk-ant-...)") },
                                    placeholder = { Text("Paste any AI API Key here") },
                                    singleLine = true,
                                    visualTransformation = if (showApiKeyPlain) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val clipText = clipboardManager.getText()?.text
                                                    if (!clipText.isNullOrBlank()) {
                                                        apiKeyInput = clipText.trim()
                                                    }
                                                },
                                                modifier = Modifier.testTag("paste_api_key_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentPaste,
                                                    contentDescription = "Paste API Key",
                                                    tint = SuaCyan
                                                )
                                            }
                                            IconButton(onClick = { showApiKeyPlain = !showApiKeyPlain }) {
                                                Icon(
                                                    imageVector = if (showApiKeyPlain) {
                                                        Icons.Default.VisibilityOff
                                                    } else {
                                                        Icons.Default.Visibility
                                                    },
                                                    contentDescription = "Toggle API Key Visibility",
                                                    tint = SuaTextPrimary
                                                )
                                            }
                                        }
                                    },
                                    colors = violetTextFieldColors()
                                )

                                OutlinedTextField(
                                    value = customUrlInput,
                                    onValueChange = { customUrlInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_api_url_input"),
                                    label = { Text("API Website Link / Endpoint URL (Optional)") },
                                    placeholder = { Text("Leave blank for automatic official URL, or paste custom API URL") },
                                    singleLine = true,
                                    colors = violetTextFieldColors()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.saveApiConfiguration(apiKeyInput, customUrlInput)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .testTag("save_api_key_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SuaEmerald,
                                            contentColor = SuaObsidian
                                        )
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "Save API Key & Link")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("SAVE API & LINK", fontWeight = FontWeight.ExtraBold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            apiKeyInput = ""
                                            customUrlInput = ""
                                            viewModel.deleteApiKey()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .testTag("delete_api_key_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SuaCrimson),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = SuaCrimson
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete API Key")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CLEAR API", fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                Text(
                                    text = "GEMINI MODEL VERSION (AUTO-DISCOVERY ENABLED):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuaCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuaPreferences.SUPPORTED_GEMINI_MODELS.forEach { mId ->
                                        FilterChip(
                                            selected = geminiModel == mId,
                                            onClick = { viewModel.setGeminiModel(mId) },
                                            label = { Text(mId) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SuaCyan,
                                                selectedLabelColor = SuaObsidian
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 4: BACKGROUND WAKE ENGINE, VOICE TUNING & CUSTOM COMMANDS
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = SuaCyan
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "04 // WAKE ENGINE & CUSTOM VOICE COMMANDS",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SuaTextPrimary,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Configure low-battery wake mode, voice tone & custom commands",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuaCyan
                                        )
                                    }
                                }

                                Text(
                                    text = "OFFLINE FALLBACK VOICE TONE PRESET:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuaCyan,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VoicePreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = voicePreset == preset,
                                            onClick = { viewModel.selectVoicePreset(preset) },
                                            label = { Text(preset.label, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SuaCyan,
                                                selectedLabelColor = SuaObsidian
                                            )
                                        )
                                    }
                                }

                                // Pitch & Speed Sliders
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SuaDeepNavy)
                                        .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Voice Sweetness / Pitch", color = SuaTextPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2fx", voicePitch), color = SuaCyan, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = voicePitch,
                                        onValueChange = { viewModel.updateVoicePitch(it) },
                                        valueRange = 0.6f..1.6f,
                                        colors = SliderDefaults.colors(thumbColor = SuaCyan, activeTrackColor = SuaCyan)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Speaking Speed", color = SuaTextPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2fx", voiceSpeed), color = SuaEmerald, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = voiceSpeed,
                                        onValueChange = { viewModel.updateVoiceSpeed(it) },
                                        valueRange = 0.6f..1.5f,
                                        colors = SliderDefaults.colors(thumbColor = SuaEmerald, activeTrackColor = SuaEmerald)
                                    )
                                }

                                // Low-Battery "Ok Google" Style Wake Mode Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "'Ok Google' Style Low-Battery Background Wake",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = SuaTextPrimary
                                        )
                                        Text(
                                            text = "Smart duty-cycle listening for 'Ok $assistantName' / 'Hi $assistantName' with minimal battery drain",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = lowBatteryWakeMode,
                                        onCheckedChange = { viewModel.setLowBatteryWakeMode(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SuaObsidian,
                                            checkedTrackColor = SuaEmerald
                                        )
                                    )
                                }

                                // Wake-Word Required Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Require 'Ok $assistantName' / 'Hi $assistantName' Wake-Word",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = SuaTextPrimary
                                        )
                                        Text(
                                            text = "Prevents accidental triggers from background noise",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = wakeWordRequired,
                                        onCheckedChange = { viewModel.setWakeWordRequired(it) },
                                        modifier = Modifier.testTag("wake_word_switch"),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SuaObsidian,
                                            checkedTrackColor = SuaCyan
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilterChip(
                                        selected = voiceLocale == "hi-IN",
                                        onClick = { viewModel.setVoiceLanguage("hi-IN") },
                                        label = { Text("Hindi + English Recognition (hi-IN)", fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SuaCyan,
                                            selectedLabelColor = SuaObsidian
                                        )
                                    )
                                    FilterChip(
                                        selected = voiceLocale == "en-IN",
                                        onClick = { viewModel.setVoiceLanguage("en-IN") },
                                        label = { Text("English Recognition (en-IN)", fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SuaCyan,
                                            selectedLabelColor = SuaObsidian
                                        )
                                    )
                                }

                                Button(
                                    onClick = { viewModel.testVoiceGreeting() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("test_voice_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaElectricBlue,
                                        contentColor = SuaTextPrimary
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("TEST KALPA NEURAL VOICE OUTPUT", fontWeight = FontWeight.ExtraBold)
                                }

                                HorizontalDivider(color = SuaBorder)

                                // CUSTOM VOICE COMMAND BUILDER (100% English UI Labels & Placeholders)
                                Text(
                                    text = "CUSTOM VOICE COMMANDS (ADD YOUR OWN COMMANDS):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuaCyan,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                OutlinedTextField(
                                    value = customTriggerInput,
                                    onValueChange = { customTriggerInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_cmd_trigger_input"),
                                    label = { Text("When I Say (Trigger Phrase)") },
                                    placeholder = { Text("e.g. Good morning / Enter system code") },
                                    singleLine = true,
                                    colors = violetTextFieldColors()
                                )

                                OutlinedTextField(
                                    value = customReplyInput,
                                    onValueChange = { customReplyInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_cmd_reply_input"),
                                    label = { Text("Assistant Should Speak / Reply") },
                                    placeholder = { Text("e.g. Good morning Boss Shashank, have a great day!") },
                                    singleLine = true,
                                    colors = violetTextFieldColors()
                                )

                                Button(
                                    onClick = {
                                        if (customTriggerInput.isNotBlank() && customReplyInput.isNotBlank()) {
                                            viewModel.addCustomVoiceCommand(customTriggerInput, customReplyInput)
                                            customTriggerInput = ""
                                            customReplyInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("add_custom_cmd_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaCyan,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ADD CUSTOM VOICE COMMAND", fontWeight = FontWeight.ExtraBold)
                                }

                                customCommands.forEach { cmd ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SuaDeepNavy)
                                            .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Trigger: \"${cmd.triggerPhrase}\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = SuaCyan,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Spoken Reply: ${cmd.spokenResponse}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SuaTextPrimary
                                            )
                                        }
                                        IconButton(onClick = { viewModel.removeCustomVoiceCommand(cmd.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Custom Command",
                                                tint = SuaCrimson
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 5: CONVERSATION HISTORY & MEMORY VAULT
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = SuaCyan
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "05 // CHAT HISTORY & MEMORY VAULT",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = SuaTextPrimary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Keep important chats in Memory or delete history anytime",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SuaCyan
                                            )
                                        }
                                    }

                                    if (historySubTab == 0 && commandLogs.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.clearCommandHistory() },
                                            modifier = Modifier.testTag("clear_logs_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteSweep,
                                                contentDescription = "Clear All History",
                                                tint = SuaCrimson
                                            )
                                        }
                                    } else if (historySubTab == 1 && memoryItems.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.clearAllMemories() },
                                            modifier = Modifier.testTag("clear_memories_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteSweep,
                                                contentDescription = "Clear All Memories",
                                                tint = SuaCrimson
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilterChip(
                                        selected = historySubTab == 0,
                                        onClick = { historySubTab = 0 },
                                        modifier = Modifier.testTag("tab_chat_history"),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("History (${commandLogs.size})", fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SuaCyan,
                                            selectedLabelColor = SuaObsidian
                                        )
                                    )

                                    FilterChip(
                                        selected = historySubTab == 1,
                                        onClick = { historySubTab = 1 },
                                        modifier = Modifier.testTag("tab_memory_vault"),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Psychology,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Memory Vault (${memoryItems.size})", fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SuaEmerald,
                                            selectedLabelColor = SuaObsidian
                                        )
                                    )
                                }

                                if (historySubTab == 0) {
                                    if (commandLogs.isEmpty()) {
                                        Text(
                                            text = "No conversation history yet. Speak or message on the main screen and your logs will appear here.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            commandLogs.forEach { entry ->
                                                SettingsHistoryRow(
                                                    entry = entry,
                                                    onPinToMemory = { viewModel.pinChatToMemory(entry) },
                                                    onDelete = { viewModel.deleteCommandLogItem(entry.id) }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newMemoryText,
                                            onValueChange = { newMemoryText = it },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("add_memory_input"),
                                            placeholder = { Text("Add a fact to remember...") },
                                            singleLine = true,
                                            colors = violetTextFieldColors()
                                        )
                                        Button(
                                            onClick = {
                                                if (newMemoryText.isNotBlank()) {
                                                    viewModel.addManualMemoryFact(newMemoryText)
                                                    newMemoryText = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SuaEmerald,
                                                contentColor = SuaObsidian
                                            ),
                                            modifier = Modifier.testTag("save_memory_button")
                                        ) {
                                            Text("KEEP", fontWeight = FontWeight.ExtraBold)
                                        }
                                    }

                                    if (memoryItems.isEmpty()) {
                                        Text(
                                            text = "Memory Vault is empty. Add a fact above or tap the Bookmark icon on any chat item.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            memoryItems.forEach { mem ->
                                                SettingsMemoryRow(
                                                    item = mem,
                                                    onDelete = { viewModel.deleteMemoryFact(mem.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 6: SYSTEM PERMISSIONS MATRIX
                    item {
                        val grantedCount = permissionStatuses.count { it.isGranted }
                        val totalCount = permissionStatuses.size
                        val allGranted = grantedCount == totalCount

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.5.dp,
                                        if (allGranted) SuaEmerald else SuaCyan,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "Permissions Matrix",
                                            tint = SuaCyan
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "06 // SYSTEM PERMISSIONS MATRIX",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = SuaTextPrimary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "ACTIVE SUBSYSTEMS: $grantedCount / $totalCount ONLINE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (allGranted) SuaEmerald else SuaAmber
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (!viewModel.controller.areAllRuntimePermissionsGranted()) {
                                            val runtimePerms =
                                                viewModel.controller.getRequiredRuntimePermissions().toTypedArray()
                                            multiplePermissionsLauncher.launch(runtimePerms)
                                        } else if (!viewModel.controller.canWriteSystemSettings()) {
                                            launchWriteSettingsIntent()
                                        } else if (!viewModel.controller.isDeviceAdminActive()) {
                                            launchDeviceAdminIntent()
                                        } else if (!viewModel.controller.isIgnoringBatteryOptimizations()) {
                                            launchBatteryOptIntent()
                                        } else {
                                            val runtimePerms =
                                                viewModel.controller.getRequiredRuntimePermissions().toTypedArray()
                                            multiplePermissionsLauncher.launch(runtimePerms)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("allow_all_permissions_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaCyan,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "GRANT ALL SYSTEM PERMISSIONS",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    permissionStatuses.forEach { perm ->
                                        PermissionRowItem(
                                            item = perm,
                                            onGrantClick = {
                                                when (perm.id) {
                                                    "WRITE_SETTINGS" -> launchWriteSettingsIntent()
                                                    "DEVICE_ADMIN" -> launchDeviceAdminIntent()
                                                    "BATTERY_OPT" -> launchBatteryOptIntent()
                                                    else -> multiplePermissionsLauncher.launch(arrayOf(perm.id))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 7: SPEED DIAL / VOICE CONTACTS
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, violetGlassBrush, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "07 // VOICE SPEED-DIAL DIRECTORY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SuaTextPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                quickContacts.forEach { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SuaDeepNavy)
                                            .border(1.dp, SuaBorder, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = SuaCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = contact.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SuaTextPrimary
                                                )
                                                Text(
                                                    text = "${contact.aliasTag} • ${contact.phoneNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SuaCyan
                                                )
                                            }
                                        }
                                        IconButton(onClick = { viewModel.removeQuickContact(contact.name) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Contact",
                                                tint = SuaCrimson
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newContactName,
                                        onValueChange = { newContactName = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("contact_name_input"),
                                        label = { Text("Name (e.g. Rahul)") },
                                        singleLine = true,
                                        colors = violetTextFieldColors()
                                    )
                                    OutlinedTextField(
                                        value = newContactTag,
                                        onValueChange = { newContactTag = it },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("Alias / Nickname") },
                                        singleLine = true,
                                        colors = violetTextFieldColors()
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newContactPhone,
                                        onValueChange = { newContactPhone = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("contact_phone_input"),
                                        label = { Text("Phone Number") },
                                        singleLine = true,
                                        colors = violetTextFieldColors()
                                    )

                                    Button(
                                        onClick = {
                                            if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                                                viewModel.addOrUpdateQuickContact(
                                                    name = newContactName,
                                                    aliasTag = newContactTag,
                                                    phone = newContactPhone
                                                )
                                                newContactName = ""
                                                newContactTag = ""
                                                newContactPhone = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .height(54.dp)
                                            .testTag("add_contact_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SuaCyan,
                                            contentColor = SuaObsidian
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Contact")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ADD", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(28.dp)) }
                }
            }
        }
    }
}

@Composable
private fun violetTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SuaCyan,
    unfocusedBorderColor = SuaBorder,
    focusedTextColor = SuaTextPrimary,
    unfocusedTextColor = SuaTextPrimary,
    focusedContainerColor = SuaObsidian.copy(alpha = 0.78f),
    unfocusedContainerColor = SuaObsidian.copy(alpha = 0.65f),
    focusedLabelColor = SuaCyan,
    unfocusedLabelColor = SuaTextSecondary
)

@Composable
private fun SettingsHistoryRow(
    entry: CommandLogEntry,
    onPinToMemory: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SuaDeepNavy)
            .border(1.dp, if (entry.isPinnedToMemory) SuaEmerald else SuaBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${entry.actionType.name} • ${entry.timestamp}",
                style = MaterialTheme.typography.labelSmall,
                color = SuaCyan,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(
                    onClick = onPinToMemory,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isPinnedToMemory) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Keep in Memory",
                        tint = if (entry.isPinnedToMemory) SuaEmerald else SuaCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete History Item",
                        tint = SuaCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            text = "You: \"${entry.userCommand}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = SuaTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = entry.suaResponse,
            style = MaterialTheme.typography.bodySmall,
            color = SuaTextSecondary
        )
    }
}

@Composable
private fun SettingsMemoryRow(
    item: MokoMemoryItem,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SuaDeepNavy)
            .border(1.dp, SuaEmerald, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.factText,
                style = MaterialTheme.typography.bodyMedium,
                color = SuaTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Saved ${item.timestamp}",
                style = MaterialTheme.typography.labelSmall,
                color = SuaEmerald
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Memory",
                tint = SuaCrimson
            )
        }
    }
}

@Composable
private fun PermissionRowItem(
    item: PermissionStatusItem,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SuaDeepNavy)
            .border(
                width = 1.dp,
                color = if (item.isGranted) SuaEmerald else SuaBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SuaTextPrimary
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SuaTextSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (item.isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuaObsidian)
                    .border(1.dp, SuaEmerald, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuaCyan,
                    contentColor = SuaObsidian
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "ALLOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
