package com.stock.market.index.history.provider.kis;

public enum KisMarketIndex {
    KOSPI("KOSPI", "0001");

    private final String benchmarkId;
    private final String inputCode;

    KisMarketIndex(String benchmarkId, String inputCode) {
        this.benchmarkId = benchmarkId;
        this.inputCode = inputCode;
    }

    public String benchmarkId() {
        return benchmarkId;
    }

    public String inputCode() {
        return inputCode;
    }

    public static KisMarketIndex fromBenchmarkId(String benchmarkId) {
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        for (KisMarketIndex marketIndex : values()) {
            if (marketIndex.benchmarkId.equals(benchmarkId)) {
                return marketIndex;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported KIS market index: " + benchmarkId
        );
    }
}
