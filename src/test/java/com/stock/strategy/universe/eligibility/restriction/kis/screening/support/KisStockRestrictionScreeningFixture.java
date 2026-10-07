package com.stock.strategy.universe.eligibility.restriction.kis.screening.support;

import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.put;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;

public final class KisStockRestrictionScreeningFixture {
    private KisStockRestrictionScreeningFixture() {
    }

    public static Inputs inputs(KisStockMasterMarket market, String code, String preannouncement,
                                Map<String, String> masterFields, Map<String, String> apiFields) {
        var batch = batch(market, code, preannouncement, masterFields);
        return new Inputs(basicInfo(batch, market == KOSPI ? "005930" : "0004Y0",
                market == KOSPI ? "KR7005930003" : "KR70004Y0000", market, apiFields), warnings(batch, market));
    }

    public static StockMasterBatchParseResult batch(KisStockMasterMarket market, String code, String preannouncement,
                                                   Map<String, String> masterFields) {
        byte[] kospi = warningRow(KOSPI, "005930", "KR7005930003", market == KOSPI ? code : "00", market == KOSPI ? preannouncement : "N");
        byte[] kosdaq = warningRow(KOSDAQ, "0004Y0", "KR70004Y0000", market == KOSDAQ ? code : "00", market == KOSDAQ ? preannouncement : "N");
        byte[] selected = market == KOSPI ? kospi : kosdaq;
        masterFields.forEach((field, value) -> put(selected, switch (field) {
            case "suspension" -> market == KOSPI ? 121 : 116;
            case "liquidation" -> market == KOSPI ? 122 : 117;
            case "spac" -> market == KOSPI ? 90 : 85;
            case "management" -> market == KOSPI ? 123 : 118;
            case "caution" -> {
                if (market == KOSPI) {
                    throw new IllegalArgumentException("KOSPI has no investment caution field.");
                }
                yield 91;
            }
            default -> throw new IllegalArgumentException("Unknown fixture field.");
        }, value));
        // An unrelated flagged row catches accidental whole-market or first-row selection.
        return KisStockBasicInfoMatchingFixture.batch(content(kospi, warningRow(KOSPI, "111111", "KR7111111111", "03", "Y")),
                content(kosdaq, warningRow(KOSDAQ, "000250", "KR7000250001", "03", "Y")));
    }

    public static KisStockBasicInfoRestrictionScreeningResult basicInfo(StockMasterBatchParseResult batch, String symbol,
                                                                     String standardCode, KisStockMasterMarket market,
                                                                     Map<String, String> apiFields) {
        var fields = KisStockBasicInfoMatchingFixture.fields(symbol, standardCode, market == KOSPI ? "STK" : "KSQ")
                .put("scty_grp_id_cd", "ST").put("stck_kind_cd", "101").put("tr_stop_yn", "N").put("admn_item_yn", "N");
        apiFields.forEach(fields::put);
        var matching = new KisStockBasicInfoMatchingPolicy().match(batch, symbol, KisStockBasicInfoMatchingFixture.api(fields));
        var type = KisStockBasicInfoTypeResolutionFixture.policy().resolve(matching);
        return new KisStockBasicInfoRestrictionScreeningPolicy().evaluate(new KisStockBasicInfoRestrictionObservationPolicy().evaluate(type));
    }

    public static KisStockMarketWarningObservationResult warnings(StockMasterBatchParseResult batch, KisStockMasterMarket market) {
        var source = batch.marketResults().stream().filter(result -> result.market() == market).findFirst().orElseThrow();
        return new KisStockMarketWarningObservationPolicy().evaluate(new KisStockMasterMarketWarningParser().parse(source));
    }

    private static byte[] warningRow(KisStockMasterMarket market, String symbol, String standardCode, String code, String preannouncement) {
        byte[] row = row(market, symbol, standardCode, "SYNTHETIC STOCK");
        put(row, market == KOSPI ? 124 : 119, code);
        put(row, market == KOSPI ? 126 : 121, preannouncement);
        return row;
    }

    public record Inputs(KisStockBasicInfoRestrictionScreeningResult basicInfo,
                         KisStockMarketWarningObservationResult warnings) {
    }
}
