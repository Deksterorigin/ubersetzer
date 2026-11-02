package app.deutschlern.data.local

/** Runs the block directly; DAO mocks have no real transactions. */
object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}
