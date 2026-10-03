package com.stock.strategy.universe.liquidity.evaluation.snapshot.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.DailyTradingValueAverageCalculator;
import com.stock.strategy.universe.liquidity.evaluation.DailyTradingValueSelectionEvaluationService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.ranking.DailyTradingValueRankingPolicy;
import com.stock.strategy.universe.liquidity.selection.DailyTradingValueSelectionPolicy;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionSnapshotJsonConverterTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate SECOND_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final List<LocalDate> TRADING_DATES = List.of(FIRST_DATE, SECOND_DATE, SELECTION_DATE);
    private static final TradingVenueScope VENUE = TradingVenueScope.INTEGRATED;
    private static final String DESERIALIZATION_FAILURE =
            "Failed to deserialize daily trading value selection snapshot json.";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final DailyTradingValueSelectionSnapshotJsonConverter converter =
            new DailyTradingValueSelectionSnapshotJsonConverter(objectMapper);
    private final DailyTradingValueSelectionEvaluationService evaluationService =
            new DailyTradingValueSelectionEvaluationService(
                    new DailyTradingValueAverageCalculator(),
                    new DailyTradingValueSelectionPolicy(new DailyTradingValueRankingPolicy())
            );

    @Test
    void restoresFixedVersionOneDocumentWithoutRelyingOnCurrentSerializer() {
        String json = """
                {
                  "schemaVersion": 1,
                  "evaluationResult": {
                    "request": {
                      "targetSymbols": ["005930"],
                      "selectionAsOfDate": "2026-09-23",
                      "requiredTradingDates": ["2026-09-23"],
                      "expectedVenueScope": "INTEGRATED",
                      "minimumAverageTradingValueKrw": 100,
                      "maxCandidateCount": 1
                    },
                    "status": "INCOMPLETE",
                    "inputHistories": [{
                      "symbol": "005930",
                      "bars": [{
                        "tradingDate": "2026-09-23",
                        "openPriceKrw": 100,
                        "highPriceKrw": 100,
                        "lowPriceKrw": 100,
                        "closePriceKrw": 100,
                        "volume": 1,
                        "tradingValueKrw": null,
                        "tradingVenueScope": "INTEGRATED"
                      }]
                    }],
                    "calculatedAverages": [],
                    "unverifiedSymbols": ["005930"],
                    "selectionResults": []
                  }
                }
                """;
        DailyTradingValueSelectionEvaluationResult expected = evaluationService.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("005930"), SELECTION_DATE, List.of(SELECTION_DATE), VENUE, 100L, 1
                ), List.of(history("005930", bar(SELECTION_DATE, null, VENUE)))
        );

        DailyTradingValueSelectionSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(DailyTradingValueSelectionSnapshot.from(expected));
    }

    @Test
    void roundTripsCompleteEvaluationIncludingExcludedCandidatesAndKnownZero() throws Exception {
        DailyTradingValueSelectionSnapshot snapshot = completeSnapshot();

        String json = converter.toJson(snapshot);
        DailyTradingValueSelectionSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.evaluationResult().selectionResults()).extracting(selection -> selection.status())
                .containsExactly(DailyTradingValueSelectionStatus.SELECTED, DailyTradingValueSelectionStatus.SELECTED,
                        DailyTradingValueSelectionStatus.CANDIDATE_LIMIT,
                        DailyTradingValueSelectionStatus.LIQUIDITY_BELOW_MINIMUM);
        JsonNode tree = objectMapper.readTree(json);
        assertThat(tree.get("schemaVersion").intValue()).isEqualTo(1);
        assertThat(tree.at("/evaluationResult/request/selectionAsOfDate").textValue()).isEqualTo("2026-09-23");
        assertThat(tree.at("/evaluationResult/inputHistories/0/symbol").textValue()).isEqualTo("000660");
        assertThat(tree.at("/evaluationResult/inputHistories/1/bars/0/tradingValueKrw").longValue()).isZero();
        assertThat(tree.at("/evaluationResult/inputHistories/0/bars/0/tradingDate").textValue())
                .isEqualTo("2026-09-21");
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void roundTripsIncompleteEvaluationWithoutReplacingEmptyMissingOrNullableInputs(
            boolean missingValue, boolean missingVenue
    ) throws Exception {
        DailyTradingValueSelectionSnapshot snapshot = incompleteSnapshot(missingValue, missingVenue);

        String json = converter.toJson(snapshot);
        DailyTradingValueSelectionSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.evaluationResult().status()).isEqualTo(DailyTradingValueSelectionEvaluationStatus.INCOMPLETE);
        assertThat(restored.evaluationResult().inputHistories()).extracting(DailyPriceHistory::symbol)
                .containsExactly("000660", "005380", "005930");
        assertThat(restored.evaluationResult().inputHistories().getFirst().bars()).isEmpty();
        assertThat(restored.evaluationResult().unverifiedSymbols()).containsExactly("000660", "005380", "035420");
        assertThat(restored.evaluationResult().selectionResults()).isEmpty();
        JsonNode unknownBar = objectMapper.readTree(json).at("/evaluationResult/inputHistories/1/bars/1");
        assertThat(unknownBar.has("tradingValueKrw")).isTrue();
        assertThat(unknownBar.get("tradingValueKrw").isNull()).isEqualTo(missingValue);
        assertThat(unknownBar.has("tradingVenueScope")).isTrue();
        assertThat(unknownBar.get("tradingVenueScope").isNull()).isEqualTo(missingVenue);
    }

    @Test
    void roundTripsAllMissingEvaluationWithoutFabricatingHistories() {
        DailyTradingValueSelectionSnapshot snapshot = snapshot(List.of());

        DailyTradingValueSelectionSnapshot restored = converter.fromJson(converter.toJson(snapshot));

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.evaluationResult().inputHistories()).isEmpty();
        assertThat(restored.evaluationResult().unverifiedSymbols())
                .containsExactlyElementsOf(restored.evaluationResult().request().targetSymbols());
    }

    @Test
    void preservesIntegerTotalsBeyondLongRangeExactly() throws Exception {
        DailyTradingValueSelectionSnapshot snapshot = DailyTradingValueSelectionSnapshot.from(evaluationService.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("005930"), SELECTION_DATE, TRADING_DATES, VENUE, Long.MAX_VALUE, 1
                ), List.of(completeHistory("005930", Long.MAX_VALUE))
        ));
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3L));

        String json = converter.toJson(snapshot);
        DailyTradingValueSelectionSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.evaluationResult().calculatedAverages().getFirst().totalTradingValueKrw()).isEqualTo(expected);
        assertThat(objectMapper.readTree(json).at("/evaluationResult/calculatedAverages/0/totalTradingValueKrw")
                .bigIntegerValue()).isEqualTo(expected);
        assertThat(evaluationService.evaluate(restored.evaluationResult().request(),
                restored.evaluationResult().inputHistories())).isEqualTo(snapshot.evaluationResult());
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void restoredInputsReproduceOriginalEvaluationUsingExistingPolicies(DailyTradingValueSelectionEvaluationStatus status) {
        DailyTradingValueSelectionSnapshot original = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();
        DailyTradingValueSelectionSnapshot restored = converter.fromJson(converter.toJson(original));

        DailyTradingValueSelectionEvaluationResult replayed = evaluationService.evaluate(
                restored.evaluationResult().request(), restored.evaluationResult().inputHistories()
        );

        assertThat(replayed).isEqualTo(original.evaluationResult());
        assertThatThrownBy(restored.evaluationResult().inputHistories()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored.evaluationResult().inputHistories().getLast().bars()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored.evaluationResult().request().targetSymbols()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void preservesExplicitVenueAndNonDefaultCriteria(TradingVenueScope venue) {
        DailyTradingValueSelectionSnapshot original = DailyTradingValueSelectionSnapshot.from(evaluationService.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("005930"), SELECTION_DATE, List.of(SELECTION_DATE), venue, 123L, 7
                ), List.of(history("005930", bar(SELECTION_DATE, 123L, venue)))
        ));

        DailyTradingValueSelectionSnapshot restored = converter.fromJson(converter.toJson(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.evaluationResult().request().expectedVenueScope()).isEqualTo(venue);
        assertThat(restored.evaluationResult().request().minimumAverageTradingValueKrw()).isEqualTo(123L);
        assertThat(restored.evaluationResult().request().maxCandidateCount()).isEqualTo(7);
    }

    @Test
    void preservesUnusedBarsWithoutUsingThemDuringReplay() {
        DailyTradingValueSelectionSnapshot original = DailyTradingValueSelectionSnapshot.from(evaluationService.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("005930"), SELECTION_DATE, TRADING_DATES, VENUE, 100L, 1
                ), List.of(history("005930", bar(FIRST_DATE.minusDays(1), 999L, TradingVenueScope.KRX),
                        bar(FIRST_DATE, 100L, VENUE), bar(SECOND_DATE, 100L, VENUE),
                        bar(SELECTION_DATE, 100L, VENUE), bar(SELECTION_DATE.plusDays(1), null, null)))
        ));

        DailyTradingValueSelectionSnapshot restored = converter.fromJson(converter.toJson(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.evaluationResult().inputHistories().getFirst().bars()).hasSize(5);
        assertThat(restored.evaluationResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(300L));
        assertThat(evaluationService.evaluate(restored.evaluationResult().request(),
                restored.evaluationResult().inputHistories())).isEqualTo(original.evaluationResult());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-json", "{}", "[]", "null"})
    void rejectsInvalidOrEmptyDocumentRatherThanReturningDefaultSnapshot(String json) {
        assertThatThrownBy(() -> converter.fromJson(json)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, Integer.MAX_VALUE})
    void rejectsUnsupportedFormatVersions(int version) throws Exception {
        ObjectNode tree = completeTree();
        tree.put("schemaVersion", version);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", ";evaluationResult",
            "/evaluationResult;request", "/evaluationResult;status", "/evaluationResult;inputHistories",
            "/evaluationResult;calculatedAverages", "/evaluationResult;unverifiedSymbols",
            "/evaluationResult;selectionResults", "/evaluationResult/request;targetSymbols",
            "/evaluationResult/request;selectionAsOfDate", "/evaluationResult/request;requiredTradingDates",
            "/evaluationResult/request;expectedVenueScope", "/evaluationResult/request;minimumAverageTradingValueKrw",
            "/evaluationResult/request;maxCandidateCount", "/evaluationResult/inputHistories/0;symbol",
            "/evaluationResult/inputHistories/0;bars", "/evaluationResult/inputHistories/0/bars/0;volume",
            "/evaluationResult/inputHistories/0/bars/0;tradingValueKrw",
            "/evaluationResult/inputHistories/0/bars/0;tradingVenueScope"
        }, delimiter = ';')
    void rejectsMissingFieldsIncludingZeroCapablePrimitiveAndNullableMetadata(String parentPointer, String field)
            throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at(parentPointer == null ? "" : parentPointer)).remove(field);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", ";evaluationResult",
            "/evaluationResult;request", "/evaluationResult;status", "/evaluationResult;inputHistories",
            "/evaluationResult;calculatedAverages", "/evaluationResult;unverifiedSymbols",
            "/evaluationResult;selectionResults", "/evaluationResult/inputHistories/0/bars/0;volume"
        }, delimiter = ';')
    void rejectsNullRequiredFieldsWithoutReplacingThemWithZeroOrEmptyList(String parentPointer, String field)
            throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at(parentPointer == null ? "" : parentPointer)).putNull(field);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", "/evaluationResult/request;minimumAverageTradingValueKrw",
            "/evaluationResult/inputHistories/0/bars/0;volume",
            "/evaluationResult/calculatedAverages/0;totalTradingValueKrw"
        }, delimiter = ';')
    void rejectsFractionalIntegersRatherThanTruncatingThem(String parentPointer, String field) throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at(parentPointer == null ? "" : parentPointer)).put(field, 1.5);

        assertRejected(tree);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "01", ""})
    void rejectsStringVersionRatherThanCoercingItToInteger(String version) throws Exception {
        ObjectNode tree = completeTree();
        tree.put("schemaVersion", version);

        assertRejected(tree);
    }

    @Test
    void rejectsNumericSymbolRatherThanLosingLeadingZero() throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at("/evaluationResult/request")).withArray("targetSymbols").set(0,
                objectMapper.getNodeFactory().numberNode(660));
        ((ObjectNode) tree.at("/evaluationResult/inputHistories/0")).put("symbol", 660);
        ((ObjectNode) tree.at("/evaluationResult/calculatedAverages/0")).put("symbol", 660);
        ((ObjectNode) tree.at("/evaluationResult/selectionResults/1/average")).put("symbol", 660);

        assertRejected(tree);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/evaluationResult", "/evaluationResult/inputHistories/0/bars/0"})
    void rejectsUnknownEnumEvenForNullableMetadata(String parentPointer) throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at(parentPointer)).put(parentPointer.equals("/evaluationResult")
                ? "status" : "tradingVenueScope", "UNKNOWN");

        assertRejected(tree);
    }

    @Test
    void rejectsNumericEnumRatherThanUsingOrdinal() throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at("/evaluationResult")).put("status", 0);

        assertRejected(tree);
    }

    @Test
    void rejectsUnknownFieldsRatherThanSilentlyDroppingEvidence() throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at("/evaluationResult")).put("unknownEvidence", "must not be dropped");

        assertRejected(tree);
    }

    @Test
    void rejectsDuplicateFieldsRatherThanChoosingOneValue() {
        String validJson = converter.toJson(completeSnapshot());
        String json = "{\"schemaVersion\":1," + validJson.substring(1);

        assertThatThrownBy(() -> converter.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(DESERIALIZATION_FAILURE)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsTrailingDocumentRatherThanIgnoringIt() {
        String json = converter.toJson(completeSnapshot()) + " {}";

        assertThatThrownBy(() -> converter.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(DESERIALIZATION_FAILURE);
    }

    @Test
    void existingResultValidationStillRejectsMissingTargetClassification() throws Exception {
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at("/evaluationResult/request")).withArray("targetSymbols").add("999999");

        assertRejected(tree);
    }

    @Test
    void restoringSnapshotDoesNotReplaceArithmeticVerificationByExistingEvaluator() throws Exception {
        DailyTradingValueSelectionSnapshot original = completeSnapshot();
        ObjectNode tree = completeTree();
        ((ObjectNode) tree.at("/evaluationResult/calculatedAverages/0")).put("totalTradingValueKrw", 601);
        ((ObjectNode) tree.at("/evaluationResult/selectionResults/1/average")).put("totalTradingValueKrw", 601);

        DailyTradingValueSelectionSnapshot restored = converter.fromJson(objectMapper.writeValueAsString(tree));
        DailyTradingValueSelectionEvaluationResult recalculated = evaluationService.evaluate(
                restored.evaluationResult().request(), restored.evaluationResult().inputHistories()
        );

        assertThat(restored.evaluationResult()).isNotEqualTo(original.evaluationResult());
        assertThat(recalculated).isEqualTo(original.evaluationResult());
        assertThat(recalculated).isNotEqualTo(restored.evaluationResult());
    }

    @Test
    void preservesSerializationFailureCauseInsteadOfReturningPartialJson() {
        DailyTradingValueSelectionSnapshotJsonConverter withoutDateModule =
                new DailyTradingValueSelectionSnapshotJsonConverter(new ObjectMapper());

        assertThatThrownBy(() -> withoutDateModule.toJson(completeSnapshot()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to serialize daily trading value selection snapshot.")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionSnapshotJsonConverter(null))
                .isInstanceOf(NullPointerException.class).hasMessage("objectMapper must not be null.");
        assertThatThrownBy(() -> converter.toJson(null))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshot must not be null.");
        assertThatThrownBy(() -> converter.fromJson(null))
                .isInstanceOf(NullPointerException.class).hasMessage("json must not be null.");
    }

    @Test
    void usesLocalSettingsWithoutChangingSharedMapperAndKeepsExplicitNullFields() throws Exception {
        ObjectMapper shared = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        DailyTradingValueSelectionSnapshotJsonConverter localConverter =
                new DailyTradingValueSelectionSnapshotJsonConverter(shared);
        DailyTradingValueSelectionSnapshot original = incompleteSnapshot();

        String json = localConverter.toJson(original);

        assertThat(localConverter.fromJson(json)).isEqualTo(original);
        assertThat(shared.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(shared.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)).isTrue();
        assertThat(shared.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)).isTrue();
        assertThat(shared.readValue("[1]", Integer.class)).isEqualTo(1);
        assertThat(shared.readValue("\"1\"", Integer.class)).isEqualTo(1);
        assertThat(shared.readValue("660", String.class)).isEqualTo("660");
        assertThat(shared.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isTrue();
        assertThat(shared.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion())
                .isEqualTo(JsonInclude.Include.NON_NULL);
        ObjectNode unknown = (ObjectNode) shared.readTree(json);
        ((ObjectNode) unknown.at("/evaluationResult/inputHistories/1/bars/1"))
                .put("tradingVenueScope", "UNKNOWN");
        assertThatThrownBy(() -> localConverter.fromJson(shared.writeValueAsString(unknown)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(DESERIALIZATION_FAILURE);
        assertThatThrownBy(() -> localConverter.fromJson("[" + json + "]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(DESERIALIZATION_FAILURE);
    }

    @Test
    void rejectsScalarListsEvenIfSharedMapperAllowsSingleValueAsArray() throws Exception {
        ObjectMapper shared = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        DailyTradingValueSelectionSnapshotJsonConverter localConverter =
                new DailyTradingValueSelectionSnapshotJsonConverter(shared);
        DailyTradingValueSelectionSnapshot original = DailyTradingValueSelectionSnapshot.from(evaluationService.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("005930"), SELECTION_DATE, TRADING_DATES, VENUE, 100L, 1
                ), List.of()
        ));
        ObjectNode tree = (ObjectNode) shared.readTree(localConverter.toJson(original));
        ((ObjectNode) tree.at("/evaluationResult/request")).put("targetSymbols", "005930");
        ((ObjectNode) tree.at("/evaluationResult")).put("unverifiedSymbols", "005930");

        assertThatThrownBy(() -> localConverter.fromJson(shared.writeValueAsString(tree)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(DESERIALIZATION_FAILURE);
    }

    private void assertRejected(JsonNode tree) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(tree);
        assertThatThrownBy(() -> converter.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(DESERIALIZATION_FAILURE)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    private ObjectNode completeTree() throws JsonProcessingException {
        return (ObjectNode) objectMapper.readTree(converter.toJson(completeSnapshot()));
    }

    private DailyTradingValueSelectionSnapshot completeSnapshot() {
        return snapshot(List.of(completeHistory("005930", 300L), completeHistory("000660", 200L),
                completeHistory("035420", 150L), completeHistory("005380", 0L)));
    }

    private DailyTradingValueSelectionSnapshot incompleteSnapshot() {
        return incompleteSnapshot(true, true);
    }

    private DailyTradingValueSelectionSnapshot incompleteSnapshot(boolean missingValue, boolean missingVenue) {
        return snapshot(List.of(completeHistory("005930", 100L), history("000660"), history(
                "005380", bar(FIRST_DATE, 100L, VENUE),
                bar(SECOND_DATE, missingValue ? null : 0L, missingVenue ? null : VENUE),
                bar(SELECTION_DATE, 100L, VENUE)
        )));
    }

    private DailyTradingValueSelectionSnapshot snapshot(List<DailyPriceHistory> histories) {
        return DailyTradingValueSelectionSnapshot.from(evaluationService.evaluate(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("035420", "005930", "000660", "005380"),
                        SELECTION_DATE, TRADING_DATES, VENUE, 100L, 2
                ), histories
        ));
    }

    private DailyPriceHistory completeHistory(String symbol, long value) {
        return history(symbol, bar(FIRST_DATE, value, VENUE), bar(SECOND_DATE, value, VENUE),
                bar(SELECTION_DATE, value, VENUE));
    }

    private DailyPriceHistory history(String symbol, DailyPriceBar... bars) {
        return new DailyPriceHistory(symbol, List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope venue) {
        return new DailyPriceBar(date, 100L, 100L, 100L, 100L, 1L, value, venue);
    }
}
