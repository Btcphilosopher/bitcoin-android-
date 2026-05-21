package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lightning_channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerName: String,
    val peerNodeUri: String,
    val capacitySats: Long,
    val localBalanceSats: Long,
    val remoteBalanceSats: Long,
    val isActive: Boolean,
    val openedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "lightning_transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "SEND", "RECEIVE", "CHANNEL_OPEN", "CHANNEL_CLOSE", "ROUTING_FEES"
    val amountSats: Long,
    val description: String,
    val status: String, // "SUCCESS", "PENDING", "FAILED"
    val timestamp: Long = System.currentTimeMillis(),
    val feeSats: Long = 0,
    val invoice: String = "",
    val preimage: String = ""
)

@Dao
interface ChannelDao {
    @Query("SELECT * FROM lightning_channels ORDER BY openedTimestamp DESC")
    fun getAllChannelsFlow(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM lightning_channels")
    suspend fun getAllChannels(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity): Long

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    @Query("DELETE FROM lightning_channels WHERE id = :id")
    suspend fun deleteChannelById(id: Long)

    @Query("UPDATE lightning_channels SET localBalanceSats = :local, remoteBalanceSats = :remote WHERE id = :id")
    suspend fun updateChannelBalances(id: Long, local: Long, remote: Long)

    @Query("SELECT SUM(localBalanceSats) FROM lightning_channels WHERE isActive = 1")
    fun getTotalLocalBalanceFlow(): Flow<Long?>

    @Query("SELECT SUM(remoteBalanceSats) FROM lightning_channels WHERE isActive = 1")
    fun getTotalRemoteBalanceFlow(): Flow<Long?>
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM lightning_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM lightning_transactions")
    suspend fun clearAllTransactions()
}

@Database(entities = [ChannelEntity::class, TransactionEntity::class], version = 1, exportSchema = false)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: WalletDatabase? = null

        fun getDatabase(context: Context): WalletDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "lightning_wallet_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
