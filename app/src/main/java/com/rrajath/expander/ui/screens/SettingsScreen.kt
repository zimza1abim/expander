package com.rrajath.expander.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rrajath.expander.service.TextExpansionService
import com.rrajath.expander.service.SuggestionMenuLayout
import com.rrajath.expander.service.SuggestionResultColor
import com.rrajath.expander.util.ThemeMode
import com.rrajath.expander.util.ThemePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onThemeChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(TextExpansionService.isServiceEnabled(context)) }
    var smartPunctuationEnabled by remember { mutableStateOf(TextExpansionService.isSmartPunctuationEnabled(context)) }
    var smartPunctuationChars by remember { mutableStateOf(TextExpansionService.getSmartPunctuationCharsRaw(context)) }
    var partialSuggestionsEnabled by remember { mutableStateOf(TextExpansionService.arePartialSuggestionsEnabled(context)) }
    var partialSuggestionsMinLength by remember { mutableStateOf(TextExpansionService.getPartialSuggestionsMinLength(context).toString()) }
    var partialSuggestionsMaxResults by remember { mutableStateOf(TextExpansionService.getPartialSuggestionsMaxResults(context).toString()) }
    var useHorizontalSuggestionMenu by remember {
        mutableStateOf(TextExpansionService.getSuggestionMenuLayout(context) == SuggestionMenuLayout.HORIZONTAL)
    }
    var resultColor by remember { mutableStateOf(TextExpansionService.getSuggestionResultColor(context)) }
    var customResultColor by remember { mutableIntStateOf(TextExpansionService.getCustomResultColor(context)) }
    var currentTheme by remember { mutableStateOf(ThemePreferences.getThemeMode(context)) }
    var showThemeDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                currentTheme = theme
                ThemePreferences.setThemeMode(context, theme)
                onThemeChanged()
                showThemeDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Service Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Text Expansion",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (serviceEnabled) "Service is active" else "Service is disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = serviceEnabled,
                            onCheckedChange = {
                                serviceEnabled = it
                                TextExpansionService.setServiceEnabled(context, it)
                            }
                        )
                    }
                }
            }

            // Optional prefix suggestions. Disabled by default to preserve the
            // original exact-match-on-space behavior for existing users.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Partial Match Suggestions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Preview matching snippets while typing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = partialSuggestionsEnabled,
                            onCheckedChange = {
                                partialSuggestionsEnabled = it
                                TextExpansionService.setPartialSuggestionsEnabled(context, it)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = partialSuggestionsMinLength,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.toIntOrNull() in 1..20) {
                                    partialSuggestionsMinLength = value
                                    value.toIntOrNull()?.let {
                                        TextExpansionService.setPartialSuggestionsMinLength(context, it)
                                    }
                                }
                            },
                            enabled = partialSuggestionsEnabled,
                            label = { Text("Minimum characters") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = partialSuggestionsMaxResults,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.toIntOrNull() in 1..10) {
                                    partialSuggestionsMaxResults = value
                                    value.toIntOrNull()?.let {
                                        TextExpansionService.setPartialSuggestionsMaxResults(context, it)
                                    }
                                }
                            },
                            enabled = partialSuggestionsEnabled,
                            label = { Text("Maximum results") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Suggestion menu layout",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                useHorizontalSuggestionMenu = false
                                TextExpansionService.setSuggestionMenuLayout(context, SuggestionMenuLayout.LIST)
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = if (!useHorizontalSuggestionMenu) 2.dp else 1.dp,
                                color = if (!useHorizontalSuggestionMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (!useHorizontalSuggestionMenu) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            enabled = partialSuggestionsEnabled
                        ) { Text("Vertical list") }
                        OutlinedButton(
                            onClick = {
                                useHorizontalSuggestionMenu = true
                                TextExpansionService.setSuggestionMenuLayout(context, SuggestionMenuLayout.HORIZONTAL)
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = if (useHorizontalSuggestionMenu) 2.dp else 1.dp,
                                color = if (useHorizontalSuggestionMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (useHorizontalSuggestionMenu) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            enabled = partialSuggestionsEnabled
                        ) { Text("Horizontal bar") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsItem(
                        title = "App theme",
                        subtitle = "${currentTheme.name.lowercase().replaceFirstChar { it.uppercase() }} · App and suggestion menu",
                        onClick = { showThemeDialog = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ResultColorPicker(
                        selected = resultColor,
                        customColor = customResultColor,
                        enabled = partialSuggestionsEnabled,
                        dark = currentTheme == ThemeMode.DARK ||
                            (currentTheme == ThemeMode.SYSTEM && isSystemInDarkTheme()),
                        onSelected = {
                            resultColor = it
                            TextExpansionService.setSuggestionResultColor(context, it)
                        },
                        onCustomColor = {
                            customResultColor = it
                            resultColor = SuggestionResultColor.CUSTOM
                            TextExpansionService.setCustomResultColor(context, it)
                        }
                    )
                }
            }

            // Smart Punctuation Spacing Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Punctuation Spacing",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Fixes \"you ? \" to \"you? \" when punctuation is typed after a space",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = smartPunctuationEnabled,
                            onCheckedChange = {
                                smartPunctuationEnabled = it
                                TextExpansionService.setSmartPunctuationEnabled(context, it)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = smartPunctuationChars,
                        onValueChange = {
                            smartPunctuationChars = it
                            TextExpansionService.setSmartPunctuationCharsRaw(context, it)
                        },
                        enabled = smartPunctuationEnabled,
                        label = { Text("Punctuation (space-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Accessibility Settings
            SettingsItem(
                title = "Accessibility Settings",
                subtitle = "Grant accessibility permission",
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            Divider()

            // Import/Export Section
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            SettingsItem(
                title = "Export Snippets",
                subtitle = "Save snippets to JSON file",
                onClick = onExportClick
            )

            SettingsItem(
                title = "Import Snippets",
                subtitle = "Load snippets from JSON file",
                onClick = onImportClick
            )

            Divider()

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Expander",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version 1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A text expansion tool that works system-wide using accessibility services.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultColorPicker(
    selected: SuggestionResultColor,
    customColor: Int,
    enabled: Boolean,
    dark: Boolean,
    onSelected: (SuggestionResultColor) -> Unit,
    onCustomColor: (Int) -> Unit
) {
    var draftColor by rememberSaveable(customColor) { mutableIntStateOf(customColor) }
    val previewInk = if (selected == SuggestionResultColor.CUSTOM) draftColor else selected.argb(dark)
    Text("Result text color", style = MaterialTheme.typography.labelLarge)
    Text("Applies to all suggestion previews. Triggers keep their secondary color.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    SuggestionResultColor.entries.filter { it != SuggestionResultColor.CUSTOM }.chunked(3).forEach { colors ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.forEach { color ->
                FilterChip(
                    selected = color == selected,
                    onClick = { onSelected(color) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    label = { Text(color.label, maxLines = 1) },
                    leadingIcon = {
                        if (color == selected) Icon(Icons.Default.Check, contentDescription = "Selected", Modifier.size(16.dp))
                        else Box(Modifier.size(12.dp).background(Color(color.argb(dark)), CircleShape))
                    }
                )
            }
        }
    }
    FilterChip(
        selected = selected == SuggestionResultColor.CUSTOM,
        onClick = { onSelected(SuggestionResultColor.CUSTOM) },
        enabled = enabled,
        label = { Text("Color wheel") },
        leadingIcon = {
            if (selected == SuggestionResultColor.CUSTOM) Icon(Icons.Default.Check, "Selected", Modifier.size(16.dp))
            else Box(Modifier.size(12.dp).background(Color(customColor), CircleShape))
        }
    )
    if (selected == SuggestionResultColor.CUSTOM) {
        ColorWheelPicker(initialColor = customColor, enabled = enabled, onColorChange = { draftColor = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(28.dp).semantics { contentDescription = "Selected color" },
                shape = RoundedCornerShape(8.dp), color = Color(draftColor),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
            Text(SuggestionResultColor.formatHex(draftColor), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onCustomColor(draftColor) }, enabled = enabled) {
                Text("Apply")
            }
        }
        if (androidx.core.graphics.ColorUtils.calculateContrast(draftColor,
                if (dark) 0xFF232326.toInt() else 0xFFF4F4F7.toInt()) < 4.5) {
            Text("Low contrast on this theme. A darker or lighter color will be easier to read.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(if (dark) 0xFF232326 else 0xFFF4F4F7),
        border = BorderStroke(0.5.dp, Color(if (dark) 0xFF65656B else 0xFFC9C9D0))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("example.com", color = Color(previewInk), style = MaterialTheme.typography.bodyMedium)
            Text("email1", color = Color(if (dark) 0xFFB7B7BF else 0xFF696972),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App theme") },
        text = {
            Column {
                ThemeOption(
                    title = "Light",
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onThemeSelected(ThemeMode.LIGHT) }
                )
                ThemeOption(
                    title = "Dark",
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeSelected(ThemeMode.DARK) }
                )
                ThemeOption(
                    title = "System default",
                    selected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onThemeSelected(ThemeMode.SYSTEM) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
