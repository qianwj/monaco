package cn.elvis.monaco.testkit.store;

public record StoreCapabilities(
        boolean persistentRestart,
        boolean schemaMigration,
        boolean concurrentTransactions) {
}
