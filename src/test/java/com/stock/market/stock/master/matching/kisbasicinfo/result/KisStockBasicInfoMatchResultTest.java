package com.stock.market.stock.master.matching.kisbasicinfo.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture.api;
import static com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture.batch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KisStockBasicInfoMatchResultTest {
    private static final String VERSION = KisStockBasicInfoMatchingPolicy.MATCHING_VERSION;
    private final StockMasterBatchParseResult master = batch();
    private final KisStockBasicInfoParseResult input = api("005930", "KR7005930003", "STK");

    @ParameterizedTest
    @MethodSource("outcomes")
    void acceptsOnlyTheReasonConsistentWithAllInputs(Outcome outcome) {
        var result = matched(outcome);
        assertEquals(outcome.reason(), result.reasonCode());
        for (var reason : KisStockBasicInfoMatchReasonCode.values()) {
            if (reason != outcome.reason()) {
                assertThrows(IllegalArgumentException.class, () -> copy(result, result.comparedMarket(),
                        result.comparedRecord(), reason));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("outcomes")
    void retainsCompleteInputsAndComparisonAcrossJsonRoundTrip(Outcome outcome) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var result = matched(outcome);
        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoMatchResult.class);
        assertEquals(result, restored);
        assertEquals(mapper.valueToTree(result), mapper.valueToTree(restored));
    }

    @Test
    void rejectsAnotherExistingRowInsteadOfTheRequestedRow() {
        var requested = new KisStockBasicInfoMatchingPolicy().match(master, "005930", input);
        var other = new KisStockBasicInfoMatchingPolicy().match(master, "111111", input);
        assertThrows(IllegalArgumentException.class, () -> copy(requested, other.comparedMarket(),
                other.comparedRecord(), requested.reasonCode()));
    }

    @Test
    void rejectsAlteredRowEvenWhenIdentifiersAreUnchanged() {
        var result = new KisStockBasicInfoMatchingPolicy().match(master, "005930", input);
        var raw = result.comparedRecord();
        var altered = new KisStockMasterRawRecord(raw.lineNumber(), raw.symbol(), raw.standardCode(), "ALTERED NAME",
                raw.rawGroup(), raw.rawEtp(), raw.rawPreferred(), raw.rawListingDate(), raw.rawSuspension(),
                raw.rawLiquidation(), raw.rawSpac(), raw.rawManagement(), raw.rawInvestmentCaution(),
                raw.rawBaseDate(), raw.rawLine());
        assertThrows(IllegalArgumentException.class,
                () -> copy(result, result.comparedMarket(), altered, result.reasonCode()));
    }

    @Test
    void rejectsWrongOrMissingComparedMarketAndRow() {
        var result = new KisStockBasicInfoMatchingPolicy().match(master, "005930", input);
        assertThrows(IllegalArgumentException.class,
                () -> copy(result, KisStockMasterMarket.KOSDAQ, result.comparedRecord(), result.reasonCode()));
        assertThrows(IllegalArgumentException.class,
                () -> copy(result, null, result.comparedRecord(), result.reasonCode()));
        assertThrows(IllegalArgumentException.class,
                () -> copy(result, result.comparedMarket(), null, result.reasonCode()));
        assertThrows(IllegalArgumentException.class,
                () -> copy(result, null, null, KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND));
    }

    @Test
    void rejectsInjectedRowWhenRequestedSymbolIsMissing() {
        var missing = new KisStockBasicInfoMatchingPolicy().match(master, "999999", input);
        var existing = new KisStockBasicInfoMatchingPolicy().match(master, "005930", input);
        assertThrows(IllegalArgumentException.class, () -> copy(missing, existing.comparedMarket(),
                existing.comparedRecord(), KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankVersion(String version) {
        var result = new KisStockBasicInfoMatchingPolicy().match(master, "005930", input);
        assertThrows(IllegalArgumentException.class, () -> new KisStockBasicInfoMatchResult(version, master,
                "005930", input, result.comparedMarket(), result.comparedRecord(), result.reasonCode()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankRequest(String request) {
        assertThrows(IllegalArgumentException.class, () -> new KisStockBasicInfoMatchResult(VERSION, master,
                request, input, null, null, KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND));
    }

    @Test
    void rejectsNullInputsAndReason() {
        var result = new KisStockBasicInfoMatchingPolicy().match(master, "005930", input);
        assertThrows(NullPointerException.class, () -> new KisStockBasicInfoMatchResult(VERSION, null,
                "005930", input, result.comparedMarket(), result.comparedRecord(), result.reasonCode()));
        assertThrows(NullPointerException.class, () -> new KisStockBasicInfoMatchResult(VERSION, master,
                "005930", null, result.comparedMarket(), result.comparedRecord(), result.reasonCode()));
        assertThrows(NullPointerException.class, () -> copy(result, result.comparedMarket(), result.comparedRecord(), null));
    }

    private KisStockBasicInfoMatchResult matched(Outcome outcome) {
        return new KisStockBasicInfoMatchingPolicy().match(master, outcome.request(),
                api(outcome.request(), outcome.standardCode(), outcome.market()));
    }

    private KisStockBasicInfoMatchResult copy(KisStockBasicInfoMatchResult result,
            KisStockMasterMarket market, KisStockMasterRawRecord record, KisStockBasicInfoMatchReasonCode reason) {
        return new KisStockBasicInfoMatchResult(result.matchingVersion(), result.masterBatch(),
                result.requestedSymbol(), result.apiInput(), market, record, reason);
    }

    static Stream<Outcome> outcomes() {
        return Arrays.stream(new Outcome[]{
                new Outcome("005930", "KR7005930003", "STK", KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH),
                new Outcome("999999", "KR7005930003", "STK", KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND),
                new Outcome("005930", " ", "STK", KisStockBasicInfoMatchReasonCode.API_STANDARD_CODE_BLANK),
                new Outcome("005930", "KR7111111111", "STK", KisStockBasicInfoMatchReasonCode.STANDARD_CODE_MISMATCH),
                new Outcome("005930", "KR7005930003", "UNKNOWN", KisStockBasicInfoMatchReasonCode.API_MARKET_UNVERIFIED),
                new Outcome("005930", "KR7005930003", "KSQ", KisStockBasicInfoMatchReasonCode.MARKET_MISMATCH)
        });
    }

    record Outcome(String request, String standardCode, String market, KisStockBasicInfoMatchReasonCode reason) {
    }
}
