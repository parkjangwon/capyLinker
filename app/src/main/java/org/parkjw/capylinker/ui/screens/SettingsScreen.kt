package org.parkjw.capylinker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.parkjw.capylinker.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val apiKey by viewModel.apiKey.collectAsState(initial = "")
    val geminiModel by viewModel.geminiModel.collectAsState(initial = "gemini-2.5-flash-lite")
    val language by viewModel.language.collectAsState(initial = "en")
    val theme by viewModel.theme.collectAsState(initial = "system")
    var tempApiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("gemini-2.5-flash-lite") }
    var selectedLanguage by remember { mutableStateOf("en") }
    var selectedTheme by remember { mutableStateOf("system") }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var showBackupSuccess by remember { mutableStateOf(false) }
    var showRestoreSuccess by remember { mutableStateOf(false) }
    var showRestoreFailed by remember { mutableStateOf(false) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }
    var expandedLanguage by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val strings = remember(language) { 
        org.parkjw.capylinker.ui.strings.getStrings(language)
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isBackupInProgress = true
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        viewModel.createBackup().let { (_, jsonString) ->
                            outputStream.write(jsonString.toByteArray())
                            outputStream.flush()
                        }
                    }
                    showBackupSuccess = true
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isBackupInProgress = false
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isRestoreInProgress = true
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val success = viewModel.restoreFromBackup(inputStream)
                        if (success) {
                            showRestoreSuccess = true
                        } else {
                            showRestoreFailed = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showRestoreFailed = true
                } finally {
                    isRestoreInProgress = false
                }
            }
        }
    }

    LaunchedEffect(apiKey) {
        tempApiKey = apiKey
    }

    LaunchedEffect(geminiModel) {
        selectedModel = geminiModel
    }

    LaunchedEffect(language) {
        selectedLanguage = language
    }

    LaunchedEffect(theme) {
        selectedTheme = theme
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language Settings
            Text(
                strings.languageSettings,
                style = MaterialTheme.typography.titleLarge
            )

            ExposedDropdownMenuBox(
                expanded = expandedLanguage,
                onExpandedChange = { expandedLanguage = !expandedLanguage }
            ) {
                OutlinedTextField(
                    value = org.parkjw.capylinker.ui.strings.SupportedLanguages
                        .find { it.first == selectedLanguage }?.second 
                        ?: "English",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.languageLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLanguage) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedLanguage,
                    onDismissRequest = { expandedLanguage = false }
                ) {
                    org.parkjw.capylinker.ui.strings.SupportedLanguages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedLanguage = code
                                viewModel.saveLanguage(code)
                                expandedLanguage = false
                            }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Theme Settings
            Text(
                strings.themeSettings,
                style = MaterialTheme.typography.titleLarge
            )

            ExposedDropdownMenuBox(
                expanded = expandedTheme,
                onExpandedChange = { expandedTheme = !expandedTheme }
            ) {
                OutlinedTextField(
                    value = when(selectedTheme) {
                        "system" -> strings.themeSystem
                        "light" -> strings.themeLight
                        "dark" -> strings.themeDark
                        else -> strings.themeSystem
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.themeLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedTheme,
                    onDismissRequest = { expandedTheme = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.themeSystem) },
                        onClick = {
                            selectedTheme = "system"
                            viewModel.saveTheme("system")
                            expandedTheme = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.themeLight) },
                        onClick = {
                            selectedTheme = "light"
                            viewModel.saveTheme("light")
                            expandedTheme = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.themeDark) },
                        onClick = {
                            selectedTheme = "dark"
                            viewModel.saveTheme("dark")
                            expandedTheme = false
                        }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Clipboard Auto-add Settings
            Text(
                strings.clipboardAutoAddLabel,
                style = MaterialTheme.typography.titleLarge
            )

            val clipboardAutoAdd by viewModel.clipboardAutoAdd.collectAsState(initial = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = strings.clipboardAutoAddLabel)
                Switch(
                    checked = clipboardAutoAdd,
                    onCheckedChange = { viewModel.setClipboardAutoAdd(it) }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // API Key Configuration
            Text(
                strings.apiKeyConfiguration,
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                strings.apiKeyDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = tempApiKey,
                onValueChange = { tempApiKey = it },
                label = { Text(strings.apiKeyLabel) },
                placeholder = { Text(strings.apiKeyPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                strings.apiKeyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val availableModels = listOf(
                "gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite",
                "gemini-2.5-flash" to "Gemini 2.5 Flash",
                "gemini-2.5-pro" to "Gemini 2.5 Pro"
            )

            ExposedDropdownMenuBox(
                expanded = expandedModel,
                onExpandedChange = { expandedModel = !expandedModel }
            ) {
                OutlinedTextField(
                    value = availableModels.find { it.first == selectedModel }?.second 
                        ?: "Gemini 2.5 Flash Lite",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.modelLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedModel,
                    onDismissRequest = { expandedModel = false }
                ) {
                    availableModels.forEach { (modelId, modelName) ->
                        DropdownMenuItem(
                            text = { Text(modelName) },
                            onClick = {
                                selectedModel = modelId
                                viewModel.saveGeminiModel(modelId)
                                expandedModel = false
                            }
                        )
                    }
                }
            }

            Text(
                strings.modelDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        strings.tip,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        strings.quotaTipMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.saveApiKey(tempApiKey)
                    showSaveConfirmation = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = tempApiKey.isNotBlank()
            ) {
                Text(strings.saveApiKey)
            }

            if (showSaveConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        strings.apiKeySaved,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showSaveConfirmation = false
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Backup & Restore Settings
            Text(
                strings.backupAndRestore,
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                strings.backupDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val (fileName, _) = viewModel.createBackup()
                            backupLauncher.launch(fileName)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBackupInProgress && !isRestoreInProgress
                ) {
                    Text(if (isBackupInProgress) strings.backupCreating else strings.createBackup)
                }

                OutlinedButton(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/json"))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBackupInProgress && !isRestoreInProgress
                ) {
                    Text(if (isRestoreInProgress) strings.restoreInProgress else strings.restoreBackup)
                }
            }

            if (showBackupSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        strings.backupSuccess,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showBackupSuccess = false
                }
            }

            if (showRestoreSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        strings.restoreSuccess,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showRestoreSuccess = false
                }
            }

            if (showRestoreFailed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        strings.restoreFailed,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showRestoreFailed = false
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // App Version Info
            val packageInfo = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) {
                    null
                }
            }

            packageInfo?.let { info ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.appVersion,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(
                            strings.versionFormat,
                            info.versionName ?: "Unknown",
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                info.longVersionCode.toString()
                            } else {
                                @Suppress("DEPRECATION")
                                info.versionCode.toString()
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
