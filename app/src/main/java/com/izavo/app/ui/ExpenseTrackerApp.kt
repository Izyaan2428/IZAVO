package com.izavo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.preferences.CurrencyPreferencesRepository
import com.izavo.app.ui.history.HistoryV2Screen
import com.izavo.app.ui.detail.ExpenseDetailBottomSheet
import com.izavo.app.ui.detail.EditExpenseBottomSheet
import com.izavo.app.ui.home.HomeV22Screen
import com.izavo.app.ui.quickadd.QuickAddBottomSheet
import com.izavo.app.ui.onboarding.OnboardingScreen
import com.izavo.app.ui.settings.SettingsScreen
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import com.izavo.app.R
import com.izavo.app.importing.BankImportViewModel
import com.izavo.app.importing.ImportUiState
import com.izavo.app.ui.statistics.StatisticsScreen
import com.izavo.app.ui.statistics.StatisticsViewModel
import com.izavo.app.preferences.AppAppearance
import com.izavo.app.ui.design.premiumClick
import com.izavo.app.ui.design.IzavoMotion
import com.izavo.app.ui.design.IzavoMaterial
import com.izavo.app.ui.onboarding.OnboardingCompletion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.geometry.Offset
import java.time.LocalTime
import com.izavo.app.ui.home.HomeGreetingPose

internal enum class MainTab {
    HOME,
    STATISTICS,
    HISTORY,
    SETTINGS
}

private data class FirstHomeTransition(val name: String, val greeting: String)

