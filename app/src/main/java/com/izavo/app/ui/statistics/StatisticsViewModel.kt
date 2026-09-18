package com.izavo.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.izavo.app.data.ExpenseMerchantMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(private val repository: StatisticsRepository) : ViewModel() {
    private val section = MutableStateFlow(StatisticsSection.OVERVIEW)
    private val period = MutableStateFlow(StatisticsPeriod())
    private val trendRange = MutableStateFlow(TrendRange.SIX_MONTHS)
    private val currency = MutableStateFlow("MVR")
    private val selectedCategory = MutableStateFlow<String?>(null)

    private val expenses = currency.flatMapLatest(repository::observeExpenses)

    val state = combine(
        section, period, trendRange, currency, selectedCategory,
        expenses, repository.observeCurrencyCoverage(), repository.observeMerchantMetadata()
    ) { values ->
        val currentSection = values[0] as StatisticsSection
        val currentPeriod = values[1] as StatisticsPeriod
        val currentRange = values[2] as TrendRange
        val currentCurrency = values[3] as String
        val category = values[4] as String?
        @Suppress("UNCHECKED_CAST") val currentExpenses = values[5] as List<com.izavo.app.data.ExpenseEntity>
        @Suppress("UNCHECKED_CAST") val coverage = values[6] as List<com.izavo.app.data.CurrencyCoverage>
        @Suppress("UNCHECKED_CAST") val metadata = values[7] as List<ExpenseMerchantMetadata>
        val zone = ZoneId.systemDefault()
        val months = currentExpenses.map { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) }.distinct().sortedDescending()
        StatisticsUiState(
            loading = false,
            section = currentSection,
            period = currentPeriod,
            trendRange = currentRange,
            currencyCode = currentCurrency,
            availableCurrencies = (listOf(currentCurrency) + coverage.map { it.currencyCode }).distinct(),
            availableMonths = months,
            snapshot = StatisticsCalculator.calculate(currentExpenses, metadata, currentPeriod, currentCurrency),
            trendPoints = StatisticsCalculator.trendPoints(currentExpenses, currentRange),
            selectedCategory = category
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun reset(homeCurrency: String) {
        section.value = StatisticsSection.OVERVIEW
        period.value = StatisticsPeriod()
        trendRange.value = TrendRange.SIX_MONTHS
        currency.value = homeCurrency
        selectedCategory.value = null
    }

    fun selectSection(value: StatisticsSection) { section.value = value; selectedCategory.value = null }
    fun selectCurrency(value: String) { currency.value = value; selectedCategory.value = null }
    fun selectMonth(value: YearMonth) { period.value = StatisticsPeriod(month = value, year = value.year); selectedCategory.value = null }
    fun selectYear(value: Int) { period.value = StatisticsPeriod(StatisticsPeriodMode.YEAR, YearMonth.of(value, 1), value); selectedCategory.value = null }
    fun selectTrendRange(value: TrendRange) { trendRange.value = value }
    fun selectCategory(value: String?) { selectedCategory.value = value }
}

class StatisticsViewModelFactory(private val repository: StatisticsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) return StatisticsViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
