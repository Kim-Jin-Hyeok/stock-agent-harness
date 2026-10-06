package com.stock.strategy.universe.eligibility.classification.krx;

import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.strategy.universe.eligibility.classification.krx.result.KrxStockBasicInfoTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.krx.result.KrxStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;
import java.util.Set;

public class KrxStockBasicInfoTypeClassificationPolicy {
    public static final String CLASSIFICATION_VERSION = "KRX_STOCK_BASIC_INFO_CURRENT_TYPE_V1";
    public static final String SOURCE_REFERENCE = "build/stock-eligibility-krx-source-validation-03/final-verification.json";
    public static final String SOURCE_SHA256 = "df0658234036f57e88f297ef3cdf13eca804ea07ff0b944474ba3151a6448711";
    private static final Set<String> SUPPORTED_MARKETS = Set.of("KOSPI", "KOSDAQ");
    private static final String SHARE = "\uc8fc\uad8c";
    private static final String COMMON = "\ubcf4\ud1b5\uc8fc";
    private static final String OLD_PREFERRED = "\uad6c\ud615\uc6b0\uc120\uc8fc";
    private static final String NEW_PREFERRED = "\uc2e0\ud615\uc6b0\uc120\uc8fc";
    private static final Set<String> OBSERVED_SECURITY_GROUPS = Set.of(
            SHARE, "\ubd80\ub3d9\uc0b0\ud22c\uc790\ud68c\uc0ac",
            "\uc0ac\ud68c\uac04\uc811\uc790\ubcf8\ud22c\uc735\uc790\ud68c\uc0ac",
            "\uc678\uad6d\uc8fc\uad8c", "\uc8fc\uc2dd\uc608\ud0c1\uc99d\uad8c", "\ud22c\uc790\ud68c\uc0ac"
    );
    private static final Set<String> OBSERVED_STOCK_KINDS = Set.of(
            COMMON, OLD_PREFERRED, NEW_PREFERRED, "\uc885\ub958\uc8fc\uad8c"
    );

    public KrxStockBasicInfoTypeClassificationResult classify(KrxStockBasicInfoRawRecord rawRecord) {
        Objects.requireNonNull(rawRecord, "rawRecord must not be null.");
        if (!SUPPORTED_MARKETS.contains(rawRecord.rawMarket())) {
            return result(rawRecord, null, KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED);
        }
        if (!OBSERVED_SECURITY_GROUPS.contains(rawRecord.rawSecurityGroup())) {
            return result(rawRecord, null, KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
        }
        if (!SHARE.equals(rawRecord.rawSecurityGroup())) {
            return result(rawRecord, null, KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
        }
        if (!OBSERVED_STOCK_KINDS.contains(rawRecord.rawStockKind())) {
            return result(rawRecord, null, KrxStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED);
        }
        // Share type does not certify section restrictions, listing status, or point-in-time eligibility.
        if (COMMON.equals(rawRecord.rawStockKind())) {
            return result(rawRecord, StockSecurityType.COMMON_STOCK, KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        }
        if (OLD_PREFERRED.equals(rawRecord.rawStockKind()) || NEW_PREFERRED.equals(rawRecord.rawStockKind())) {
            return result(rawRecord, StockSecurityType.PREFERRED_STOCK, KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        }
        return result(rawRecord, null, KrxStockBasicInfoTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    private static KrxStockBasicInfoTypeClassificationResult result(
            KrxStockBasicInfoRawRecord rawRecord, StockSecurityType type,
            KrxStockBasicInfoTypeClassificationReasonCode reasonCode
    ) {
        return new KrxStockBasicInfoTypeClassificationResult(rawRecord, type, reasonCode,
                CLASSIFICATION_VERSION, SOURCE_REFERENCE, SOURCE_SHA256);
    }
}
