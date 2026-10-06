package com.stock.market.stock.master.matching.kiskrx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchReasonCode;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchingResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.market.stock.master.provider.krx.parsing.KrxStockBasicInfoParser;
import com.stock.market.stock.master.provider.krx.parsing.result.KrxStockBasicInfoParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisKrxStockIdentityMatchingPolicyTest {
    private final KisKrxStockIdentityMatchingPolicy policy = new KisKrxStockIdentityMatchingPolicy();
    private final StockMasterBatchParseResult batch = batch(
            KisStockMasterParsingFixture.row(KOSPI),
            KisStockMasterParsingFixture.row(KOSPI, "000660", "KR7000660001", "SECOND"));

    @Test
    void matchesBothMarketsAndPreservesInputsHashesCollectionAndResidualRows() {
        var inputs = inputs(stock(KOSPI, "005930", "KR7005930003"));
        inputs.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")));

        var result = policy.match(batch, inputs);

        assertThat(result.matchingVersion()).isEqualTo("KIS_KRX_STOCK_IDENTITY_MATCH_V1");
        assertThat(result.kisBatch()).isSameAs(batch);
        assertThat(result.krxInputs().get(KOSPI)).isSameAs(inputs.get(KOSPI));
        assertThat(result.krxInputs().get(KOSDAQ)).isSameAs(inputs.get(KOSDAQ));
        assertThat(result.rowResults()).extracting(KisKrxStockIdentityMatchResult::reasonCode)
                .containsExactly(KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH,
                        KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH);
        assertThat(result.rowResults().getFirst().krxRecord()).isSameAs(inputs.get(KOSPI).records().getFirst());
        assertThat(result.rowResults().getFirst().matchedKisRecord()).isSameAs(kis(KOSPI, 0));
        assertThat(result.rowResults().getLast().matchedKisRecord()).isSameAs(kis(KOSDAQ, 0));
        assertThat(result.unmatchedKisRecords().get(KOSPI)).containsExactly(kis(KOSPI, 1));
        assertThat(result.unmatchedKisRecords().get(KOSDAQ)).isEmpty();
        assertThat(result.kisBatch().collection().finishedAt()).isEqualTo(Instant.parse("2026-10-05T09:32:36Z"));
        assertThat(policy.match(batch, inputs)).isEqualTo(result);
        assertThat(new KisKrxStockIdentityMatchingPolicy().match(batch, inputs)).isEqualTo(result);
    }

    @Test
    void matchesIdentifiersWithoutCertifyingNamesTypesDatesOrTradingRestrictions() {
        var raw = stock(KOSPI, "005930", "KR7005930003")
                .put("ISU_NM", "DIFFERENT NAME ETF")
                .put("LIST_DD", "NOT_A_DATE")
                .put("SECUGRP_NM", "UNKNOWN_GROUP")
                .put("SECT_TP_NM", "UNKNOWN_SECTION")
                .put("KIND_STKCERT_TP_NM", "UNKNOWN_KIND");

        var result = policy.match(batch, inputs(raw)).rowResults().getFirst();

        assertThat(result.reasonCode()).isEqualTo(KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH);
        assertThat(result.krxRecord().rawListingDate()).isEqualTo("NOT_A_DATE");
        assertThat(result.krxRecord().rawSecurityGroup()).isEqualTo("UNKNOWN_GROUP");
        assertThat(result.matchedKisRecord().rawEtp()).isEqualTo(" ");
        assertThat(result.matchedKisRecord().rawLine()).isEqualTo(kis(KOSPI, 0).rawLine());
    }

    @ParameterizedTest
    @CsvSource(value = {"ISU_CD|''", "ISU_CD| ", "ISU_SRT_CD|''", "ISU_SRT_CD| "}, delimiter = '|',
            emptyValue = "", ignoreLeadingAndTrailingWhitespace = false)
    void defersBlankIdentifiersWithoutLosingTheRow(String field, String value) {
        assertReason(stock(KOSPI, "005930", "KR7005930003").put(field, value),
                KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_BLANK);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "ISU_CD| KR7005930003|SYMBOL_ONLY_MATCH", "ISU_CD|KR7005930003 |SYMBOL_ONLY_MATCH",
            "ISU_CD|kr7005930003|SYMBOL_ONLY_MATCH", "ISU_SRT_CD| 005930|SYMBOL_MISMATCH",
            "ISU_SRT_CD|005930 |SYMBOL_MISMATCH", "ISU_SRT_CD|5930|SYMBOL_MISMATCH"
    }, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    void doesNotTrimCaseFoldOrNumericallyConvertIdentifiers(String field, String value,
                                                            KisKrxStockIdentityMatchReasonCode reason) {
        assertReason(stock(KOSPI, "005930", "KR7005930003").put(field, value), reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "kospi", " KOSPI", "KOSPI ", "KOSDAQ", "KONEX", "UNKNOWN"})
    void requiresRawMarketToAgreeExactlyWithExplicitRequestMarket(String rawMarket) {
        assertReason(stock(KOSPI, "005930", "KR7005930003").put("MKT_TP_NM", rawMarket),
                KisKrxStockIdentityMatchReasonCode.REQUEST_MARKET_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISU_CD", "ISU_SRT_CD", "BOTH"})
    void defersEveryMemberOfADuplicateInsteadOfPickingTheFirst(String duplicateField) {
        var first = stock(KOSPI, "005930", "KR7005930003");
        var second = stock(KOSPI, "999999", "KR7999999999");
        if (!duplicateField.equals("ISU_SRT_CD")) {
            second.put("ISU_CD", "KR7005930003");
        }
        if (!duplicateField.equals("ISU_CD")) {
            second.put("ISU_SRT_CD", "005930");
        }
        var input = inputs(first, second);

        var result = policy.match(batch, input);

        assertThat(result.rowResults()).hasSize(2).allSatisfy(row -> {
            assertThat(row.reasonCode()).isEqualTo(KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED);
            assertThat(row.matchedKisRecord()).isNull();
        });
        assertAllKisUnmatched(result);
        assertThat(result.rowResults().getLast().krxRecord()).isSameAs(input.get(KOSPI).records().getLast());
    }

    @Test
    void detectsDuplicateIdentifiersAcrossMarketsEvenWhenOneRowHasWrongMarket() {
        var input = inputs(stock(KOSPI, "005930", "KR7005930003"));
        input.put(KOSDAQ, parsed(stock(KOSPI, "005930", "KR7005930003")));

        var result = policy.match(batch, input);

        assertThat(result.rowResults()).extracting(KisKrxStockIdentityMatchResult::reasonCode)
                .containsOnly(KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED);
        assertAllKisUnmatched(result);
    }

    @Test
    void reportsFirstReasonWhilePreservingAllProblemFields() {
        var input = inputs(stock(KOSDAQ, "", "KR7005930003"), stock(KOSDAQ, "005930", "KR7005930003"));

        var result = policy.match(batch, input);

        assertThat(result.rowResults()).extracting(KisKrxStockIdentityMatchResult::reasonCode)
                .containsExactly(KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_BLANK,
                        KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED);
        assertAllKisUnmatched(result);
    }

    @ParameterizedTest
    @CsvSource({
            "999999,KR7999999999,STANDARD_CODE_NOT_FOUND", "005930,KR7999999999,SYMBOL_ONLY_MATCH",
            "000660,KR7005930003,SYMBOL_MISMATCH", "999999,KR7005930003,SYMBOL_MISMATCH",
            "0001A0,KR70001A0001,MARKET_MISMATCH"
    })
    void keepsMissingAndContradictoryCounterpartsUnmatched(String symbol, String standard,
                                                          KisKrxStockIdentityMatchReasonCode reason) {
        assertReason(stock(KOSPI, symbol, standard), reason);
    }

    @Test
    void retainsAllKisRowsForEmptyKrxResponsesWithoutInferringDelisting() {
        var result = policy.match(batch, inputs());
        assertThat(result.rowResults()).isEmpty();
        assertAllKisUnmatched(result);
    }

    @Test
    void usesCanonicalMarketOrderAndRetainsSourceRowOrderRegardlessOfMapOrBatchOrder() {
        var input = inputs(stock(KOSPI, "000660", "KR7000660001"), stock(KOSPI, "005930", "KR7005930003"));
        input.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")));
        var reversedBatch = new StockMasterBatchParseResult(batch.collection(), batch.marketResults().reversed());
        var reversedMap = new LinkedHashMap<KisStockMasterMarket, KrxStockBasicInfoParseResult>();
        reversedMap.put(KOSDAQ, input.get(KOSDAQ));
        reversedMap.put(KOSPI, input.get(KOSPI));

        var result = policy.match(reversedBatch, reversedMap);

        assertThat(result.rowResults()).extracting(row -> row.krxRecord().symbol())
                .containsExactly("000660", "005930", "0001A0");
        assertThat(result.rowResults().getFirst().matchedKisRecord()).isSameAs(kis(KOSPI, 1));
    }

    @Test
    void snapshotsCallerContainersAndMakesResultCollectionsImmutable() {
        var input = inputs(stock(KOSPI, "005930", "KR7005930003"));
        var result = policy.match(batch, input);
        var savedInput = input.get(KOSPI);
        input.clear();

        assertThat(result.krxInputs().get(KOSPI)).isSameAs(savedInput);
        assertThatThrownBy(() -> result.krxInputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.rowResults().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unmatchedKisRecords().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unmatchedKisRecords().get(KOSPI).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingInputsMarketsOrNullResponsesBeforeMatching() {
        assertThatThrownBy(() -> policy.match(null, inputs())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.match(batch, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.match(batch, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.match(batch, Map.of(KOSPI, parsed()))).isInstanceOf(IllegalArgumentException.class);
        var input = inputs();
        input.put(KOSDAQ, null);
        assertThatThrownBy(() -> policy.match(batch, input)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void reusesExistingKisParserAndBatchDuplicateRejection() {
        assertThatThrownBy(() -> batch(KisStockMasterParsingFixture.row(KOSPI), KisStockMasterParsingFixture.row(KOSPI)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> batch(KisStockMasterParsingFixture.row(KOSPI),
                KisStockMasterParsingFixture.row(KOSPI, "005930", "KR7999999999", "DUPLICATE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate symbol");
        assertThatThrownBy(() -> batch(KisStockMasterParsingFixture.row(KOSPI),
                KisStockMasterParsingFixture.row(KOSPI, "999999", "KR7005930003", "DUPLICATE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate standardCode");
        assertThatThrownBy(() -> batch(KisStockMasterParsingFixture.row(KOSPI, "0001A0", "KR7999999999", "CROSS_MARKET")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("collision");
        assertThatThrownBy(() -> batch(KisStockMasterParsingFixture.row(KOSPI, "999999", "KR70001A0001", "CROSS_MARKET")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("collision");
    }

    @ParameterizedTest
    @EnumSource(value = KisKrxStockIdentityMatchReasonCode.class, names = "EXACT_IDENTITY_MATCH", mode = EnumSource.Mode.EXCLUDE)
    void nonmatchesNeverExposeAMatchedKisRecord(KisKrxStockIdentityMatchReasonCode reason) {
        var raw = parsed(stock(KOSPI, "005930", "KR7005930003")).records().getFirst();
        assertThat(new KisKrxStockIdentityMatchResult(KOSPI, raw, null, reason).krxRecord()).isSameAs(raw);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(KOSPI, raw, kis(KOSPI, 0), reason))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rowResultRejectsNullsMissingMatchesAndIdentifierOrRequestMarketContradictions() {
        var raw = parsed(stock(KOSPI, "005930", "KR7005930003")).records().getFirst();
        var exact = KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH;
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(null, raw, null, exact)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(KOSPI, null, null, exact)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(KOSPI, raw, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(KOSPI, raw, null, exact)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(KOSPI, raw, kis(KOSPI, 1), exact))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchResult(KOSDAQ, raw, kis(KOSPI, 0), exact))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchResultRejectsMissingAddedReorderedOrAlteredKrxRows() {
        var result = policy.match(batch, inputs(stock(KOSPI, "005930", "KR7005930003"),
                stock(KOSPI, "000660", "KR7000660001")));
        assertThatThrownBy(() -> rebuild(result, List.of(), result.unmatchedKisRecords())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rebuild(result, result.rowResults().reversed(), result.unmatchedKisRecords()))
                .isInstanceOf(IllegalArgumentException.class);
        var added = new ArrayList<>(result.rowResults());
        added.add(result.rowResults().getFirst());
        assertThatThrownBy(() -> rebuild(result, added, result.unmatchedKisRecords())).isInstanceOf(IllegalArgumentException.class);
        var cloned = new ArrayList<>(result.rowResults());
        var first = cloned.getFirst();
        var copiedRaw = parsed(stock(KOSPI, "005930", "KR7005930003").put("ISU_NM", "ALTERED_NAME")).records().getFirst();
        cloned.set(0, new KisKrxStockIdentityMatchResult(KOSPI, copiedRaw, first.matchedKisRecord(), first.reasonCode()));
        assertThatThrownBy(() -> rebuild(result, cloned, result.unmatchedKisRecords())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchResultRejectsLostAddedReorderedOrAlteredResidualKisRows() {
        var result = policy.match(batch, inputs());
        for (var rows : List.of(List.<KisStockMasterRawRecord>of(), List.of(kis(KOSPI, 0)),
                List.of(kis(KOSPI, 0), kis(KOSPI, 1), kis(KOSPI, 0)), List.of(kis(KOSPI, 1), kis(KOSPI, 0)))) {
            assertThatThrownBy(() -> rebuild(result, result.rowResults(), Map.of(KOSPI, rows, KOSDAQ, List.of(kis(KOSDAQ, 0)))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var clonedKis = new KisStockMasterParser().parse(KOSPI,
                KisStockMasterParsingFixture.content(KisStockMasterParsingFixture.row(KOSPI, "005930", "KR7005930003", "ALTERED_NAME")))
                .records().getFirst();
        assertThatThrownBy(() -> rebuild(result, result.rowResults(),
                Map.of(KOSPI, List.of(clonedKis, kis(KOSPI, 1)), KOSDAQ, List.of(kis(KOSDAQ, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchResultRequiresAMatchedKisRecordFromTheActualInputMarket() {
        var result = policy.match(batch, inputs(stock(KOSPI, "005930", "KR7005930003")));
        var first = result.rowResults().getFirst();
        var reconstructed = new KisStockMasterParser().parse(KOSPI,
                KisStockMasterParsingFixture.content(KisStockMasterParsingFixture.row(KOSPI, "005930", "KR7005930003", "ALTERED_NAME")))
                .records().getFirst();
        var rowResult = new KisKrxStockIdentityMatchResult(KOSPI, first.krxRecord(), reconstructed, first.reasonCode());
        assertThatThrownBy(() -> rebuild(result, List.of(rowResult), result.unmatchedKisRecords()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchResultRejectsMultipleMatchesToTheSameKisRecord() {
        var input = inputs(stock(KOSPI, "005930", "KR7005930003"), stock(KOSPI, "005930", "KR7005930003"));
        var rows = input.get(KOSPI).records().stream().map(raw -> new KisKrxStockIdentityMatchResult(KOSPI, raw,
                kis(KOSPI, 0), KisKrxStockIdentityMatchReasonCode.EXACT_IDENTITY_MATCH)).toList();
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchingResult(KisKrxStockIdentityMatchingPolicy.MATCHING_VERSION,
                batch, input, rows, Map.of(KOSPI, List.of(kis(KOSPI, 1)), KOSDAQ, List.of(kis(KOSDAQ, 0)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unique");
    }

    @Test
    void batchResultRejectsMissingMetadataMarketsOrNullCollectionElements() {
        var result = policy.match(batch, inputs());
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchingResult(" ", batch, result.krxInputs(),
                result.rowResults(), result.unmatchedKisRecords())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchingResult(result.matchingVersion(), null, result.krxInputs(),
                result.rowResults(), result.unmatchedKisRecords())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockIdentityMatchingResult(result.matchingVersion(), batch, Map.of(KOSPI, parsed()),
                result.rowResults(), result.unmatchedKisRecords())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rebuild(result, result.rowResults(), Map.of(KOSPI, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        var nullRows = new ArrayList<KisKrxStockIdentityMatchResult>();
        nullRows.add(null);
        assertThatThrownBy(() -> rebuild(result, nullRows, result.unmatchedKisRecords())).isInstanceOf(NullPointerException.class);
        var nullResidual = new EnumMap<KisStockMasterMarket, List<KisStockMasterRawRecord>>(KisStockMasterMarket.class);
        nullResidual.put(KOSPI, null);
        assertThatThrownBy(() -> rebuild(result, result.rowResults(), nullResidual)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void batchResultCopiesNestedCallerListsWithoutRebuildingRecords() {
        var result = policy.match(batch, inputs());
        var residual = new EnumMap<KisStockMasterMarket, List<KisStockMasterRawRecord>>(KisStockMasterMarket.class);
        result.unmatchedKisRecords().forEach((market, records) -> residual.put(market, new ArrayList<>(records)));
        var copied = rebuild(result, result.rowResults(), residual);
        residual.get(KOSPI).clear();
        residual.clear();
        assertAllKisUnmatched(copied);
    }

    @Test
    void acceptsValuePreservingJsonRoundTripWithoutRelyingOnObjectAddresses() throws Exception {
        var result = policy.match(batch, inputs(stock(KOSPI, "005930", "KR7005930003")));
        var json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var restored = json.readValue(json.writeValueAsBytes(result), KisKrxStockIdentityMatchingResult.class);

        assertThat(restored).isEqualTo(result);
        assertThat(restored.rowResults().getFirst().krxRecord()).isNotSameAs(result.rowResults().getFirst().krxRecord());
        assertThat(restored.unmatchedKisRecords().get(KOSPI)).containsExactly(kis(KOSPI, 1));
    }

    private void assertReason(ObjectNode row, KisKrxStockIdentityMatchReasonCode reason) {
        var input = inputs(row);
        var result = policy.match(batch, input);
        assertThat(result.rowResults()).hasSize(1);
        assertThat(result.rowResults().getFirst().reasonCode()).isEqualTo(reason);
        assertThat(result.rowResults().getFirst().matchedKisRecord()).isNull();
        assertThat(result.rowResults().getFirst().krxRecord()).isSameAs(input.get(KOSPI).records().getFirst());
        assertAllKisUnmatched(result);
    }

    private void assertAllKisUnmatched(KisKrxStockIdentityMatchingResult result) {
        for (var input : batch.marketResults()) {
            assertThat(result.unmatchedKisRecords().get(input.market())).containsExactlyElementsOf(input.records());
        }
    }

    private KisStockMasterRawRecord kis(KisStockMasterMarket market, int index) {
        return batch.marketResults().stream().filter(input -> input.market() == market).findFirst().orElseThrow().records().get(index);
    }

    private static KisKrxStockIdentityMatchingResult rebuild(KisKrxStockIdentityMatchingResult source,
                                                            List<KisKrxStockIdentityMatchResult> rows,
                                                            Map<KisStockMasterMarket, List<KisStockMasterRawRecord>> unmatched) {
        return new KisKrxStockIdentityMatchingResult(source.matchingVersion(), source.kisBatch(), source.krxInputs(), rows, unmatched);
    }

    private static EnumMap<KisStockMasterMarket, KrxStockBasicInfoParseResult> inputs(ObjectNode... kospi) {
        var inputs = new EnumMap<KisStockMasterMarket, KrxStockBasicInfoParseResult>(KisStockMasterMarket.class);
        inputs.put(KOSPI, parsed(kospi));
        inputs.put(KOSDAQ, parsed());
        return inputs;
    }

    private static KrxStockBasicInfoParseResult parsed(ObjectNode... rows) {
        return new KrxStockBasicInfoParser().parse(content(rows));
    }

    private static ObjectNode stock(KisStockMasterMarket market, String symbol, String standardCode) {
        return row().put("MKT_TP_NM", market.name()).put("ISU_SRT_CD", symbol).put("ISU_CD", standardCode);
    }

    private static StockMasterBatchParseResult batch(byte[]... kospiRows) {
        byte[] kospiBytes = KisStockMasterParsingFixture.content(kospiRows);
        byte[] kosdaqBytes = KisStockMasterParsingFixture.content(
                KisStockMasterParsingFixture.row(KOSDAQ, "0001A0", "KR70001A0001", "ALPHA"));
        var parser = new KisStockMasterParser();
        var kospi = parser.parse(KOSPI, kospiBytes);
        var kosdaq = parser.parse(KOSDAQ, kosdaqBytes);
        var at = Instant.parse("2026-10-05T09:32:36Z");
        // Synthetic collection metadata connects parser hashes without filesystem or HTTP access.
        var collection = new StockMasterCollectionResult(1, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CURRENT_OBSERVATION", at, at, List.of(observation(kospi, kospiBytes.length, at),
                observation(kosdaq, kosdaqBytes.length, at)));
        return new StockMasterBatchParseResult(collection, List.of(kospi, kosdaq));
    }

    private static StockMasterCollectionResult.FileObservation observation(KisStockMasterParseResult parsed, int bytes, Instant at) {
        var market = parsed.market();
        return new StockMasterCollectionResult.FileObservation(market, market.sourceUri(), at, at,
                market.name() + "/" + market.fileName() + ".zip", 1, "f".repeat(64),
                market.name() + "/" + market.fileName(), bytes, parsed.inputSha256());
    }
}
