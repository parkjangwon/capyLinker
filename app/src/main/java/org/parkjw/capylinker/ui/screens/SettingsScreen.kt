package org.parkjw.capylinker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.parkjw.capylinker.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val apiKey by viewModel.apiKey.collectAsState(initial = "")
    val language by viewModel.language.collectAsState(initial = "en")
    val theme by viewModel.theme.collectAsState(initial = "system")
    var tempApiKey by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("en") }
    var selectedTheme by remember { mutableStateOf("system") }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var expandedLanguage by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf(false) }

    val strings = remember(language) { 
        org.parkjw.capylinker.ui.strings.getStrings(language)
    }

    LaunchedEffect(apiKey) {
        tempApiKey = apiKey
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
        }
    }
}
