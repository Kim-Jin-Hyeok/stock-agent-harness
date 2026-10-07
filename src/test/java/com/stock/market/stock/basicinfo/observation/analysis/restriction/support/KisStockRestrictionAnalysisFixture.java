package com.stock.market.stock.basicinfo.observation.analysis.restriction.support;

import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.Map;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;

public final class KisStockRestrictionAnalysisFixture {
    private KisStockRestrictionAnalysisFixture() {
    }

    public static Inputs inputs() {
        return inputs(KOSDAQ, "00", "N", Map.of(), Map.of());
    }

    public static Inputs inputs(KisStockMasterMarket market, String code, String preannouncement,
                                Map<String, String> masterFields, Map<String, String> apiFields) {
        var master = KisStockRestrictionScreeningFixture.batch(market, code, preannouncement, masterFields);
        String symbol = market == KOSPI ? "005930" : "0004Y0";
        var fields = KisStockBasicInfoMatchingFixture.fields(symbol,
                        market == KOSPI ? "KR7005930003" : "KR70004Y0000", market == KOSPI ? "STK" : "KSQ")
                .put("scty_grp_id_cd", "ST").put("stck_kind_cd", "101")
                .put("tr_stop_yn", "N").put("admn_item_yn", "N");
        apiFields.forEach(fields::put);
        return new Inputs(response(symbol, fields), master, KisStockRestrictionScreeningFixture.warnings(master, market));
    }

    public static KisStockBasicInfoAnalysisResult analysis(Long id, Inputs inputs) {
        return new KisStockBasicInfoAnalysisResult(id, inputs.response(), screening(inputs.master(), inputs.response()));
    }

    public record Inputs(KisStockBasicInfoRawResponse response, StockMasterBatchParseResult master,
                         KisStockMarketWarningObservationResult warnings) {
    }
}
