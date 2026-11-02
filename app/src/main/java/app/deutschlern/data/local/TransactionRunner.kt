package app.deutschlern.data.local

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs a block atomically. Abstracted from Room so repositories stay unit-testable with DAO mocks.
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction(block)
}
