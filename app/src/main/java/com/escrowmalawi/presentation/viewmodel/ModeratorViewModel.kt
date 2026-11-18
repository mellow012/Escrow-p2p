package com.escrowmalawi.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.data.model.*
import com.escrowmalawi.data.repository.ModeratorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A list that may be loading, loaded, or failed. [data] is kept while reloading. */
data class Loadable<T>(val loading: Boolean = false, val data: T? = null, val error: String? = null)

@HiltViewModel
class ModeratorViewModel @Inject constructor(
    private val repo: ModeratorRepository
) : ViewModel() {

    private val _isModerator = MutableStateFlow<Boolean?>(null)
    /** null = still checking; used by the profile screen to decide whether to show the console. */
    val isModerator: StateFlow<Boolean?> = _isModerator.asStateFlow()

    private val _overview = MutableStateFlow(Loadable<ModOverview>())
    val overview: StateFlow<Loadable<ModOverview>> = _overview.asStateFlow()

    private val _disputes = MutableStateFlow(Loadable<List<ModDispute>>())
    val disputes: StateFlow<Loadable<List<ModDispute>>> = _disputes.asStateFlow()

    private val _users = MutableStateFlow(Loadable<ModUsersData>())
    val users: StateFlow<Loadable<ModUsersData>> = _users.asStateFlow()

    private val _verifications = MutableStateFlow(Loadable<List<ModVerification>>())
    val verifications: StateFlow<Loadable<List<ModVerification>>> = _verifications.asStateFlow()

    private val _userTrades = MutableStateFlow<Map<String, Loadable<ModUserTrades>>>(emptyMap())
    val userTrades: StateFlow<Map<String, Loadable<ModUserTrades>>> = _userTrades.asStateFlow()

    /** Keys of actions in progress, e.g. "analyze:<tradeId>", "resolve:<tradeId>", "verify:<uid>". */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun checkAccess() {
        viewModelScope.launch { _isModerator.value = repo.isModerator() }
    }

    // ── Loaders ──────────────────────────────────────────────────────────
    fun loadOverview() = load(_overview) { repo.overview() }
    fun loadDisputes() = load(_disputes) { repo.disputes() }
    fun loadUsers() = load(_users) { repo.users() }
    fun loadVerifications() = load(_verifications) { repo.verifications() }

    private fun <T> load(state: MutableStateFlow<Loadable<T>>, call: suspend () -> Result<T>) {
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            val result = call()
            state.update {
                if (result.isSuccess) Loadable(false, result.getOrNull(), null)
                else it.copy(loading = false, error = result.exceptionOrNull()?.message ?: "Failed to load")
            }
        }
    }

    fun loadUserTrades(uid: String) {
        if (_userTrades.value[uid]?.data != null || _userTrades.value[uid]?.loading == true) return
        viewModelScope.launch {
            _userTrades.update { it + (uid to Loadable<ModUserTrades>(loading = true)) }
            val result = repo.userTrades(uid)
            val next: Loadable<ModUserTrades> =
                if (result.isSuccess) Loadable(false, result.getOrNull(), null)
                else Loadable(false, null, result.exceptionOrNull()?.message ?: "Failed to load trades")
            _userTrades.update { it + (uid to next) }
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────
    private fun act(key: String, work: suspend () -> Unit) {
        if (key in _busy.value) return
        viewModelScope.launch {
            _busy.update { it + key }
            try { work() } finally { _busy.update { it - key } }
        }
    }

    fun analyze(tradeId: String) = act("analyze:$tradeId") {
        val r = repo.analyze(tradeId)
        if (r.isFailure) _message.value = r.exceptionOrNull()?.message ?: "AI analysis failed"
        repo.disputes().onSuccess { list -> _disputes.update { it.copy(data = list) } }
    }

    fun resolve(tradeId: String, outcome: String, note: String) = act("resolve:$tradeId") {
        val r = repo.resolve(tradeId, outcome, note)
        _message.value = if (r.isSuccess) "Dispute resolved on-chain" else (r.exceptionOrNull()?.message ?: "Could not resolve")
        repo.disputes().onSuccess { list -> _disputes.update { it.copy(data = list) } }
        if (r.isSuccess) loadOverview()
    }

    fun setVerified(uid: String, verified: Boolean) = act("verify:$uid") {
        val r = repo.setVerified(uid, verified)
        _message.value = if (r.isSuccess) (if (verified) "Marked as verified" else "Badge removed") else (r.exceptionOrNull()?.message ?: "Update failed")
        repo.users().onSuccess { data -> _users.update { it.copy(data = data) } }
    }

    fun review(uid: String, approve: Boolean, reason: String) = act("review:$uid") {
        val r = repo.reviewVerification(uid, approve, reason)
        _message.value = if (r.isSuccess) (if (approve) "Approved" else "Rejected") else (r.exceptionOrNull()?.message ?: "Update failed")
        repo.verifications().onSuccess { list -> _verifications.update { it.copy(data = list) } }
    }
}
