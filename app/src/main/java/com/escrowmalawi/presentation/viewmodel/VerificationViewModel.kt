package com.escrowmalawi.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.data.model.Verification
import com.escrowmalawi.data.repository.AuthRepository
import com.escrowmalawi.data.repository.StorageRepository
import com.escrowmalawi.data.repository.VerificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VerifyUiState {
    data object Idle : VerifyUiState
    data class Working(val step: String) : VerifyUiState
    data class Error(val message: String) : VerifyUiState
}

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificationRepository: VerificationRepository,
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<VerifyUiState>(VerifyUiState.Idle)
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    /** null while loading; a Verification with a blank status means "never submitted". */
    val verification: StateFlow<Verification?> = flow {
        val uid = authRepository.currentFirebaseUser()?.uid
        if (uid == null) {
            emit(Verification())
        } else {
            emitAll(verificationRepository.statusFlow(uid).map { it ?: Verification() })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Uploads both photos, then files the request as "pending". The live flow updates the screen. */
    fun submit(context: Context, idPhoto: Uri, selfie: Uri) {
        viewModelScope.launch {
            val uid = authRepository.currentFirebaseUser()?.uid
            if (uid == null) {
                _uiState.value = VerifyUiState.Error("You are signed out")
                return@launch
            }
            _uiState.value = VerifyUiState.Working("Uploading your ID photo…")
            val id = storageRepository.uploadVerificationImage(context, uid, idPhoto)
            if (id.isFailure) {
                _uiState.value = VerifyUiState.Error("ID photo failed to upload: ${id.exceptionOrNull()?.message ?: "check your connection"}")
                return@launch
            }
            _uiState.value = VerifyUiState.Working("Uploading your selfie…")
            val face = storageRepository.uploadVerificationImage(context, uid, selfie)
            if (face.isFailure) {
                _uiState.value = VerifyUiState.Error("Selfie failed to upload: ${face.exceptionOrNull()?.message ?: "check your connection"}")
                return@launch
            }
            _uiState.value = VerifyUiState.Working("Sending for review…")
            val name = authRepository.currentUserProfile()?.name.orEmpty()
            val result = verificationRepository.submit(uid, name, id.getOrThrow(), face.getOrThrow())
            _uiState.value = if (result.isSuccess) VerifyUiState.Idle
            else VerifyUiState.Error(result.exceptionOrNull()?.message ?: "Could not submit")
        }
    }

    fun clearError() { _uiState.value = VerifyUiState.Idle }
}
