package com.escrowmalawi.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.data.model.Follow
import com.escrowmalawi.data.repository.AuthRepository
import com.escrowmalawi.data.repository.FollowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FollowViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    /** Sellers I follow (live, newest first). */
    val follows: StateFlow<List<Follow>> = flow {
        val uid = authRepository.currentFirebaseUser()?.uid ?: return@flow
        emitAll(followRepository.myFollowsFlow(uid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedUids: StateFlow<Set<String>> = follows
        .map { list -> list.map { it.sellerUid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Follow if not following, unfollow otherwise. The live list updates the buttons straight away. */
    fun toggle(sellerUid: String, sellerName: String) {
        viewModelScope.launch {
            val uid = authRepository.currentFirebaseUser()?.uid ?: return@launch
            if (uid == sellerUid) return@launch
            if (sellerUid in followedUids.value) followRepository.unfollow(uid, sellerUid)
            else followRepository.follow(uid, sellerUid, sellerName)
        }
    }

    fun unfollow(sellerUid: String) {
        viewModelScope.launch {
            val uid = authRepository.currentFirebaseUser()?.uid ?: return@launch
            followRepository.unfollow(uid, sellerUid)
        }
    }

    suspend fun followerCount(sellerUid: String): Int = followRepository.followerCount(sellerUid)
}
