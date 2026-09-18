package com.izavo.app

import android.content.Intent
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.izavo.app.data.ExpenseDatabase
import com.izavo.app.data.ExpenseRepository
import com.izavo.app.importing.BankImportRepository
import com.izavo.app.importing.BankImportViewModel
import com.izavo.app.importing.BankImportViewModelFactory
import com.izavo.app.quicksettings.ACTION_OPEN_QUICK_ADD
import com.izavo.app.quicksettings.QuickAddRequestController
import com.izavo.app.quicksettings.QuickAddTileService
import com.izavo.app.preferences.CurrencyPreferencesRepository
import com.izavo.app.ui.ExpenseTrackerApp
import com.izavo.app.ui.ExpenseViewModel
import com.izavo.app.ui.ExpenseViewModelFactory
import com.izavo.app.ui.statistics.StatisticsRepository
import com.izavo.app.ui.statistics.StatisticsViewModel
import com.izavo.app.ui.statistics.StatisticsViewModelFactory
import com.izavo.app.ui.theme.ExpenseTrackerTheme
import com.izavo.app.ui.theme.ThemeRevealHost
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.izavo.app.preferences.AppAppearance

class MainActivity : ComponentActivity() {

    private val quickAddRequestController = QuickAddRequestController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            handleQuickAddIntent(intent)
        }

        enableEdgeToEdge()

        val database =
            ExpenseDatabase.getDatabase(
                applicationContext
            )

        val repository =
            ExpenseRepository(
                database.expenseDao()
            )

        val viewModel =
            ViewModelProvider(
                this,
                ExpenseViewModelFactory(repository)
            )[ExpenseViewModel::class.java]

        val importViewModel = ViewModelProvider(
            this,
            BankImportViewModelFactory(BankImportRepository(database.importDao()))
        )[BankImportViewModel::class.java]

        val statisticsViewModel = ViewModelProvider(
            this,
            StatisticsViewModelFactory(StatisticsRepository(repository, database.importDao()))
        )[StatisticsViewModel::class.java]

        val currencyPreferencesRepository = CurrencyPreferencesRepository(applicationContext)

        setContent {
            val preferences by currencyPreferencesRepository.state.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (preferences.appearance) {
                AppAppearance.DARK -> true
                AppAppearance.LIGHT -> false
                AppAppearance.SYSTEM -> systemDark
            }
            ExpenseTrackerTheme(darkTheme = darkTheme) {
                ThemeRevealHost(onCommitTheme = {
                    currencyPreferencesRepository.setAppearance(if (darkTheme) AppAppearance.LIGHT else AppAppearance.DARK)
                }) { requestThemeReveal -> ExpenseTrackerApp(
                    viewModel = viewModel,
                    importViewModel = importViewModel,
                    statisticsViewModel = statisticsViewModel,
                    quickAddRequests = quickAddRequestController.requests,
                    currencyPreferencesRepository = currencyPreferencesRepository,
                    darkTheme = darkTheme,
                    onThemeToggle = requestThemeReveal,
                    onRequestQuickSettingsTile = ::requestQuickSettingsTile
                ) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickAddIntent(intent)
    }

    private fun handleQuickAddIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_QUICK_ADD) return

        quickAddRequestController.requestQuickAdd()

        // Consume the action so activity recreation cannot reopen the sheet.
        setIntent(Intent(intent).setAction(null))
    }

    private fun requestQuickSettingsTile(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false

        val statusBarManager = getSystemService(StatusBarManager::class.java) ?: return false
        statusBarManager.requestAddTileService(
            ComponentName(this, QuickAddTileService::class.java),
            getString(R.string.quick_add_tile_label),
            Icon.createWithResource(this, R.drawable.ic_quick_add_tile),
            mainExecutor
        ) { }
        return true
    }
}
