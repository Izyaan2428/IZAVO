package com.izavo.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izavo.app.data.CURRENCY_NAMES
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import com.izavo.app.preferences.CurrencyPreferences
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.ui.design.HairlineDivider
import com.izavo.app.ui.design.QuietChoice
import com.izavo.app.ui.design.SettingsRow
import com.izavo.app.ui.design.NameEditor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

private enum class OnboardingPicker { HOME, DEFAULT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(initial: CurrencyPreferences, onContinue: (String, String, List<String>, String) -> Unit) {
    var home by rememberSaveable { mutableStateOf(initial.homeCurrency) }
    var default by rememberSaveable { mutableStateOf(initial.defaultExpenseCurrency) }
    var quick by rememberSaveable { mutableStateOf(initial.quickCurrencies) }
    var nameStep by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initial.displayName) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(nameStep) { focus.clearFocus(); keyboard?.hide(); nameStep = false }
    if (nameStep) {
        val submit = { focus.clearFocus(); keyboard?.hide(); onContinue(home, default, quick, name) }
        Column(Modifier.fillMaxSize().background(ExpenseColors.Background).safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp)) {
            Text("IZAVO", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(54.dp))
            Text("What should we call you?", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text("This stays on your device.", color = ExpenseColors.InkSecondary)
            Spacer(Modifier.height(32.dp))
            NameEditor(name, { name = it }, submit)
            Spacer(Modifier.height(40.dp))
            ExpensePrimaryButton("Continue", onClick = submit)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = { name = ""; submit() }, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
        return
    }
    var picker by remember { mutableStateOf<OnboardingPicker?>(null) }

    Box(Modifier.fillMaxSize().background(ExpenseColors.Background)) {
        Canvas(Modifier.fillMaxWidth().height(330.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to ExpenseColors.CyanAtmosphere.copy(alpha = .12f),
                    1f to ExpenseColors.CyanAtmosphere.copy(alpha = 0f),
                    center = Offset(size.width * .5f, size.height * .45f), radius = size.width * .7f
                ), center = Offset(size.width * .5f, size.height * .45f), radius = size.width * .7f
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 50.dp, bottom = 24.dp)) {
            Text("IZAVO", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(27.dp))
            Text("Track spending\nin seconds.", color = ExpenseColors.Ink,
                fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Text("A private, local view of everyday spending—without converting or combining currencies.",
                color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(22.dp))
            Text("• Add from Android Quick Settings", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
            Text("• Your data stays on this device", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
            Text("• Import historical BML statements", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(42.dp))
            OnboardingLabel("Home currency")
            Spacer(Modifier.height(10.dp))
            CurrencyRow(home) { picker = OnboardingPicker.HOME }
            Spacer(Modifier.height(28.dp))
            OnboardingLabel("Default expense currency")
            Spacer(Modifier.height(10.dp))
            CurrencyRow(default) { picker = OnboardingPicker.DEFAULT }
            Spacer(Modifier.height(28.dp))
            OnboardingLabel("Quick currencies")
            Text("Shown in the fast currency picker.", color = ExpenseColors.InkSecondary,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORTED_CURRENCY_CODES.forEach { code ->
                    QuietChoice(code, code in quick) {
                        if (code !in setOf(home, default)) quick = if (code in quick) quick - code else quick + code
                    }
                }
            }
            Spacer(Modifier.height(27.dp))
            Text("You can also add the Add Expense tile to Android Quick Settings later.",
                color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(44.dp))
            ExpensePrimaryButton("Continue") { nameStep = true }
        }
    }

    picker?.let { target ->
        CurrencySelectionSheet(
            title = if (target == OnboardingPicker.HOME) "Home currency" else "Default expense currency",
            selected = if (target == OnboardingPicker.HOME) home else default,
            onDismiss = { picker = null },
            onSelected = { code ->
                if (target == OnboardingPicker.HOME) home = code else default = code
                if (code !in quick) quick = quick + code
                picker = null
            }
        )
    }
}

@Composable
private fun OnboardingLabel(text: String) = Text(text, color = ExpenseColors.InkSecondary,
    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)

@Composable
private fun CurrencyRow(code: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(20.dp), color = ExpenseColors.Surface.copy(alpha = .72f),
        border = BorderStroke(1.dp, ExpenseColors.GlassBorder)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(CURRENCY_NAMES[code] ?: code, color = ExpenseColors.Ink, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            Text(code, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySelectionSheet(title: String, selected: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ExpenseColors.Glass,
        scrimColor = ExpenseColors.Scrim,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            SUPPORTED_CURRENCY_CODES.forEach { code ->
                SettingsRow(CURRENCY_NAMES[code] ?: code, trailing = if (selected == code) "✓ $code" else code) {
                    onSelected(code)
                }
                if (code != SUPPORTED_CURRENCY_CODES.last()) HairlineDivider()
            }
        }
    }
}
