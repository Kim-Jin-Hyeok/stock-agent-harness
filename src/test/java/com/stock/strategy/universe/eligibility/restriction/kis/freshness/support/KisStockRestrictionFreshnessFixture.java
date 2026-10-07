package com.stock.strategy.universe.eligibility.restriction.kis.freshness.support;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture.inputs;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;

public final class KisStockRestrictionFreshnessFixture {
    public static final Instant EVALUATED_AT = Instant.parse("2026-10-07T10:00:00.123456789Z");
    public static final Duration MAX_MASTER_AGE = Duration.ofHours(24);
    public static final Duration MAX_BASIC_INFO_AGE = Duration.ofHours(1);

    private KisStockRestrictionFreshnessFixture() {
    }

    public static KisStockRestrictionFreshnessRequest request() {
        return new KisStockRestrictionFreshnessRequest(EVALUATED_AT, MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);
    }

    public static KisStockRestrictionAnalysisResult analysis() {
        return analysis(KOSDAQ);
    }

    public static KisStockRestrictionAnalysisResult analysis(KisStockMasterMarket market) {
        return analysis(market, "00", "N", Map.of(), Map.of());
    }

    public static KisStockRestrictionAnalysisResult analysis(KisStockMasterMarket market, String code, String preannouncement,
                                                             Map<String, String> masterFields, Map<String, String> apiFields) {
        var input = inputs(market, code, preannouncement, masterFields, apiFields);
        var master = withMarketTimes(input.master(), market, EVALUATED_AT.minus(Duration.ofHours(2)),
                EVALUATED_AT.minus(Duration.ofHours(2)).plusSeconds(20));
        var response = withResponseTimes(input.response(), EVALUATED_AT.minusSeconds(300), EVALUATED_AT.minusSeconds(298));
        return combine(response, master, warnings(master, market));
    }

    public static KisStockRestrictionAnalysisResult withTimes(KisStockRestrictionAnalysisResult original,
                                                              Instant masterStartedAt, Instant masterFinishedAt,
                                                              Instant apiStartedAt, Instant apiFinishedAt) {
        var matching = original.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult();
        var market = matching.comparedMarket();
        var master = withMarketTimes(matching.masterBatch(), market, masterStartedAt, masterFinishedAt);
        var response = withResponseTimes(original.basicInfoAnalysis().response(), apiStartedAt, apiFinishedAt);
        return combine(response, master, warnings(master, market));
    }

    public static StockMasterBatchParseResult withMarketTimes(StockMasterBatchParseResult original, KisStockMasterMarket market,
                                                              Instant startedAt, Instant finishedAt) {
        var collection = original.collection();
        var files = collection.files().stream().map(file -> file.market() == market
                ? new StockMasterCollectionResult.FileObservation(file.market(), file.sourceUri(), startedAt, finishedAt,
                file.archivePath(), file.archiveBytes(), file.archiveSha256(), file.extractedPath(), file.extractedBytes(), file.extractedSha256())
                : file).toList();
        var start = files.stream().map(StockMasterCollectionResult.FileObservation::startedAt).min(Instant::compareTo).orElseThrow();
        var finish = files.stream().map(StockMasterCollectionResult.FileObservation::finishedAt).max(Instant::compareTo).orElseThrow();
        return new StockMasterBatchParseResult(new StockMasterCollectionResult(collection.formatVersion(), collection.collectionId(),
                collection.evidenceScope(), start, finish, files), original.marketResults());
    }

    public static KisStockBasicInfoRawResponse withResponseTimes(KisStockBasicInfoRawResponse response, Instant startedAt, Instant finishedAt) {
        return new KisStockBasicInfoRawResponse(response.requestedSymbol(), startedAt, finishedAt, response.httpStatus(), response.content());
    }

    public static KisStockRestrictionAnalysisResult combine(KisStockBasicInfoRawResponse response, StockMasterBatchParseResult master,
                                                            KisStockMarketWarningObservationResult warning) {
        var basic = new KisStockBasicInfoAnalysisResult(17L, response, screening(master, response));
        return new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warning));
    }
}
