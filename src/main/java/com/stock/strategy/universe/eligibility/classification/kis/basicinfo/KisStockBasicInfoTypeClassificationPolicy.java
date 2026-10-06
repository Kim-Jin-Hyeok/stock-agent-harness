package com.stock.strategy.universe.eligibility.classification.kis.basicinfo;

import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import java.util.Objects;
import java.util.Set;

public class KisStockBasicInfoTypeClassificationPolicy {
    public static final String CLASSIFICATION_VERSION = "KIS_STOCK_BASIC_INFO_CURRENT_TYPE_V1";
    public static final String SOURCE_REFERENCE = "build/kis-stock-eligibility-source-validation-01/apiportal-specification.json";
    public static final String SOURCE_SHA256 = "1fbf349c755a3f54469450e0b7ca89ee3aa3a779e4a33286348cc2e198c0567a";
    private static final Set<String> DEFINED_MARKETS = Set.of(
            "AGR", "BON", "CMD", "CUR", "ENG", "EQU", "ETF", "IRT", "KNX", "KSQ", "MTL", "SPI", "STK"
    );
    private static final Set<String> SUPPORTED_MARKETS = Set.of("STK", "KSQ");
    private static final Set<String> DEFINED_SECURITY_GROUPS = Set.of(
            "BC", "DR", "EF", "EN", "EW", "FE", "FO", "FS", "FU", "FX", "GD", "IC", "IF", "KN",
            "MF", "OP", "RT", "SC", "SR", "ST", "SW", "TC"
    );
    private static final Set<String> DEFINED_STOCK_KINDS = Set.of(
            "000", "101", "201", "202", "203", "204", "205", "206", "207", "208", "209", "210",
            "211", "212", "213", "214", "215", "216", "217", "218", "219", "220", "301", "401"
    );
    private static final Set<String> SUPPORTED_PREFERRED_KINDS = Set.of("201", "202");

    public KisStockBasicInfoTypeClassificationResult classify(KisStockBasicInfoParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult must not be null.");
        var raw = parseResult.rawRecord();
        if (!DEFINED_MARKETS.contains(raw.rawMarket())) {
            return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED);
        }
        if (!SUPPORTED_MARKETS.contains(raw.rawMarket())) {
            return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.MARKET_UNSUPPORTED);
        }
        // The captured response definition is blank; only the observed response product type 300 is supported.
        if (!"300".equals(raw.rawProductType())) {
            return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.PRODUCT_TYPE_VALUE_UNVERIFIED);
        }
        if (!DEFINED_SECURITY_GROUPS.contains(raw.rawSecurityGroup())) {
            return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
        }
        if (!"ST".equals(raw.rawSecurityGroup())) {
            return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
        }
        if (!DEFINED_STOCK_KINDS.contains(raw.rawStockKind())) {
            return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED);
        }
        // A share type can also describe a SPAC or restricted stock; it is not an eligibility approval.
        if ("101".equals(raw.rawStockKind())) {
            return result(parseResult, StockSecurityType.COMMON_STOCK, KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        }
        if (SUPPORTED_PREFERRED_KINDS.contains(raw.rawStockKind())) {
            return result(parseResult, StockSecurityType.PREFERRED_STOCK, KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        }
        return result(parseResult, null, KisStockBasicInfoTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    private static KisStockBasicInfoTypeClassificationResult result(
            KisStockBasicInfoParseResult parseResult, StockSecurityType type,
            KisStockBasicInfoTypeClassificationReasonCode reasonCode
    ) {
        return new KisStockBasicInfoTypeClassificationResult(parseResult, type, reasonCode,
                CLASSIFICATION_VERSION, SOURCE_REFERENCE, SOURCE_SHA256);
    }
}
