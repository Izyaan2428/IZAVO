package com.izavo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(metadata: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var feedback by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet),
        containerColor = ExpenseColors.Glass,
        scrimColor = ExpenseColors.Scrim,
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(.105f).height(4.dp)
                    .background(ExpenseColors.InkSecondary.copy(alpha = .72f), RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(22.dp))
            Text("Send feedback", color = ExpenseColors.Ink, fontSize = 26.sp, lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Tell us what worked—or what got in your way.", color = ExpenseColors.InkSecondary,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            TextField(
                value = feedback,
                onValueChange = { feedback = it.take(5000) },
                placeholder = { Text("Your feedback") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ExpenseColors.SurfaceQuiet,
                    unfocusedContainerColor = ExpenseColors.SurfaceQuiet,
                    disabledContainerColor = ExpenseColors.SurfaceQuiet,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ), minLines = 5
            )
            Spacer(Modifier.height(12.dp))
            Text(metadata.replace('\n', ' ').replace("  ", " "), color = ExpenseColors.InkSecondary,
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("No expense or financial data is attached.", color = ExpenseColors.InkSecondary,
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(18.dp))
            ExpensePrimaryButton("Send feedback", feedback.isNotBlank()) { onSend(feedback.trim()) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
