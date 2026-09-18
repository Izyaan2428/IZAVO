package com.izavo.app.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.izavo.app.ui.design.QuietChoice
import com.izavo.app.ui.design.ExpenseColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseCategories

val QUICK_ADD_CATEGORIES = ExpenseCategories.all

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickChoiceRow(
    choices: List<String>,
    selectedChoice: String?,
    allowNoSelection: Boolean,
    onChoiceSelected: (String?) -> Unit
) {
    if (choices == QUICK_ADD_CATEGORIES && choices.size <= 5) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val selected = selectedChoice == choice
                val weight = when (choice) {
                    "Transport", "Shopping" -> 1.35f
                    "Food", "Bills" -> .85f
                    else -> 1f
                }
                Surface(
                    onClick = {
                        onChoiceSelected(if (allowNoSelection && selected) null else choice)
                    },
                    modifier = Modifier.weight(weight).height(42.dp),
                    color = if (selected) ExpenseColors.CyanSoft.copy(alpha = .94f)
                        else ExpenseColors.SurfaceQuiet.copy(alpha = .88f),
                    contentColor = if (selected) ExpenseColors.CyanSelected else ExpenseColors.InkSecondary,
                    shape = RoundedCornerShape(15.dp),
                    border = if (selected) BorderStroke(1.dp, Color(0x576ED6E5)) else null
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            choice,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        choices.forEach { choice ->
            QuietChoice(
                label = choice,
                selected = selectedChoice == choice,
                onClick = {
                    val newChoice = if (
                        allowNoSelection && selectedChoice == choice
                    ) {
                        null
                    } else {
                        choice
                    }
                    onChoiceSelected(newChoice)
                }
            )
        }
    }
}