@Composable
fun ExpenseTrackerApp(
    viewModel: ExpenseViewModel,
    importViewModel: BankImportViewModel,
    statisticsViewModel: StatisticsViewModel,
    quickAddRequests: StateFlow<Long>,
    currencyPreferencesRepository: CurrencyPreferencesRepository,
    darkTheme: Boolean,
    onThemeToggle: (Offset) -> Unit,
    onRequestQuickSettingsTile: () -> Boolean
) {
    val expenses by viewModel.expenses.collectAsState()
    val todayTotals by viewModel.todayTotals.collectAsState()
    val monthTotals by viewModel.monthTotals.collectAsState()
    val monthCategoryTotals by viewModel.monthCategoryTotals.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val quickAddRequestId by quickAddRequests.collectAsState()
    val currencyPreferences by currencyPreferencesRepository.state.collectAsState()
    val statisticsState by statisticsViewModel.state.collectAsState()
    val importState by importViewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    var firstHomeTransition by remember { mutableStateOf<FirstHomeTransition?>(null) }
    var homeGreetingPose by remember { mutableStateOf<HomeGreetingPose?>(null) }

    if (!currencyPreferences.onboardingCompleted) {
        Box(Modifier.fillMaxSize().background(ExpenseColors.Background)) {
        OnboardingScreen(currencyPreferences) { home, defaultCurrency, quickCurrencies, name ->
            if (firstHomeTransition == null) {
            val normalizedName = com.izavo.app.preferences.normalizeDisplayName(name)
            firstHomeTransition = FirstHomeTransition(
                normalizedName,
                com.izavo.app.preferences.homeGreeting(LocalTime.now().hour, normalizedName)
            )
            currencyPreferencesRepository.completeOnboarding(home, defaultCurrency, quickCurrencies, name)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        }
        return
    }

    var selectedTab by remember {
        mutableStateOf(MainTab.HOME)
    }

    var showQuickAdd by remember {
        mutableStateOf(false)
    }

    var selectedExpense by remember {
        mutableStateOf<ExpenseEntity?>(null)
    }

    var editingExpense by remember {
        mutableStateOf<ExpenseEntity?>(null)
    }

    var consumedQuickAddRequestId by rememberSaveable {
        mutableStateOf(0L)
    }

    LaunchedEffect(quickAddRequestId, firstHomeTransition) {
        if (firstHomeTransition == null && quickAddRequestId > consumedQuickAddRequestId) {
            consumedQuickAddRequestId = quickAddRequestId
            selectedExpense = null
            editingExpense = null
            showQuickAdd = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ExpenseColors.Background),
        containerColor = ExpenseColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (importState == ImportUiState.Idle) {
                ExpenseBottomNavigation(
                    selectedTab = selectedTab,
                    onSelect = { tab ->
                        if (selectedTab != tab) {
                            if (tab == MainTab.STATISTICS) statisticsViewModel.reset(currencyPreferences.homeCurrency)
                            selectedTab = tab
                        }
                    },
                    onAdd = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); showQuickAdd = true }
                )
            }
        }
    ) { _ ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ExpenseColors.Background)
        ) {
            AnimatedContent(selectedTab, modifier = Modifier.fillMaxSize().background(ExpenseColors.Background), transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(IzavoMotion.Screen)) + slideInHorizontally(tween(IzavoMotion.Screen)) { direction * 12 }) togetherWith
                    (fadeOut(tween(IzavoMotion.Control)) + slideOutHorizontally(tween(IzavoMotion.Screen)) { -direction * 12 })
            }, label = "destination") { tab ->
            when (tab) {
                MainTab.SETTINGS -> Box(Modifier.fillMaxSize().background(ExpenseColors.Background).statusBarsPadding()) { SettingsScreen(
                    importViewModel = importViewModel,
                    preferences = currencyPreferences,
                    expenses = expenses,
                    onBack = { selectedTab = MainTab.HOME },
                    showBack = false,
                    onHomeCurrencyChange = currencyPreferencesRepository::setHomeCurrency,
                    onDefaultCurrencyChange = currencyPreferencesRepository::setDefaultExpenseCurrency,
                    onQuickCurrenciesChange = currencyPreferencesRepository::setQuickCurrencies,
                    onShowSecondsChange = currencyPreferencesRepository::setShowSeconds,
                    onDisplayNameChange = currencyPreferencesRepository::setDisplayName,
                    onRequestQuickSettingsTile = onRequestQuickSettingsTile,
                    onMessage = { message -> coroutineScope.launch { snackbarHostState.showSnackbar(message) } }
                ) }
                MainTab.HOME -> {
                    HomeV22Screen(
                        monthTotals = monthTotals,
                        monthCategoryTotals = monthCategoryTotals,
                        expenses = expenses,
                        homeCurrency = currencyPreferences.homeCurrency,
                        displayName = currencyPreferences.displayName,
                        greetingOverride = firstHomeTransition?.greeting,
                        greetingVisible = firstHomeTransition == null,
                        onGreetingPositioned = { homeGreetingPose = it },
                        todayTotals = todayTotals,
                        darkTheme = darkTheme,
                        onThemeToggle = onThemeToggle,
                        onTrendClick = {
                            statisticsViewModel.reset(currencyPreferences.homeCurrency)
                            statisticsViewModel.selectSection(com.izavo.app.ui.statistics.StatisticsSection.TRENDS)
                            selectedTab = MainTab.STATISTICS
                        },
                        showSeconds = currencyPreferences.showSeconds,
                        onStatisticsClick = {
                            statisticsViewModel.reset(currencyPreferences.homeCurrency)
                            selectedTab = MainTab.STATISTICS
                        },
                        onViewAllClick = {
                            selectedTab = MainTab.HISTORY
                        },
                        onExpenseClick = {
                            selectedExpense = it
                        }
                    )
                }

                MainTab.STATISTICS -> Box(Modifier.fillMaxSize().background(ExpenseColors.Background).statusBarsPadding()) { StatisticsScreen(
                    state = statisticsState,
                    onSection = statisticsViewModel::selectSection,
                    onCurrency = statisticsViewModel::selectCurrency,
                    onMonth = statisticsViewModel::selectMonth,
                    onYear = statisticsViewModel::selectYear,
                    onRange = statisticsViewModel::selectTrendRange,
                    onCategory = statisticsViewModel::selectCategory,
                    onExpenseClick = { selectedExpense = it },
                    onAddExpense = { showQuickAdd = true },
                    onImport = { selectedTab = MainTab.SETTINGS }
                ) }

                MainTab.HISTORY -> Box(Modifier.fillMaxSize().background(ExpenseColors.Background).statusBarsPadding()) {
                    HistoryV2Screen(
                        expenses = expenses,
                        showSeconds = currencyPreferences.showSeconds,
                        onExpenseClick = {
                            selectedExpense = it
                        }
                    )
                }
            }
        }
    }

    }

    if (showQuickAdd) {
        QuickAddBottomSheet(
            defaultCurrency = currencyPreferences.defaultExpenseCurrency,
            quickCurrencies = currencyPreferences.quickCurrencies,
            showSeconds = currencyPreferences.showSeconds,
            onDismiss = {
                showQuickAdd = false
            },
            onSave = { amount, currencyCode, category, note, dateTime ->
                viewModel.addExpense(
                    amount = amount,
                    currencyCode = currencyCode,
                    category = category,
                    note = note,
                    dateTime = dateTime,
                    onInserted = { savedExpense ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Expense saved",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.deleteExpense(savedExpense)
                            }
                        }
                    }
                )
                showQuickAdd = false
            }
        )
    }

    selectedExpense?.let { expense ->
        ExpenseDetailBottomSheet(
            expense = expense,
            showSeconds = currencyPreferences.showSeconds,
            onDismiss = { selectedExpense = null },
            onEdit = {
                selectedExpense = null
                editingExpense = expense
            },
            onDelete = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.deleteExpense(expense)
                selectedExpense = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Expense deleted")
                }
            }
        )
    }

    editingExpense?.let { expense ->
        EditExpenseBottomSheet(
            expense = expense,
            showSeconds = currencyPreferences.showSeconds,
            onDismiss = {
                editingExpense = null
                selectedExpense = expense
            },
            onSave = { amount, currencyCode, category, note, dateTime ->
                viewModel.updateExpense(
                    originalExpense = expense,
                    amount = amount,
                    currencyCode = currencyCode,
                    category = category,
                    note = note,
                    dateTime = dateTime
                )
                editingExpense = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Expense updated")
                }
            }
        )
    }

    firstHomeTransition?.let { transition ->
        OnboardingCompletion(transition.greeting, homeGreetingPose) { firstHomeTransition = null }
    }
}

