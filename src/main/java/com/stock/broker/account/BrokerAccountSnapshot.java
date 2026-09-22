package com.stock.broker.account;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record BrokerAccountSnapshot(
        long depositAmountKrw,
        long totalAssetAmountKrw,
        List<BrokerAccountPosition> positions,
        Instant observedAt
) {
    public BrokerAccountSnapshot {
        if (depositAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "depositAmountKrw must not be negative."
            );
        }
        if (totalAssetAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "totalAssetAmountKrw must not be negative."
            );
        }

        positions = List.copyOf(Objects.requireNonNull(
                positions,
                "positions must not be null."
        ));
        Objects.requireNonNull(observedAt, "observedAt must not be null.");
    }
}
