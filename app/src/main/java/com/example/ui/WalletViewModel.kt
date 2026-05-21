package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChannelEntity
import com.example.data.ChannelOpenResult
import com.example.data.PaymentResult
import com.example.data.TransactionEntity
import com.example.data.WalletDatabase
import com.example.data.WalletRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WalletRepository

    init {
        val database = WalletDatabase.getDatabase(application)
        repository = WalletRepository(database.channelDao(), database.transactionDao(), application)
        
        // Ensure default channels and activity exist
        viewModelScope.launch {
            repository.prepopulateIfEmpty()
        }
    }

    // UI events flow (e.g. success/error messages toast)
    private val _eventFlow = MutableSharedFlow<WalletUiEvent>()
    val eventFlow: SharedFlow<WalletUiEvent> = _eventFlow.asSharedFlow()

    // Base flows from repository
    val channels: StateFlow<List<ChannelEntity>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onChainBalance: StateFlow<Long> = repository.onChainBalanceSats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100000L)

    val nodeAlias: StateFlow<String> = repository.nodeAlias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "⚡️ ZeusMobileApp")

    val nodeUri: StateFlow<String> = repository.nodeUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Computed states (Aggregate balances)
    val lightningBalance: StateFlow<Long> = channels.map { list ->
        list.filter { it.isActive }.sumOf { it.localBalanceSats }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val inboundCapacity: StateFlow<Long> = channels.map { list ->
        list.filter { it.isActive }.sumOf { it.remoteBalanceSats }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalBalance: StateFlow<Long> = combine(lightningBalance, onChainBalance) { ln, onchain ->
        ln + onchain
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100000L)

    // Interactive preferences
    private val _isFiatMode = MutableStateFlow(false)
    val isFiatMode: StateFlow<Boolean> = _isFiatMode.asStateFlow()

    // Exchange rate placeholder (1 BTC = $93,450 USD in 2026!)
    private val satToUsdRate = 0.0009345 // 1 Sat = ~0.0009345 USD

    fun toggleFiatMode() {
        _isFiatMode.value = !_isFiatMode.value
    }

    fun formatBalance(sats: Long): String {
        return if (_isFiatMode.value) {
            val usd = sats * satToUsdRate
            String.format(Locale.US, "$%,.2f", usd)
        } else {
            String.format(Locale.US, "%,d sat", sats)
        }
    }

    fun satsToFiatString(sats: Long): String {
        val usd = sats * satToUsdRate
        return String.format(Locale.US, "$%,.2f USD", usd)
    }

    // Interactive operations
    fun sendLightningPayment(invoice: String, amount: Long, description: String) {
        viewModelScope.launch {
            if (invoice.isBlank() || !invoice.lowercase().startsWith("lnbc")) {
                _eventFlow.emit(WalletUiEvent.Error("Invalid Lightning Invoice format. Must start with 'lnbc'."))
                return@launch
            }
            if (amount <= 0) {
                _eventFlow.emit(WalletUiEvent.Error("Payment amount must be greater than 0"))
                return@launch
            }

            when (val result = repository.sendPayment(invoice, amount, description)) {
                is PaymentResult.Success -> {
                    _eventFlow.emit(WalletUiEvent.Success("Sent ${formatBalance(result.amountSats)} (Fee: ${result.feeSats} sat)"))
                }
                is PaymentResult.Failure -> {
                    _eventFlow.emit(WalletUiEvent.Error(result.message))
                }
            }
        }
    }

    fun receiveLightningPayment(amount: Long, description: String) {
        viewModelScope.launch {
            if (amount <= 0) {
                _eventFlow.emit(WalletUiEvent.Error("Receive amount must be greater than 0"))
                return@launch
            }

            when (val result = repository.receivePayment(amount, description)) {
                is PaymentResult.Success -> {
                    _eventFlow.emit(WalletUiEvent.Success("Received ${formatBalance(result.amountSats)}!"))
                }
                is PaymentResult.Failure -> {
                    _eventFlow.emit(WalletUiEvent.Error(result.message))
                }
            }
        }
    }

    fun openChannel(peerName: String, peerUri: String, capacity: Long, localFunding: Long) {
        viewModelScope.launch {
            val pName = peerName.ifEmpty { "Default LSP Peer" }
            val pUri = peerUri.ifEmpty { "02e1cd...129@peer.lightning.network:9735" }

            when (val result = repository.openChannel(pName, pUri, capacity, localFunding)) {
                is ChannelOpenResult.Success -> {
                    _eventFlow.emit(WalletUiEvent.Success("Successfully funded channel with $pName!"))
                }
                is ChannelOpenResult.Failure -> {
                    _eventFlow.emit(WalletUiEvent.Error(result.message))
                }
            }
        }
    }

    fun closeChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.closeChannel(channel.id, channel.localBalanceSats, channel.peerName)
            _eventFlow.emit(WalletUiEvent.Success("Closed channel with ${channel.peerName}. Funded ${formatBalance(channel.localBalanceSats)} back to On-Chain!"))
        }
    }

    fun requestFaucetFunds() {
        viewModelScope.launch {
            repository.requestOnChainFaucet(50000L) // Add 50,000 sats
            _eventFlow.emit(WalletUiEvent.Success("Earned 50,000 sats from Testnet Faucet!"))
        }
    }

    fun simulateRoutingActivity() {
        viewModelScope.launch {
            val routed = repository.simulateRoutingTransaction()
            if (routed) {
                _eventFlow.emit(WalletUiEvent.Success("Earned passive Routing Fees! Check your ledger."))
            } else {
                _eventFlow.emit(WalletUiEvent.Error("Failed to route. You need at least 2 active channels with reciprocal capacities."))
            }
        }
    }

    fun resetWallet() {
        viewModelScope.launch {
            repository.resetWallet()
            _eventFlow.emit(WalletUiEvent.Success("Wallet state has been completely re-indexed!"))
        }
    }
}

sealed class WalletUiEvent {
    data class Success(val message: String) : WalletUiEvent()
    data class Error(val message: String) : WalletUiEvent()
}

class WalletViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WalletViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
