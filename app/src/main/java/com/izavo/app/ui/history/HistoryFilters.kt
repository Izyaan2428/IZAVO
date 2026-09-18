package com.izavo.app.ui.history

enum class HistoryDateFilter(val label: String) {
    ALL_TIME("All time"),
    TODAY("Today"),
    THIS_MONTH("This month")
}

data class HistoryFilterState(
    val category: String? = null,
    val currencyCode: String? = null,
    val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL_TIME
) {
    val isActive: Boolean
        get() = category != null ||
            currencyCode != null ||
            dateFilter != HistoryDateFilter.ALL_TIME
}
