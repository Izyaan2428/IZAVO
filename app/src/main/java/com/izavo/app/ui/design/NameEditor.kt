package com.izavo.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable
fun NameEditor(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    BasicTextField(value, onValueChange = { text ->
        // Keep trailing spaces while typing; trim when saving.
        val count = text.codePointCount(0, text.length).coerceAtMost(40)
        onChange(text.substring(0, text.offsetByCodePoints(0, count)))
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp)
        .semantics { contentDescription = "Your name" },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineLarge.copy(color = ExpenseColors.Ink, textDirection = TextDirection.ContentOrLtr),
        cursorBrush = SolidColor(ExpenseColors.Cyan),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { field -> Box { if (value.isEmpty()) Text("Your name", style = MaterialTheme.typography.headlineLarge, color = ExpenseColors.InkTertiary); field() } })
}
