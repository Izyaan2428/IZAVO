package com.izavo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.izavo.app.ui.design.QuietChoice
import com.izavo.app.ui.design.ExpenseColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import com.izavo.app.ui.home.HomeMutedText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleCurrencyPicker(
    label: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SUPPORTED_CURRENCY_CODES.forEach { code ->
            QuietChoice(
                label = code,
                selected = code == selected,
                onClick = { onSelected(code) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickCurrenciesPicker(
    selected: List<String>,
    required: Set<String>,
    onChanged: (List<String>) -> Unit
) {
    Text("Quick Currencies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Home and default currencies stay available.",
        color = ExpenseColors.InkSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SUPPORTED_CURRENCY_CODES.forEach { code ->
            val isSelected = code in selected
            QuietChoice(
                label = code,
                selected = isSelected,
                onClick = {
                    if (code !in required) onChanged(
                        if (isSelected) selected - code else selected + code
                    )
                }
            )
        }
    }
}
