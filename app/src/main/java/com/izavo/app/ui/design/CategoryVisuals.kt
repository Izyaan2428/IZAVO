package com.izavo.app.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.izavo.app.data.ExpenseCategories

data class ExpenseCategoryVisual(
    val key: String, val icon: ImageVector, val lightColor: Color, val darkColor: Color
) {
    val color: Color get() = if (ExpenseColors.IsDark) darkColor else lightColor
}

object ExpenseCategoryVisuals {
    private val entries = listOf(
        ExpenseCategoryVisual(ExpenseCategories.FOOD, Icons.Outlined.Restaurant, Color(0xFFE89A83), Color(0xFFE5917E)),
        ExpenseCategoryVisual(ExpenseCategories.TRANSPORT, Icons.Outlined.DirectionsCar, Color(0xFF69AEC3), Color(0xFF61B5D1)),
        ExpenseCategoryVisual(ExpenseCategories.SHOPPING, Icons.Outlined.ShoppingBag, Color(0xFFA58BC0), Color(0xFFA994D2)),
        ExpenseCategoryVisual(ExpenseCategories.BILLS, Icons.AutoMirrored.Outlined.ReceiptLong, Color(0xFFD4A35F), Color(0xFFD7AA67)),
        ExpenseCategoryVisual(ExpenseCategories.SUBSCRIPTIONS, Icons.Outlined.CreditCard, Color(0xFF7F91C3), Color(0xFF839DD6)),
        ExpenseCategoryVisual(ExpenseCategories.OTHER, Icons.Outlined.MoreHoriz, Color(0xFF98A2A8), Color(0xFF93A1AE))
    )
    val all: List<ExpenseCategoryVisual> = entries
    fun forKey(key: String) = entries.firstOrNull { it.key == key } ?: entries.last()
}
