package com.izavo.app.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Reader

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Review(val prepared: PreparedImport) : ImportUiState
    data class ApplyingDecision(val prepared: PreparedImport, val groupId: String) : ImportUiState
    data class Complete(val summary: ImportResultSummary) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class BankImportViewModel(private val repository: BankImportRepository) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private val submissionGuard = ImportSubmissionGuard()
    private val decisionGuard = ImportSubmissionGuard()
    private val adaptiveReviewEngine = AdaptiveReviewEngine()
    private val reviewQueueOptimizer = ReviewQueueOptimizer()

    fun scan(fileName: String, openReader: () -> Reader) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = openReader().use { BmlCsvParser().parse(it) }
                if (parsed.candidates.isEmpty()) {
                    _state.value = ImportUiState.Error(parsed.errors.firstOrNull() ?: "No supported BML transactions were found")
                } else {
                    _state.value = ImportUiState.Review(repository.prepare(fileName, parsed))
                }
            } catch (_: SecurityException) {
                _state.value = ImportUiState.Error("IZAVO could not read this file")
            } catch (_: Exception) {
                _state.value = ImportUiState.Error("This does not appear to be a supported BML CSV")
            }
        }
    }

    fun setDecision(fingerprint: String, decision: ImportDecision) = updateItem(fingerprint) { it.copy(decision = decision) }
    fun setCategory(fingerprint: String, category: String?) = updateItem(fingerprint) { it.copy(category = category) }

    fun resolveGroup(groupId: String, decision: ImportDecision, category: String?, remember: Boolean) {
        val prepared = (_state.value as? ImportUiState.Review)?.prepared ?: return
        if (!decisionGuard.tryStart()) return
        _state.value = ImportUiState.ApplyingDecision(prepared, groupId)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val adapted = adaptiveReviewEngine.resolve(prepared, groupId, decision, category, remember)
                _state.value = ImportUiState.Review(reviewQueueOptimizer.optimize(adapted))
            } catch (_: Exception) {
                _state.value = ImportUiState.Review(prepared)
            } finally {
                decisionGuard.finish()
            }
        }
    }

    fun setGroupRemember(groupId: String, remember: Boolean) {
        updatePrepared { prepared ->
            prepared.copy(reviewGroups = prepared.reviewGroups.map {
                if (it.id == groupId) it.copy(rememberRule = remember && it.rememberEligible) else it
            })
        }
    }

    fun reopenGroup(groupId: String) {
        updatePrepared { prepared ->
            prepared.copy(reviewGroups = prepared.reviewGroups.map {
                if (it.id == groupId) it.copy(resolved = false, items = it.items.map { item -> item.copy(resolved = false) }) else it
            })
        }
    }

    fun confirmImport() {
        val prepared = (_state.value as? ImportUiState.Review)?.prepared ?: return
        if (!submissionGuard.tryStart()) return
        _state.value = ImportUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try { _state.value = ImportUiState.Complete(repository.import(prepared)) }
            catch (_: Exception) { _state.value = ImportUiState.Error("The import could not be saved. Your existing expenses were not changed.") }
            finally { submissionGuard.finish() }
        }
    }

    fun deferRemainingReview() {
        val prepared = (_state.value as? ImportUiState.Review)?.prepared ?: return
        if (!decisionGuard.tryStart()) return
        _state.value = ImportUiState.Review(reviewQueueOptimizer.deferRemaining(prepared))
        decisionGuard.finish()
    }

    fun close() { _state.value = ImportUiState.Idle }

    private fun updateItem(fingerprint: String, transform: (ImportReviewItem) -> ImportReviewItem) {
        val review = _state.value as? ImportUiState.Review ?: return
        _state.value = review.copy(prepared = review.prepared.copy(items = review.prepared.items.map {
            if (it.transaction.fingerprint == fingerprint) transform(it) else it
        }))
    }

    private fun updatePrepared(transform: (PreparedImport) -> PreparedImport) {
        val review = _state.value as? ImportUiState.Review ?: return
        _state.value = review.copy(prepared = transform(review.prepared))
    }

}

internal class ImportSubmissionGuard {
    private var inFlight = false
    @Synchronized fun tryStart(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }
    @Synchronized fun finish() { inFlight = false }
}

class BankImportViewModelFactory(private val repository: BankImportRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BankImportViewModel::class.java)) return BankImportViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
