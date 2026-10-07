package com.stock.market.stock.basicinfo.observation.analysis.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.END;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;

public final class KisStockBasicInfoAnalysisFixture {
    private KisStockBasicInfoAnalysisFixture() {
    }

    public static StockMasterBatchParseResult batch() {
        byte[] etf = KisStockMasterParsingFixture.row(KOSPI, "111111", "KR7111111111", "SYNTHETIC ETF");
        KisStockMasterParsingFixture.put(etf, 61, "EF");
        KisStockMasterParsingFixture.put(etf, 83, "2");
        return KisStockBasicInfoMatchingFixture.batch(
                KisStockMasterParsingFixture.content(KisStockMasterParsingFixture.row(KOSPI), etf),
                KisStockMasterParsingFixture.content(
                        KisStockMasterParsingFixture.row(KOSDAQ, SYMBOL, "KR70004Y0000", "SYNTHETIC ALPHA")));
    }

    public static ObjectNode fields() {
        return KisStockBasicInfoMatchingFixture.fields(SYMBOL, "KR70004Y0000", "KSQ")
                .put("scty_grp_id_cd", "ST").put("stck_kind_cd", "101")
                .put("tr_stop_yn", "N").put("admn_item_yn", "N");
    }

    public static KisStockBasicInfoRawResponse response() {
        return response(SYMBOL, fields());
    }

    public static KisStockBasicInfoRawResponse response(String symbol, ObjectNode fields) {
        return new KisStockBasicInfoRawResponse(symbol, START, END, 200, KisStockBasicInfoParsingFixture.content(fields));
    }

    public static KisStockBasicInfoAnalysisService service(KisStockBasicInfoObservationStore store) {
        return new KisStockBasicInfoAnalysisService(store, new KisStockBasicInfoParser(), new KisStockBasicInfoMatchingPolicy(),
                KisStockBasicInfoTypeResolutionFixture.policy(), new KisStockBasicInfoRestrictionObservationPolicy(),
                new KisStockBasicInfoRestrictionScreeningPolicy());
    }

    public static KisStockBasicInfoRestrictionScreeningResult screening(
            StockMasterBatchParseResult masterBatch, KisStockBasicInfoRawResponse response
    ) {
        var parsed = new KisStockBasicInfoParser().parse(response.content());
        var matching = new KisStockBasicInfoMatchingPolicy().match(masterBatch, response.requestedSymbol(), parsed);
        var type = KisStockBasicInfoTypeResolutionFixture.policy().resolve(matching);
        var observation = new KisStockBasicInfoRestrictionObservationPolicy().evaluate(type);
        return new KisStockBasicInfoRestrictionScreeningPolicy().evaluate(observation);
    }
}
