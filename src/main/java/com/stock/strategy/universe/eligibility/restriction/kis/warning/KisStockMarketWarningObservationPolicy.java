package com.stock.strategy.universe.eligibility.restriction.kis.warning;

import com.stock.market.stock.master.provider.kis.parsing.warning.result.KisStockMasterMarketWarningParseResult;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservation;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus;

import java.util.Objects;

public class KisStockMarketWarningObservationPolicy {
    public static final String OBSERVATION_VERSION = "KIS_STOCK_MARKET_WARNING_OBSERVATION_V1";
    public static final String SOURCE_REVISION = "277ec0eb7a9b7f63b6807829286c80f36649dad2";

    public KisStockMarketWarningObservationResult evaluate(KisStockMasterMarketWarningParseResult source) {
        requireSupportedSource(source);
        var observations = source.records().stream()
                .map(raw -> new KisStockMarketWarningObservation(raw,
                        KisStockMarketWarningStatus.fromRawValue(raw.rawMarketWarningCode()),
                        KisStockTradingFlagStatus.fromRawValue(raw.rawMarketWarningRiskPreannouncement())))
                .toList();
        return new KisStockMarketWarningObservationResult(source, observations, OBSERVATION_VERSION, SOURCE_REVISION);
    }

    public static void requireSupportedSource(KisStockMasterMarketWarningParseResult source) {
        Objects.requireNonNull(source, "source must not be null.");
        // Freeze the evidence contract even if a future raw parser accepts a different layout.
        if (!"KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1".equals(source.parserVersion())
                || !SOURCE_REVISION.equals(source.sourceRevision())
                || !"KIS_STOCK_MASTER_RAW_V2".equals(source.source().parserVersion())
                || !"OBSERVED_2026_10_05_LF_V1".equals(source.source().layoutVersion())) {
            throw new IllegalArgumentException("Market warning observation requires the verified raw parser, layout and source revision.");
        }
    }
}
