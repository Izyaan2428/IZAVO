package com.izavo.app.data

/** Persisted category keys. Keep these stable because Room stores them as text. */
object ExpenseCategories {
    const val FOOD = "Food"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills"
    const val SUBSCRIPTIONS = "Subscriptions"
    const val OTHER = "Other"

    val all = listOf(FOOD, TRANSPORT, SHOPPING, BILLS, SUBSCRIPTIONS, OTHER)

    fun normalized(value: String?): String = value?.takeIf(String::isNotBlank) ?: OTHER
}
