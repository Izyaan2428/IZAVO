package com.izavo.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.izavo.app.ui.design.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayNameSheet(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val save = { focus.clearFocus(); keyboard?.hide(); onSave(name); onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IzavoMaterial.Sheet,
        scrimColor = ExpenseColors.Scrim, shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Your name", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Used for greetings in IZAVO.", color = ExpenseColors.InkSecondary)
            Spacer(Modifier.height(18.dp))
            NameEditor(name, { name = it }, save)
            Spacer(Modifier.height(24.dp))
            ExpensePrimaryButton("Save", onClick = save)
        }
    }
}
