package com.izavo.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.izavo.app.data.CURRENCY_NAMES
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import com.izavo.app.preferences.CurrencyPreferences
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.ui.design.QuietDivider
import com.izavo.app.ui.design.SettingsRow
import com.izavo.app.importing.BankImportViewModel
import com.izavo.app.importing.ImportUiState
import com.izavo.app.ui.importing.SmartImportFlowScreen
import android.provider.OpenableColumns
import java.time.LocalDate

private enum class CurrencyPicker { HOME, DEFAULT, QUICK }

@Composable
fun SettingsScreen(
    importViewModel: BankImportViewModel,
    preferences: CurrencyPreferences,
    expenses: List<ExpenseEntity>,
    onBack: () -> Unit,
    showBack: Boolean = true,
    onHomeCurrencyChange: (String) -> Unit,
    onDefaultCurrencyChange: (String) -> Unit,
    onQuickCurrenciesChange: (List<String>) -> Unit,
    onShowSecondsChange: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onRequestQuickSettingsTile: () -> Boolean,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    var picker by remember { mutableStateOf<CurrencyPicker?>(null) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onMessage(if (ExpenseCsvExporter.export(context, uri, expenses)) "Export complete" else "Couldn’t export expenses")
    }
    val importState by importViewModel.state.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "BML statement.csv"
            importViewModel.scan(fileName) {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                    ?: throw IllegalArgumentException("File could not be opened")
            }
        }
    }

    if (importState != ImportUiState.Idle) {
        SmartImportFlowScreen(
            state = importState,
            showSeconds = preferences.showSeconds,
            onBack = importViewModel::close,
            onResolveGroup = importViewModel::resolveGroup,
            onRemember = importViewModel::setGroupRemember,
            onReopen = importViewModel::reopenGroup,
            onSkipRemaining = importViewModel::deferRemainingReview,
            onConfirm = importViewModel::confirmImport
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(ExpenseColors.Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 22.dp, bottom = 108.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 2.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ExpenseColors.InkSecondary)
                }
            }
            Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(27.dp))
        SettingsSection("Profile") {
            SettingsRow("Name", trailing = preferences.displayName.ifBlank { "Add your name" }) { showName = true }
        }
        SettingsSection("Preferences") {
            SettingsRow("Home currency", CURRENCY_NAMES[preferences.homeCurrency], preferences.homeCurrency) { picker = CurrencyPicker.HOME }
            QuietDivider()
            SettingsRow("Default expense currency", CURRENCY_NAMES[preferences.defaultExpenseCurrency], preferences.defaultExpenseCurrency) { picker = CurrencyPicker.DEFAULT }
            QuietDivider()
            SettingsRow("Quick currencies", trailing = preferences.quickCurrencies.joinToString(", ")) { picker = CurrencyPicker.QUICK }
        }
        SettingsSection("Quick Add") {
            SettingsRow("Quick Settings tile", "Add Expense from Quick Settings") { showQuickSettings = true }
        }
        SettingsSection("Display") {
            SettingsRow("Show seconds", "Transaction times", trailing = if (preferences.showSeconds) "On" else "Off") {
                onShowSecondsChange(!preferences.showSeconds)
            }
        }
        SettingsSection("Data") {
            SettingsRow("Import transactions", "BML CSV · processed on-device") {
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "text/plain"))
            }
            QuietDivider()
            SettingsRow("Export expenses", trailing = "${expenses.size} saved") {
                exportLauncher.launch("IZAVO-${LocalDate.now()}.csv")
            }
        }
        SettingsSection("Support") {
            SettingsRow("Send feedback") { showFeedback = true }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("IZAVO", color = ExpenseColors.Ink, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Your money, made clearer.", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Version $version Beta", color = ExpenseColors.InkTertiary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showName) DisplayNameSheet(preferences.displayName, { showName = false }, onDisplayNameChange)
    picker?.let { target ->
        CurrencyPickerSheet(target, preferences, { picker = null }, { code ->
            if (target == CurrencyPicker.HOME) onHomeCurrencyChange(code) else onDefaultCurrencyChange(code)
            picker = null
        }, onQuickCurrenciesChange)
    }
    if (showQuickSettings) QuickSettingsSheet(
        onDismiss = { showQuickSettings = false },
        onAdd = {
            val requested = onRequestQuickSettingsTile()
            onMessage(if (requested) "Follow the Android prompt to add the tile" else "Add the tile manually from Quick Settings edit mode")
            showQuickSettings = false
        }
    )
    if (showFeedback) FeedbackBottomSheet(
        metadata = FeedbackSupport.deviceMetadata(context), onDismiss = { showFeedback = false },
        onSend = { message ->
            if (FeedbackSupport.openEmail(context, message)) showFeedback = false else onMessage("No email app is available")
        }
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(3.dp)); content(); Spacer(Modifier.height(22.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPickerSheet(
    target: CurrencyPicker,
    preferences: CurrencyPreferences,
    onDismiss: () -> Unit,
    onSingleSelect: (String) -> Unit,
    onQuickChange: (List<String>) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ExpenseColors.Glass,
        scrimColor = ExpenseColors.Scrim,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(when (target) {
                CurrencyPicker.HOME -> "Home currency"
                CurrencyPicker.DEFAULT -> "Default expense currency"
                CurrencyPicker.QUICK -> "Quick currencies"
            }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            SUPPORTED_CURRENCY_CODES.forEach { code ->
                val selected = when (target) {
                    CurrencyPicker.HOME -> code == preferences.homeCurrency
                    CurrencyPicker.DEFAULT -> code == preferences.defaultExpenseCurrency
                    CurrencyPicker.QUICK -> code in preferences.quickCurrencies
                }
                SettingsRow(CURRENCY_NAMES[code] ?: code, trailing = if (selected) "✓ $code" else code) {
                    if (target == CurrencyPicker.QUICK) {
                        val required = setOf(preferences.homeCurrency, preferences.defaultExpenseCurrency)
                        if (code !in required) onQuickChange(if (selected) preferences.quickCurrencies - code else preferences.quickCurrencies + code)
                    } else onSingleSelect(code)
                }
                if (code != SUPPORTED_CURRENCY_CODES.last()) QuietDivider()
            }
            if (target == CurrencyPicker.QUICK) {
                Spacer(Modifier.height(10.dp))
                Text("Home and default currencies stay available.", color = ExpenseColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                ExpensePrimaryButton("Done", onClick = onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSettingsSheet(onDismiss: () -> Unit, onAdd: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ExpenseColors.Glass,
        scrimColor = ExpenseColors.Scrim,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Faster expense entry", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Open Quick Add directly from Android Quick Settings.", color = ExpenseColors.InkSecondary)
            Spacer(Modifier.height(22.dp))
            ExpensePrimaryButton("Add Quick Settings Tile", onClick = onAdd)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        }
    }
}
