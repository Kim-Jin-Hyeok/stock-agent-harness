package com.stock.broker.kis.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.broker.account.BrokerAccountPosition;
import com.stock.broker.account.BrokerAccountSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record KisAccountBalanceResponse(
        @JsonProperty("rt_cd") String resultCode,
        @JsonProperty("msg_cd") String messageCode,
        @JsonProperty("msg1") String message,
        List<KisAccountBalancePositionOutput> output1,
        List<KisAccountBalanceSummaryOutput> output2
) {
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }

    public BrokerAccountSnapshot toSnapshot(Instant observedAt) {
        if (!isSuccessful()) {
            throw new IllegalStateException(
                    "KIS account balance response was not successful. messageCode="
                            + messageCode
            );
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null.");
        if (output1 == null) {
            throw new IllegalArgumentException("output1 must not be null.");
        }
        if (output2 == null || output2.isEmpty()) {
            throw new IllegalArgumentException(
                    "output2 must contain account summary."
            );
        }

        List<BrokerAccountPosition> positions = output1.stream()
                .map(KisAccountBalancePositionOutput::toPosition)
                .toList();
        KisAccountBalanceSummaryOutput summary = output2.getFirst();

        return new BrokerAccountSnapshot(
                summary.depositAmountKrw(),
                summary.totalAssetAmountKrw(),
                positions,
                observedAt
        );
    }
}
