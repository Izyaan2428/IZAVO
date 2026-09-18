package com.izavo.app.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.izavo.app.ui.format.TransactionTimeFormatter

@Composable
fun DateTimeEditor(
    dateTime: LocalDateTime,
    showSeconds: Boolean = false,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpenseShapes.Control),
        color = ExpenseColors.SurfaceQuiet
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(
                onClick = onDateClick
            ) {

                Text(
                    dateTime.format(
                        DateTimeFormatter.ofPattern(
                            "EEE, d MMM"
                        )
                    )
                )
            }

            TextButton(
                onClick = onTimeClick
            ) {

                Text(
                    TransactionTimeFormatter.localTime(dateTime, showSeconds)
                )
            }
        }
    }
}
