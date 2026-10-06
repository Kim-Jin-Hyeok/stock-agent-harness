package com.stock.market.stock.master.matching.kisbasicinfo;

import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode.API_MARKET_UNVERIFIED;
import static com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode.API_STANDARD_CODE_BLANK;
import static com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode.MARKET_MISMATCH;
import static com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode.REQUESTED_SYMBOL_NOT_FOUND;
import static com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode.STANDARD_CODE_AND_MARKET_MATCH;
import static com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode.STANDARD_CODE_MISMATCH;
import static com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture.api;
import static com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture.batch;
import static com.stock.market.stock.master.matching.kisbasicinfo.support.KisStockBasicInfoMatchingFixture.fields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KisStockBasicInfoMatchingPolicyTest {
    private final KisStockBasicInfoMatchingPolicy policy = new KisStockBasicInfoMatchingPolicy();
    private final StockMasterBatchParseResult master = batch();

    @ParameterizedTest
    @CsvSource({"005930,KR7005930003,STK,KOSPI", "111111,KR7111111111,STK,KOSPI",
            "0004Y0,KR70004Y0000,KSQ,KOSDAQ", "000250,KR7000250001,KSQ,KOSDAQ"})
    void matchesExactStandardCodeAndExplicitMarketMapping(
            String symbol, String standardCode, String apiMarket, KisStockMasterMarket expectedMarket
    ) {
        var input = api(symbol, standardCode, apiMarket);
        var result = policy.match(master, symbol, input);

        assertEquals(STANDARD_CODE_AND_MARKET_MATCH, result.reasonCode());
        assertEquals(expectedMarket, result.comparedMarket());
        assertEquals(symbol, result.comparedRecord().symbol());
        assertEquals(standardCode, result.comparedRecord().standardCode());
        assertSame(master, result.masterBatch());
        assertSame(input, result.apiInput());
        assertEquals(symbol, result.requestedSymbol());
        assertEquals(KisStockBasicInfoMatchingPolicy.MATCHING_VERSION, result.matchingVersion());
    }

    @Test
    void doesNotReselectAnotherExistingStockFromReturnedStandardCode() {
        var input = api("111111", "KR7111111111", "STK");
        var result = policy.match(master, "005930", input);

        assertEquals(STANDARD_CODE_MISMATCH, result.reasonCode());
        assertEquals("005930", result.comparedRecord().symbol());
        assertEquals("00000A111111", result.apiInput().rawRecord().productNumber());
        assertSame(input, result.apiInput());
    }

    @ParameterizedTest
    @ValueSource(strings = {"999999", "005930 ", " 005930", "0004y0"})
    void keepsMissingRequestEvenWhenReturnedIdentifiersExist(String symbol) {
        var result = policy.match(master, symbol, api("005930", "KR7005930003", "STK"));

        assertEquals(REQUESTED_SYMBOL_NOT_FOUND, result.reasonCode());
        assertEquals(symbol, result.requestedSymbol());
        assertNull(result.comparedMarket());
        assertNull(result.comparedRecord());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void reportsBlankStandardCodeWithoutChangingTheInput(String standardCode) {
        assertMismatch(standardCode, "STK", API_STANDARD_CODE_BLANK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KR7111111111", "KR7005930003 ", " KR7005930003", "kr7005930003", "005930"})
    void comparesStandardCodeWithoutTrimmingOrConversion(String standardCode) {
        assertMismatch(standardCode, "STK", STANDARD_CODE_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "UNKNOWN", "STK ", " STK", "stk", "KOSPI", "KNX", "ETF"})
    void keepsUnverifiedMarketWithoutInferringFromNameOrStandardCode(String market) {
        assertMismatch("KR7005930003", market, API_MARKET_UNVERIFIED);
    }

    @ParameterizedTest
    @CsvSource({"005930,KR7005930003,KSQ,KOSPI", "0004Y0,KR70004Y0000,STK,KOSDAQ"})
    void reportsWrongMarketEvenWhenStandardCodeMatches(
            String symbol, String standardCode, String market, KisStockMasterMarket comparedMarket
    ) {
        var result = policy.match(master, symbol, api(symbol, standardCode, market));
        assertEquals(MARKET_MISMATCH, result.reasonCode());
        assertEquals(comparedMarket, result.comparedMarket());
        assertEquals(symbol, result.comparedRecord().symbol());
    }

    @Test
    void usesStableReasonPrecedenceWhenSeveralFieldsFail() {
        assertEquals(REQUESTED_SYMBOL_NOT_FOUND,
                policy.match(master, "999999", api("999999", "", "UNKNOWN")).reasonCode());
        assertMismatch("", "UNKNOWN", API_STANDARD_CODE_BLANK);
        assertMismatch("WRONG", "UNKNOWN", STANDARD_CODE_MISMATCH);
    }

    @ParameterizedTest
    @MethodSource("nonComparedFields")
    void ignoresUninterpretedApiFieldsButPreservesThem(String field) {
        var fields = fields("005930", "KR7005930003", "STK").put(field, "UNVERIFIED " + field);
        var input = api(fields);
        var result = policy.match(master, "005930", input);

        assertEquals(STANDARD_CODE_AND_MARKET_MATCH, result.reasonCode());
        assertSame(input, result.apiInput());
        assertEquals(input.rawRecord(), result.apiInput().rawRecord());
    }

    @Test
    void comparisonDoesNotEraseDifferentNamesOrMasterRestrictionFlags() {
        var result = policy.match(master, "005930", api("005930", "KR7005930003", "STK"));
        assertEquals(STANDARD_CODE_AND_MARKET_MATCH, result.reasonCode());
        assertNotEquals(result.comparedRecord().name(), result.apiInput().rawRecord().name());
        assertEquals("Y", result.comparedRecord().rawSuspension());
        assertEquals("Y", result.comparedRecord().rawLiquidation());
        assertEquals("Y", result.comparedRecord().rawSpac());
        assertEquals("Y", result.comparedRecord().rawManagement());
        assertEquals("Y", result.apiInput().rawRecord().rawManagement());
    }

    @Test
    void repeatedCallsDoNotRetainAnotherRequestsComparisonState() {
        var input = api("005930", "KR7005930003", "STK");
        var before = policy.match(master, "005930", input);
        policy.match(master, "0004Y0", api("0004Y0", "WRONG", "STK"));
        policy.match(master, "999999", input);
        assertEquals(before, policy.match(master, "005930", input));
        assertThrows(UnsupportedOperationException.class, () -> before.masterBatch().marketResults().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.masterBatch().marketResults().getFirst().records().clear());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingRequest(String symbol) {
        assertThrows(IllegalArgumentException.class,
                () -> policy.match(master, symbol, api("005930", "KR7005930003", "STK")));
    }

    @Test
    void rejectsNullInputs() {
        var input = api("005930", "KR7005930003", "STK");
        assertThrows(NullPointerException.class, () -> policy.match(null, "005930", input));
        assertThrows(NullPointerException.class, () -> policy.match(master, "005930", null));
    }

    static Stream<String> nonComparedFields() {
        return KisStockBasicInfoParsingFixture.FIELDS.stream()
                .filter(field -> !field.equals("std_pdno") && !field.equals("mket_id_cd"));
    }

    private void assertMismatch(String standardCode, String market, KisStockBasicInfoMatchReasonCode reason) {
        var input = api("005930", standardCode, market);
        var result = policy.match(master, "005930", input);
        assertEquals(reason, result.reasonCode());
        assertEquals("005930", result.comparedRecord().symbol());
        assertSame(input, result.apiInput());
        assertEquals(standardCode, result.apiInput().rawRecord().standardCode());
        assertEquals(market, result.apiInput().rawRecord().rawMarket());
    }
}
