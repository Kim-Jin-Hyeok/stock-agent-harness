package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support;

import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchResult;
import com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.KisStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.KisStockBasicInfoTypeResolutionPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionResult;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;

public final class KisStockBasicInfoTypeResolutionFixture {
    private KisStockBasicInfoTypeResolutionFixture() {
    }

    public static KisStockBasicInfoTypeResolutionPolicy policy() {
        return new KisStockBasicInfoTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(),
                new KisStockBasicInfoTypeClassificationPolicy());
    }

    public static StockMasterBatchParseResult batch() {
        byte[] common = KisStockMasterParsingFixture.row(KOSPI);
        for (int offset : new int[]{90, 121, 122, 123}) {
            KisStockMasterParsingFixture.put(common, offset, "Y");
        }
        byte[] etf = KisStockMasterParsingFixture.row(KOSPI, "111111", "KR7111111111", "SYNTHETIC ETF");
        KisStockMasterParsingFixture.put(etf, 61, "EF");
        KisStockMasterParsingFixture.put(etf, 83, "2");
        return KisStockBasicInfoMatchingFixture.batch(KisStockMasterParsingFixture.content(common, etf),
                KisStockMasterParsingFixture.content(KisStockMasterParsingFixture.row(KOSDAQ,
                        "0004Y0", "KR70004Y0000", "SYNTHETIC ALPHA")));
    }

    public static KisStockBasicInfoMatchResult matching(
            String symbol, String standardCode, String market, String group, String kind
    ) {
        var fields = KisStockBasicInfoMatchingFixture.fields(symbol, standardCode, market)
                .put("scty_grp_id_cd", group).put("stck_kind_cd", kind);
        return new KisStockBasicInfoMatchingPolicy().match(batch(), symbol, KisStockBasicInfoMatchingFixture.api(fields));
    }

    public static KisStockBasicInfoMatchResult commonMatch() {
        return matching("005930", "KR7005930003", "STK", "ST", "101");
    }

    public static KisStockMasterTypeClassificationResult syntheticMaster(
            KisStockBasicInfoMatchResult matching, StockSecurityType type
    ) {
        // Used only for agreement branches outside the real master's current supported types.
        return new KisStockMasterTypeClassificationResult(matching.comparedMarket(), matching.comparedRecord(), type,
                type == null ? KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED
                        : KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED,
                "SYNTHETIC_MASTER_TYPE_V1", "0".repeat(40));
    }

    public static KisStockBasicInfoTypeResolutionResult sample(KisStockBasicInfoTypeResolutionReasonCode reason) {
        var matching = switch (reason) {
            case MATCH_NOT_CONFIRMED -> matching("005930", "KR7111111111", "STK", "ST", "101");
            case MASTER_TYPE_ONLY -> matching("111111", "KR7111111111", "STK", "EF", "");
            case TYPE_CONFLICT -> matching("111111", "KR7111111111", "STK", "ST", "101");
            case TYPE_UNVERIFIED -> matching("005930", "KR7005930003", "STK", "ST", "");
            case BASIC_INFO_TYPE_SUPPLEMENTED, SOURCES_AGREE -> commonMatch();
        };
        var master = reason == KisStockBasicInfoTypeResolutionReasonCode.SOURCES_AGREE
                ? syntheticMaster(matching, StockSecurityType.COMMON_STOCK)
                : new KisStockMasterTypeClassificationPolicy().classify(matching.comparedMarket(), matching.comparedRecord());
        var api = reason == KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED ? null
                : new KisStockBasicInfoTypeClassificationPolicy().classify(matching.apiInput());
        var reference = switch (reason) {
            case BASIC_INFO_TYPE_SUPPLEMENTED, SOURCES_AGREE -> StockSecurityType.COMMON_STOCK;
            case MASTER_TYPE_ONLY -> StockSecurityType.ETF;
            case MATCH_NOT_CONFIRMED, TYPE_CONFLICT, TYPE_UNVERIFIED -> null;
        };
        return new KisStockBasicInfoTypeResolutionResult(KisStockBasicInfoTypeResolutionPolicy.RESOLUTION_VERSION,
                matching, master, api, reference, reason);
    }
}
