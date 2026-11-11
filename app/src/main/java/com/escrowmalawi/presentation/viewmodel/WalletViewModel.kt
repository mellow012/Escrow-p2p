package com.escrowmalawi.presentation.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.escrowmalawi.blockchain.EthereumService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reads the user's Sepolia ETH balance so they can see whether they can pay gas. */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val ethereum: EthereumService
) : ViewModel() {

    private val _balance = MutableStateFlow("…")
    val balance: StateFlow<String> = _balance.asStateFlow()

    fun refresh(address: String) {
        if (address.isBlank()) {
            _balance.value = "-"
            return
        }
        viewModelScope.launch {
            _balance.value = "…"
            _balance.value = ethereum.getBalanceEth(address).fold(
                onSuccess = { "$it ETH" },
                onFailure = { "unavailable" }
            )
        }
    }
}
