package com.stock.strategy.universe.eligibility.restriction.kis.warning.result;

import com.stock.market.stock.master.provider.kis.parsing.warning.record.KisStockMasterMarketWarningRawRecord;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;

import java.util.Objects;

public record KisStockMarketWarningObservation(
        KisStockMasterMarketWarningRawRecord rawRecord,
        KisStockMarketWarningStatus warningStatus,
        KisStockTradingFlagStatus riskPreannouncementStatus
) {
    public KisStockMarketWarningObservation {
        Objects.requireNonNull(rawRecord, "rawRecord must not be null.");
        Objects.requireNonNull(warningStatus, "warningStatus must not be null.");
        Objects.requireNonNull(riskPreannouncementStatus, "riskPreannouncementStatus must not be null.");
        if (warningStatus != KisStockMarketWarningStatus.fromRawValue(rawRecord.rawMarketWarningCode())) {
            throw new IllegalArgumentException("warningStatus must agree with its original market warning code.");
        }
        if (riskPreannouncementStatus != KisStockTradingFlagStatus.fromRawValue(rawRecord.rawMarketWarningRiskPreannouncement())) {
            throw new IllegalArgumentException("riskPreannouncementStatus must agree with its original market warning preannouncement value.");
        }
    }
}
