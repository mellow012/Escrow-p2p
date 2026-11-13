package com.escrowmalawi.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.data.model.Dispute
import com.escrowmalawi.data.repository.AuthRepository
import com.escrowmalawi.data.repository.DisputeRepository
import com.escrowmalawi.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DisputeUiState {
    data object Idle : DisputeUiState
    data class Working(val step: String) : DisputeUiState
    data class Error(val message: String) : DisputeUiState
    data object Done : DisputeUiState
}

@HiltViewModel
class DisputeViewModel @Inject constructor(
    private val disputeRepository: DisputeRepository,
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DisputeUiState>(DisputeUiState.Idle)
    val uiState: StateFlow<DisputeUiState> = _uiState.asStateFlow()

    fun disputeFlow(tradeId: String): Flow<Dispute?> = disputeRepository.disputeFlow(tradeId)

    /**
     * Uploads the chosen photos, then either opens the dispute ([opening] = true)
     * or adds this user's side to an existing one.
     */
    fun submit(
        context: Context,
        tradeId: String,
        opening: Boolean,
        statement: String,
        photos: List<Uri>,
        keepEvidence: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val uid = authRepository.currentFirebaseUser()?.uid
            if (uid == null) {
                _uiState.value = DisputeUiState.Error("You are signed out")
                return@launch
            }

            val urls = keepEvidence.toMutableList()
            photos.forEachIndexed { i, uri ->
                _uiState.value = DisputeUiState.Working("Uploading photo ${i + 1} of ${photos.size}…")
                val up = storageRepository.uploadEvidenceImage(context, tradeId, uri)
                if (up.isFailure) {
                    _uiState.value = DisputeUiState.Error(
                        "Photo ${i + 1} failed to upload: ${up.exceptionOrNull()?.message ?: "check your connection"}"
                    )
                    return@launch
                }
                urls += up.getOrThrow()
            }

            _uiState.value = DisputeUiState.Working(if (opening) "Opening dispute…" else "Sending your statement…")
            val result = if (opening) disputeRepository.openDispute(tradeId, uid, statement.trim(), urls)
            else disputeRepository.submitStatement(tradeId, uid, statement.trim(), urls)

            _uiState.value = if (result.isSuccess) DisputeUiState.Done
            else DisputeUiState.Error(result.exceptionOrNull()?.message ?: "Something went wrong")
        }
    }

    fun resetState() { _uiState.value = DisputeUiState.Idle }
}