@Composable
internal fun ExpenseBottomNavigation(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    onAdd: () -> Unit
) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(ExpenseShapes.FloatingBar),
            color = IzavoMaterial.Navigation,
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseColors.GlassBorder.copy(alpha = .72f))
        ) {
            Box {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    BottomDestination(Modifier.weight(1f), Icons.Filled.Home, "Home", selectedTab == MainTab.HOME) { onSelect(MainTab.HOME) }
                    BottomDestination(Modifier.weight(1f), Icons.Outlined.Analytics, "Statistics", selectedTab == MainTab.STATISTICS) { onSelect(MainTab.STATISTICS) }
                    Spacer(Modifier.width(52.dp))
                    BottomDestination(Modifier.weight(1f), Icons.Filled.History, "History", selectedTab == MainTab.HISTORY) { onSelect(MainTab.HISTORY) }
                    BottomDestination(Modifier.weight(1f), Icons.Outlined.Settings, "Settings", selectedTab == MainTab.SETTINGS) { onSelect(MainTab.SETTINGS) }
                }
                Box(Modifier.fillMaxWidth(0.62f).height(1.dp).align(Alignment.TopCenter)
                    .background(ExpenseColors.GlassBorder.copy(alpha = .34f)))
                Surface(modifier = Modifier.size(48.dp).align(Alignment.Center).premiumClick(pressedScale = .94f, onClick = onAdd), shape = CircleShape,
                    color = ExpenseColors.Action.copy(alpha = .97f), contentColor = ExpenseColors.OnAction) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_figma_add), "Add Expense", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomDestination(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val indicator by animateFloatAsState(if (selected) 1f else 0f, tween(IzavoMotion.Control), label = "navIndicator")
    Column(
        modifier.padding(horizontal = 2.dp).semantics { this.selected = selected }
            .premiumClick(role = Role.Tab, pressedScale = .95f, onClick = onClick).height(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(icon, null, tint = if (selected) ExpenseColors.Ink else ExpenseColors.InkTertiary, modifier = Modifier.size(19.dp))
        Text(label, color = if (selected) ExpenseColors.Ink else ExpenseColors.InkTertiary,
            style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        Box(Modifier.padding(top = 2.dp).size(4.dp).graphicsLayer { alpha = indicator; scaleX = indicator; scaleY = indicator }.background(ExpenseColors.Cyan, CircleShape))
    }
}
