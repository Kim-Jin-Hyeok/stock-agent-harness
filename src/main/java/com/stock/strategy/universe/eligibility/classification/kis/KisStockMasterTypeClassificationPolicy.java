package com.stock.strategy.universe.eligibility.classification.kis;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;
import java.util.Set;

public class KisStockMasterTypeClassificationPolicy {
    public static final String CLASSIFICATION_VERSION = "KIS_STOCK_MASTER_CURRENT_TYPE_V1";
    public static final String SOURCE_REVISION = "277ec0eb7a9b7f63b6807829286c80f36649dad2";
    private static final Set<String> DEFINED_GROUPS = Set.of(
            "ST", "MF", "RT", "SC", "IF", "DR", "EW", "EF", "SW", "SR", "BC", "FE", "FS"
    );
    private static final Set<String> KOSPI_DEFINED_ETP_VALUES = Set.of("0", "1", "2", "3", "4", "5");
    private static final Set<String> KOSDAQ_DEFINED_ETP_VALUES = Set.of("0", "1", "2", "3", "4");
    private static final Set<String> DEFINED_PREFERRED_VALUES = Set.of("0", "1", "2");

    public KisStockMasterTypeClassificationResult classify(KisStockMasterMarket market, KisStockMasterRawRecord rawRecord) {
        Objects.requireNonNull(market, "market must not be null.");
        Objects.requireNonNull(rawRecord, "rawRecord must not be null.");
        if (!DEFINED_GROUPS.contains(rawRecord.rawGroup())) {
            return result(market, rawRecord, null, KisStockMasterTypeClassificationReasonCode.GROUP_VALUE_UNVERIFIED);
        }
        var etpValues = switch (market) {
            case KOSPI -> KOSPI_DEFINED_ETP_VALUES;
            case KOSDAQ -> KOSDAQ_DEFINED_ETP_VALUES;
        };
        if (!etpValues.contains(rawRecord.rawEtp())) {
            return result(market, rawRecord, null, KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        }
        if (!DEFINED_PREFERRED_VALUES.contains(rawRecord.rawPreferred())) {
            return result(market, rawRecord, null, KisStockMasterTypeClassificationReasonCode.PREFERRED_VALUE_UNVERIFIED);
        }
        // Defined individual values do not prove a supported combination or stock eligibility.
        if (market == KisStockMasterMarket.KOSPI && rawRecord.rawGroup().equals("EF")
                && rawRecord.rawEtp().equals("2") && rawRecord.rawPreferred().equals("0")) {
            return result(market, rawRecord, StockSecurityType.ETF, KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED);
        }
        return result(market, rawRecord, null, KisStockMasterTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    private static KisStockMasterTypeClassificationResult result(
            KisStockMasterMarket market, KisStockMasterRawRecord rawRecord,
            StockSecurityType securityType, KisStockMasterTypeClassificationReasonCode reasonCode
    ) {
        return new KisStockMasterTypeClassificationResult(market, rawRecord, securityType, reasonCode,
                CLASSIFICATION_VERSION, SOURCE_REVISION);
    }
}
