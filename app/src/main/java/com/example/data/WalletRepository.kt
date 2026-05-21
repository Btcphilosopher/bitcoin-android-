package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

class WalletRepository(
    private val channelDao: ChannelDao,
    private val transactionDao: TransactionDao,
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

    // Flow lists
    val allChannels: Flow<List<ChannelEntity>> = channelDao.getAllChannelsFlow()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactionsFlow()

    // On-chain balance persistence
    private val _onChainBalanceSats = MutableStateFlow(prefs.getLong("on_chain_balance", 100000L)) // Default 100k sats
    val onChainBalanceSats: StateFlow<Long> = _onChainBalanceSats.asStateFlow()

    // Node state parameters
    private val _nodeAlias = MutableStateFlow("⚡️ ZeusMobileApp")
    val nodeAlias: StateFlow<String> = _nodeAlias.asStateFlow()

    private val _nodeUri = MutableStateFlow("03a89078af...928cd@142.250.200.14:9735")
    val nodeUri: StateFlow<String> = _nodeUri.asStateFlow()

    // Set interactive on-chain balance
    private fun updateOnChainBalance(newBalance: Long) {
        prefs.edit().putLong("on_chain_balance", newBalance).apply()
        _onChainBalanceSats.value = newBalance
    }

    // Add faucet funds (On-chain)
    suspend fun requestOnChainFaucet(amount: Long) {
        updateOnChainBalance(_onChainBalanceSats.value + amount)
        transactionDao.insertTransaction(
            TransactionEntity(
                type = "ON_CHAIN_RECEIVE",
                amountSats = amount,
                description = "Faucet On-Chain Funding",
                status = "SUCCESS",
                preimage = UUID.randomUUID().toString().replace("-", "").take(32)
            )
        )
    }

    // Prepopulate default high-fidelity data if empty
    suspend fun prepopulateIfEmpty() {
        val channels = channelDao.getAllChannels()
        if (channels.isEmpty()) {
            // Channel 1: ACINQ (Active, nicely balanced)
            channelDao.insertChannel(
                ChannelEntity(
                    peerName = "ACINQ Node",
                    peerNodeUri = "03864ef025fde8fb587d...3721adc@acinq.co:9735",
                    capacitySats = 500000,
                    localBalanceSats = 345000,
                    remoteBalanceSats = 155000,
                    isActive = true
                )
            )
            // Channel 2: Blockstream Greenlight (Active, received a lot)
            channelDao.insertChannel(
                ChannelEntity(
                    peerName = "Greenlight-Blockstream",
                    peerNodeUri = "02fa0547bbd223a411cf...55e09f2@greenlight.io:9735",
                    capacitySats = 250000,
                    localBalanceSats = 42000,
                    remoteBalanceSats = 208000,
                    isActive = true
                )
            )
            // Channel 3: Routing Hub (Pending/Opening)
            channelDao.insertChannel(
                ChannelEntity(
                    peerName = "Kraken Lightning",
                    peerNodeUri = "02790b9a35f400f07fa1...eed98ba@kraken.com:9735",
                    capacitySats = 1000000,
                    localBalanceSats = 1000000,
                    remoteBalanceSats = 0,
                    isActive = false // Pending
                )
            )

            // Insert initial transaction history to make UI look organic and professional on startup
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "RECEIVE",
                    amountSats = 21000,
                    description = "Coffee reimbursement",
                    status = "SUCCESS",
                    timestamp = System.currentTimeMillis() - 86400000 * 2,
                    preimage = "a55de0fb4ad5cb7e3240e106ac18cf03"
                )
            )
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "SEND",
                    amountSats = 3500,
                    description = "Lunch at Bitcoin Bistro",
                    status = "SUCCESS",
                    timestamp = System.currentTimeMillis() - 86400000,
                    feeSats = 2,
                    invoice = "lnbc35u1pjxywqr..."
                )
            )
        }
    }

    /**
     * Sends a lightning payment (deducts from a channel's local balance and adds to remote balance)
     */
    suspend fun sendPayment(invoice: String, amountSats: Long, description: String): PaymentResult {
        if (amountSats <= 0) {
            return PaymentResult.Failure("Amount must be positive")
        }

        val channels = channelDao.getAllChannels().filter { it.isActive }
        // Find a channel with sufficient local balance to send this payment (including safety buffer for fees)
        val feeEstimate = (1.0 + amountSats * 0.0005).toLong() // Base fee 1 sat + 0.05%
        val totalDebit = amountSats + feeEstimate

        val selectedChannel = channels.find { it.localBalanceSats >= totalDebit }
            ?: return PaymentResult.Failure("Insufficient outbound liquidity in your channels (Limit: ${channels.maxOfOrNull { it.localBalanceSats } ?: 0} sats)")

        // Update database (move balance horizontally inside the channel)
        val newLocal = selectedChannel.localBalanceSats - totalDebit
        val newRemote = selectedChannel.remoteBalanceSats + amountSats // remainder stays as fee or channel split

        channelDao.updateChannelBalances(selectedChannel.id, newLocal, newRemote)

        // Log transaction
        val transaction = TransactionEntity(
            type = "SEND",
            amountSats = amountSats,
            description = description.ifEmpty { "Lightning Payment" },
            status = "SUCCESS",
            feeSats = feeEstimate,
            invoice = invoice,
            preimage = UUID.randomUUID().toString().replace("-", "").take(32)
        )
        transactionDao.insertTransaction(transaction)

        return PaymentResult.Success(amountSats, feeEstimate)
    }

    /**
     * Receives a payment (adds to channel's local balance, deducts from remote balance)
     */
    suspend fun receivePayment(amountSats: Long, description: String): PaymentResult {
        if (amountSats <= 0) {
            return PaymentResult.Failure("Amount must be positive")
        }

        val channels = channelDao.getAllChannels().filter { it.isActive }
        // To receive, a channel must have enough remote balance (inbound liquidity)
        val selectedChannel = channels.find { it.remoteBalanceSats >= amountSats }
            ?: return PaymentResult.Failure("Insufficient inbound capacity. Open a new channel or spend sats first.")

        // Update balances
        val newLocal = selectedChannel.localBalanceSats + amountSats
        val newRemote = selectedChannel.remoteBalanceSats - amountSats

        channelDao.updateChannelBalances(selectedChannel.id, newLocal, newRemote)

        // Log transaction
        val transaction = TransactionEntity(
            type = "RECEIVE",
            amountSats = amountSats,
            description = description.ifEmpty { "Received Invoice Payment" },
            status = "SUCCESS",
            preimage = UUID.randomUUID().toString().replace("-", "").take(32)
        )
        transactionDao.insertTransaction(transaction)

        return PaymentResult.Success(amountSats, 0)
    }

    /**
     * Opens a new channel using onchain funds
     */
    suspend fun openChannel(peerName: String, peerUri: String, capacitySats: Long, initialLocalFunding: Long): ChannelOpenResult {
        if (capacitySats < 20000) {
            return ChannelOpenResult.Failure("Channel capacity must be at least 20,000 sats")
        }
        if (initialLocalFunding > capacitySats) {
            return ChannelOpenResult.Failure("Initial funding cannot exceed capacity")
        }
        if (_onChainBalanceSats.value < initialLocalFunding) {
            return ChannelOpenResult.Failure("Insufficient on-chain balance to fund channel")
        }

        // Deduct from onchain wallet
        updateOnChainBalance(_onChainBalanceSats.value - initialLocalFunding)

        val newChannel = ChannelEntity(
            peerName = peerName,
            peerNodeUri = peerUri,
            capacitySats = capacitySats,
            localBalanceSats = initialLocalFunding,
            remoteBalanceSats = capacitySats - initialLocalFunding,
            isActive = true // Directly opened in simulation for excellent fluidity
        )

        val channelId = channelDao.insertChannel(newChannel)

        // Log transaction
        transactionDao.insertTransaction(
            TransactionEntity(
                type = "CHANNEL_OPEN",
                amountSats = capacitySats,
                description = "Opened channel with $peerName",
                status = "SUCCESS",
                feeSats = 150 // On-chain closing/opening fee estimation
            )
        )

        return ChannelOpenResult.Success(channelId)
    }

    /**
     * Closes an active channel (refunds local balance to our onchain wallet)
     */
    suspend fun closeChannel(channelId: Long, localBalanceSats: Long, peerName: String) {
        channelDao.deleteChannelById(channelId)

        // Refund local balance to onchain
        updateOnChainBalance(_onChainBalanceSats.value + localBalanceSats)

        // Log transaction
        transactionDao.insertTransaction(
            TransactionEntity(
                type = "CHANNEL_CLOSE",
                amountSats = localBalanceSats,
                description = "Closed channel with $peerName",
                status = "SUCCESS",
                feeSats = 200
            )
        )
    }

    /**
     * Simulates routing a payment through this node (earns dynamic routing fee, updates a channel balance)
     */
    suspend fun simulateRoutingTransaction(): Boolean {
        val channels = channelDao.getAllChannels().filter { it.isActive }
        if (channels.size < 2) return false // Need at least 2 channels to route (in and out)

        val inputChannel = channels.random()
        val remainingChannels = channels.filter { it.id != inputChannel.id }
        if (remainingChannels.isEmpty()) return false
        val outputChannel = remainingChannels.random()

        // Let's route a tiny transaction (e.g. 5000 sats) with 5 sat fee
        val routeAmount = 5000L
        val routingFee = Random.nextLong(1, 15)

        if (inputChannel.remoteBalanceSats >= routeAmount + routingFee && outputChannel.localBalanceSats >= routeAmount) {
            // Channel 1 receives sats (remote balance shifts local)
            channelDao.updateChannelBalances(
                inputChannel.id,
                inputChannel.localBalanceSats + routeAmount + routingFee,
                inputChannel.remoteBalanceSats - routeAmount - routingFee
            )
            // Channel 2 sends sats (local balance shifts remote)
            channelDao.updateChannelBalances(
                outputChannel.id,
                outputChannel.localBalanceSats - routeAmount,
                outputChannel.remoteBalanceSats + routeAmount
            )

            // Log routing fees transaction
            transactionDao.insertTransaction(
                TransactionEntity(
                    type = "ROUTING_FEES",
                    amountSats = routingFee,
                    description = "Routed ${routeAmount}s from ${inputChannel.peerName} to ${outputChannel.peerName}",
                    status = "SUCCESS",
                    feeSats = 0
                )
            )
            return true
        }
        return false
    }

    /**
     * Resets database to default state (clear database)
     */
    suspend fun resetWallet() {
        transactionDao.clearAllTransactions()
        val currentChannels = channelDao.getAllChannels()
        for (c in currentChannels) {
            channelDao.deleteChannelById(c.id)
        }
        updateOnChainBalance(100000L) // Reset onchain to 100k
        prepopulateIfEmpty()
    }
}

sealed class PaymentResult {
    data class Success(val amountSats: Long, val feeSats: Long) : PaymentResult()
    data class Failure(val message: String) : PaymentResult()
}

sealed class ChannelOpenResult {
    data class Success(val channelId: Long) : ChannelOpenResult()
    data class Failure(val message: String) : ChannelOpenResult()
}
