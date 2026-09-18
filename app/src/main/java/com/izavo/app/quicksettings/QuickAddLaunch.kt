package com.izavo.app.quicksettings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ACTION_OPEN_QUICK_ADD =
    "com.izavo.app.OPEN_QUICK_ADD"

/** Activity-scoped one-shot request source; no process-global navigation state is used. */
class QuickAddRequestController {
    private val mutableRequests = MutableStateFlow(0L)
    val requests: StateFlow<Long> = mutableRequests.asStateFlow()

    fun requestQuickAdd() {
        mutableRequests.value += 1L
    }
}
