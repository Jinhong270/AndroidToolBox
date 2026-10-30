package com.jh270.toolbox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jh270.toolbox.domain.DomainLookupClient
import com.jh270.toolbox.domain.DomainRecord
import com.jh270.toolbox.domain.LookupOutcome
import com.jh270.toolbox.domain.PlatformLookupTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DomainLookupUiState(
    val input: String = "",
    val loading: Boolean = false,
    val record: DomainRecord? = null,
    val message: String? = null,
    val error: Boolean = false,
    val recent: List<String> = emptyList()
)

class DomainLookupViewModel(app: Application) : AndroidViewModel(app) {
    private val client = DomainLookupClient(PlatformLookupTransport())
    private val _uiState = MutableStateFlow(DomainLookupUiState())
    val uiState: StateFlow<DomainLookupUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, message = null, error = false) }
    }

    fun lookup(raw: String = _uiState.value.input) {
        val text = raw.trim()
        if (text.isEmpty()) {
            _uiState.update {
                it.copy(input = "", loading = false, record = null, message = "请输入域名", error = true)
            }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update {
                it.copy(input = text, loading = true, record = null, message = null, error = false)
            }
            val outcome = withContext(Dispatchers.IO) { client.lookup(text) }
            if (!isActive) return@launch
            _uiState.update { state ->
                when (outcome) {
                    is LookupOutcome.Success -> state.copy(
                        loading = false,
                        record = outcome.record,
                        input = outcome.record.unicodeName,
                        message = null,
                        error = false,
                        recent = (listOf(outcome.record.unicodeName) + state.recent).distinct().take(8)
                    )
                    is LookupOutcome.NotFound -> state.copy(
                        loading = false,
                        record = null,
                        message = "注册局没有 ${outcome.domain} 的注册记录",
                        error = false
                    )
                    is LookupOutcome.Invalid -> state.copy(
                        loading = false,
                        record = null,
                        message = outcome.message,
                        error = true
                    )
                    is LookupOutcome.Failure -> state.copy(
                        loading = false,
                        record = null,
                        message = outcome.message,
                        error = true
                    )
                }
            }
        }
    }
}
