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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PermissionStatusItem
import com.example.data.SuaPreferences
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

    val savedApiKey by viewModel.savedApiKey.collectAsStateWithLifecycle()
    val hasEffectiveApiKey by viewModel.hasEffectiveApiKey.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val permissionStatuses by viewModel.permissionStatuses.collectAsStateWithLifecycle()
    val continuousBgEnabled by viewModel.continuousBgEnabled.collectAsStateWithLifecycle()
    val wakeWordRequired by viewModel.wakeWordRequired.collectAsStateWithLifecycle()
    val voiceLocale by viewModel.voiceLocale.collectAsStateWithLifecycle()
    val quickContacts by viewModel.quickContacts.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.settingsBannerMessage.collectAsStateWithLifecycle()

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var showApiKeyPlain by remember { mutableStateOf(false) }

    var newContactName by remember { mutableStateOf("") }
    var newContactTag by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

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
                    "Enable SUA Tactical Core to physically lock your phone screen on voice command."
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = SuaObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MOKO // SYSTEM SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            color = SuaTextPrimary
                        )
                        Text(
                            text = "PERMISSIONS, GEMINI API KEY & 'HI MOKO' WAKE ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuaCyan
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
                            contentDescription = "Back to Moko Home",
                            tint = SuaCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SuaDeepNavy
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
                            colors = CardDefaults.cardColors(
                                containerColor = SuaEmerald.copy(alpha = 0.16f)
                            ),
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        SuaEmerald,
                                        CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                                    )
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
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // SECTION 1: ONE-CLICK ALLOW ALL PERMISSIONS
                item {
                    val grantedCount = permissionStatuses.count { it.isGranted }
                    val totalCount = permissionStatuses.size
                    val allGranted = grantedCount == totalCount

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (allGranted) SuaEmerald.copy(alpha = 0.7f) else SuaCyan.copy(alpha = 0.6f),
                                    CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
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
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(SuaCyan.copy(alpha = 0.15f))
                                            .border(1.dp, SuaCyan, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "Permissions Matrix",
                                            tint = SuaCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "01 // SYSTEM PERMISSIONS MATRIX",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SuaTextPrimary
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
                                    .height(54.dp)
                                    .testTag("allow_all_permissions_button"),
                                shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuaCyan,
                                    contentColor = SuaObsidian
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Grant All System Permissions"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "GRANT ALL SYSTEM PERMISSIONS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
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

                // SECTION 2: GOOGLE GEMINI API KEY & MODEL SELECTOR
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(listOf(SuaCyan, SuaElectricBlue)),
                                    shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
                                )
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(SuaElectricBlue.copy(alpha = 0.2f))
                                            .border(1.dp, SuaCyan, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = "Google Gemini API Key",
                                            tint = SuaCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "02 // GOOGLE GEMINI API CORE",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SuaTextPrimary
                                        )
                                        Text(
                                            text = "AES-GCM Keystore Encrypted Key & Model Selector",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SuaTextSecondary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (hasEffectiveApiKey) SuaEmerald.copy(alpha = 0.2f)
                                            else SuaAmber.copy(alpha = 0.2f)
                                        )
                                        .border(
                                            1.dp,
                                            if (hasEffectiveApiKey) SuaEmerald else SuaAmber,
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (hasEffectiveApiKey) "AI LINKED" else "FAST LOCAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasEffectiveApiKey) SuaEmerald else SuaAmber
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input"),
                                label = { Text("Google Gemini API Key (AIzaSy...)") },
                                placeholder = { Text("Paste your Google Gemini API Key here") },
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
                                        IconButton(
                                            onClick = { showApiKeyPlain = !showApiKeyPlain }
                                        ) {
                                            Icon(
                                                imageVector = if (showApiKeyPlain) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
                                                },
                                                contentDescription = "Toggle API Key Visibility",
                                                tint = SuaTextSecondary
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SuaCyan,
                                    unfocusedBorderColor = SuaBorder,
                                    focusedTextColor = SuaTextPrimary,
                                    unfocusedTextColor = SuaTextPrimary,
                                    focusedLabelColor = SuaCyan,
                                    unfocusedLabelColor = SuaTextSecondary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.saveApiKey(apiKeyInput) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("save_api_key_button"),
                                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaEmerald,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save API Key"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SAVE KEY",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        apiKeyInput = ""
                                        viewModel.deleteApiKey()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("delete_api_key_button"),
                                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = SuaCrimson
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete API Key"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "DELETE KEY",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            // Model Selector Row
                            Text(
                                text = "GEMINI MODEL VERSION (AUTO-FALLBACK ENABLED):",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuaCyan
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
                                            selectedContainerColor = SuaCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = SuaCyan
                                        )
                                    )
                                }
                            }

                            Text(
                                text = if (savedApiKey.isNotEmpty()) {
                                    "STATUS // Encrypted Key active (${savedApiKey.take(4)}...${savedApiKey.takeLast(4)}). Model: $geminiModel."
                                } else {
                                    "STATUS // Hardware commands work offline. Paste your Gemini API Key and tap SAVE KEY for AI features."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (savedApiKey.isNotEmpty()) SuaEmerald else SuaTextSecondary
                            )
                        }
                    }
                }

                // SECTION 3: WAKE-WORD ("SUA"), HINDI VOICE ENGINE & BACKGROUND LOOP
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SuaBorder, CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(SuaCyan.copy(alpha = 0.15f))
                                        .border(1.dp, SuaCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = "Voice Synthesis",
                                        tint = SuaCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "03 // WAKE-WORD ('HI MOKO') & VOICE ENGINE",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SuaTextPrimary
                                    )
                                    Text(
                                        text = "Prevents accidental triggers from TV or room conversation",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SuaCyan
                                    )
                                }
                            }

                            // Wake-Word Toggle ("Hi Moko" / "हाय मोको")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Require 'Hi Moko' Wake-Word in Background",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuaTextPrimary
                                    )
                                    Text(
                                        text = "Only executes background voice commands when you say 'Hi Moko', 'Hey Moko' or 'Moko' (plus 22s follow-up window)",
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
                                        checkedTrackColor = SuaEmerald
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Continuous Online Listening (Even on Lock Screen)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = SuaTextPrimary
                                    )
                                    Text(
                                        text = "Automatically keeps Moko listening in Online Mode after speaking each response",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SuaTextSecondary
                                    )
                                }
                                Switch(
                                    checked = continuousBgEnabled,
                                    onCheckedChange = { viewModel.setContinuousBackground(it) },
                                    modifier = Modifier.testTag("continuous_bg_switch"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SuaObsidian,
                                        checkedTrackColor = SuaCyan
                                    )
                                )
                            }

                            Button(
                                onClick = { viewModel.testVoiceGreeting() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("test_voice_button"),
                                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuaElectricBlue,
                                    contentColor = SuaTextPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Test Moko Hindi Voice"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "TEST MOKO HINDI VOICE OUTPUT",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = voiceLocale == "hi-IN",
                                    onClick = { viewModel.setVoiceLanguage("hi-IN") },
                                    label = { Text("Hindi Voice (hi-IN)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SuaCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = SuaCyan
                                    )
                                )
                                FilterChip(
                                    selected = voiceLocale == "en-IN",
                                    onClick = { viewModel.setVoiceLanguage("en-IN") },
                                    label = { Text("Indian English (en-IN)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SuaCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = SuaCyan
                                    )
                                )
                            }
                        }
                    }
                }

                // SECTION 4: SPEED DIAL / VOICE CONTACTS (No fake numbers!)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SuaSurfaceCard),
                        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SuaBorder, CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "04 // VOICE SPEED-DIAL DIRECTORY",
                                style = MaterialTheme.typography.titleMedium,
                                color = SuaTextPrimary
                            )
                            Text(
                                text = "SUA automatically matches Hindi & English spoken names (e.g. 'राहुल' -> 'Rahul') from your real phone Contacts. You can also add custom speed-dial numbers below:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SuaTextSecondary
                            )

                            if (quickContacts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SuaDeepNavy)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "No custom speed-dial numbers saved. SUA will search your phone Contacts book directly (no fake numbers).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SuaTextSecondary
                                    )
                                }
                            } else {
                                quickContacts.forEach { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SuaDeepNavy)
                                            .border(1.dp, SuaBorder, RoundedCornerShape(10.dp))
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
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SuaTextPrimary
                                                )
                                                Text(
                                                    text = "${contact.aliasTag} • ${contact.phoneNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SuaCyan
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeQuickContact(contact.name) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove ${contact.name}",
                                                tint = SuaTextSecondary
                                            )
                                        }
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
                                    label = { Text("Name (e.g. Khushi / Rahul)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newContactTag,
                                    onValueChange = { newContactTag = it },
                                    label = { Text("Role Tag (VIP)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
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
                                    label = { Text("Real Phone Number") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.addOrUpdateQuickContact(
                                            name = newContactName,
                                            aliasTag = newContactTag,
                                            phone = newContactPhone
                                        )
                                        newContactName = ""
                                        newContactTag = ""
                                        newContactPhone = ""
                                    },
                                    modifier = Modifier.height(54.dp),
                                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuaCyan,
                                        contentColor = SuaObsidian
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Save Contact")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ADD")
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
                1.dp,
                if (item.isGranted) SuaEmerald.copy(alpha = 0.35f) else SuaAmber.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = !item.isGranted) { onGrantClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = item.title,
                tint = if (item.isGranted) SuaEmerald else SuaAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = SuaTextPrimary
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SuaTextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (item.isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SuaEmerald.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "ONLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuaEmerald
                )
            }
        } else {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuaAmber,
                    contentColor = SuaObsidian
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = if (item.isSpecialSystemPermission) "ENABLE" else "GRANT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
