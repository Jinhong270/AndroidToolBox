package com.jh270.toolbox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jh270.toolbox.R
import com.jh270.toolbox.domain.DomainLookupClient
import com.jh270.toolbox.domain.DomainNames
import com.jh270.toolbox.domain.DomainRecord
import com.jh270.toolbox.domain.LookupOutcome
import com.jh270.toolbox.domain.LookupProblem
import com.jh270.toolbox.domain.PlatformLookupTransport
import kotlinx.coroutines.CancellationException
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

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun nameMessage(problem: DomainNames.NameProblem): String {
        val id = when (problem) {
            DomainNames.NameProblem.EMPTY -> R.string.err_domain_empty
            DomainNames.NameProblem.SPACE -> R.string.err_domain_space
            DomainNames.NameProblem.IP -> R.string.err_domain_ip
            DomainNames.NameProblem.BAD_CHARS -> R.string.err_domain_chars
            DomainNames.NameProblem.TOO_LONG -> R.string.err_domain_long
            DomainNames.NameProblem.INCOMPLETE -> R.string.err_domain_incomplete
        }
        return text(id)
    }

    private fun failureMessage(problem: LookupProblem, detail: String): String {
        return when (problem) {
            LookupProblem.RATE_LIMIT -> text(R.string.err_rate_limit)
            LookupProblem.FORBIDDEN -> text(R.string.err_forbidden)
            LookupProblem.IANA_DOWN -> text(R.string.err_iana)
            LookupProblem.NO_WHOIS -> text(R.string.err_no_whois)
            LookupProblem.REGISTRY_DOWN -> text(R.string.err_registry, detail)
        }
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, message = null, error = false) }
    }

    fun lookup(raw: String = _uiState.value.input) {
        val query = raw.trim()
        if (query.isEmpty()) {
            _uiState.update {
                it.copy(input = "", loading = false, record = null, message = text(R.string.err_domain_empty), error = true)
            }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update {
                it.copy(input = query, loading = true, record = null, message = null, error = false)
            }
            val outcome: LookupOutcome? = try {
                withContext(Dispatchers.IO) { client.lookup(query) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!isActive) return@launch
            _uiState.update { state ->
                when (outcome) {
                    null -> state.copy(
                        loading = false,
                        record = null,
                        message = text(R.string.err_connect_unknown),
                        error = true
                    )
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
                        message = text(R.string.err_not_registered, outcome.domain),
                        error = false
                    )
                    is LookupOutcome.Invalid -> state.copy(
                        loading = false,
                        record = null,
                        message = nameMessage(outcome.problem),
                        error = true
                    )
                    is LookupOutcome.Failure -> state.copy(
                        loading = false,
                        record = null,
                        message = failureMessage(outcome.problem, outcome.detail),
                        error = true
                    )
                }
            }
        }
    }
}
