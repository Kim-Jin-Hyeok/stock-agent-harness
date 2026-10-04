package com.stock.strategy.universe.candidate.evaluation.snapshot.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockListingStatus;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.eligibility.result.StockEligibilityReasonCode;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATES;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligibilityRequest;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.input;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCandidateEvaluationSnapshotJsonConverterTest {
    private static final String FAILURE = "Failed to deserialize stock candidate evaluation snapshot json.";

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final StockCandidateEvaluationSnapshotJsonConverter converter =
            new StockCandidateEvaluationSnapshotJsonConverter(mapper);

    @Test
    void restoresFixedVersionOneDocumentWithoutRelyingOnCurrentSerializer() {
        String json = """
                {
                  "schemaVersion": 1,
                  "evaluationResult": {
                    "request": {
                      "eligibilityRequest": {
                        "selectionAsOfDate": "2026-09-23",
                        "selectionCutoffAt": "2026-09-23T09:00:00Z",
                        "eligibleMarkets": ["KOSPI"],
                        "eligibleSecurityTypes": ["COMMON_STOCK"]
                      },
                      "liquidityRequest": {
                        "targetSymbols": ["005930"],
                        "selectionAsOfDate": "2026-09-23",
                        "requiredTradingDates": ["2026-09-23"],
                        "expectedVenueScope": "INTEGRATED",
                        "minimumAverageTradingValueKrw": 100,
                        "maxCandidateCount": 2
                      }
                    },
                    "status": "INCOMPLETE",
                    "eligibilityResults": [{
                      "request": {
                        "selectionAsOfDate": "2026-09-23",
                        "selectionCutoffAt": "2026-09-23T09:00:00Z",
                        "eligibleMarkets": ["KOSPI"],
                        "eligibleSecurityTypes": ["COMMON_STOCK"]
                      },
                      "input": {
                        "symbol": "005930",
                        "asOfDate": null,
                        "market": null,
                        "securityType": null,
                        "listingStatus": null,
                        "sourceReference": null,
                        "informationAvailableAt": null,
                        "evidenceStatus": "UNVERIFIED"
                      },
                      "status": "DATA_UNVERIFIED",
                      "reasonCode": "AS_OF_DATE_UNVERIFIED"
                    }],
                    "inputHistories": [],
                    "liquidityResult": null
                  }
                }
                """;
        StockCandidateEvaluationRequest criteria = new StockCandidateEvaluationRequest(eligibilityRequest(),
                new DailyTradingValueSelectionEvaluationRequest(List.of("005930"), DATE, List.of(DATE),
                        TradingVenueScope.INTEGRATED, 100L, 2));
        StockCandidateEvaluationSnapshot expected = StockCandidateEvaluationSnapshot.from(
                service().evaluate(criteria, List.of(), List.of()));

        assertThat(converter.fromJson(json)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"selected", "all-ineligible", "eligibility-incomplete", "liquidity-incomplete",
            "below-minimum", "all-missing"})
    void roundTripsEveryCompletionPathAndReplaysPreservedInputsUnderSamePolicies(String scenario) {
        StockCandidateEvaluationSnapshot original = snapshotFor(scenario);
        StockCandidateEvaluationSnapshot restored = converter.fromJson(converter.toJson(original));
        StockCandidateEvaluationResult result = restored.evaluationResult();

        assertThat(restored).isEqualTo(original);
        assertThat(result.candidateSymbols()).isEqualTo(original.evaluationResult().candidateSymbols());
        assertThat(result.unverifiedSymbols()).isEqualTo(original.evaluationResult().unverifiedSymbols());
        assertThat(service().evaluate(result.request(),
                result.eligibilityResults().stream().map(StockEligibilityResult::input).toList(),
                result.inputHistories())).isEqualTo(original.evaluationResult());
        assertThatThrownBy(result.eligibilityResults()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.request().eligibilityRequest().eligibleMarkets()::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.inputHistories()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result.request().targetSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesOriginalTargetsExcludedEvidenceKnownZeroAndBothHistoryScopes() throws Exception {
        StockCandidateEvaluationSnapshot original = snapshotFor("selected");
        String json = converter.toJson(original);
        StockCandidateEvaluationResult result = converter.fromJson(json).evaluationResult();

        assertThat(result.candidateSymbols()).containsExactly("005930", "000660");
        assertThat(result.eligibilityResults().getLast().input().securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.eligibilityResults().getLast().reasonCode())
                .isEqualTo(StockEligibilityReasonCode.UNSUPPORTED_SECURITY_TYPE);
        assertThat(result.eligibilityResults().getFirst().input().sourceReference()).isEqualTo("synthetic-source-01");
        assertThat(result.request().targetSymbols()).containsExactly("000660", "005380", "005930", "035420", "069500");
        assertThat(result.inputHistories()).hasSize(5);
        assertThat(result.liquidityResult().inputHistories()).hasSize(4);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.at("/evaluationResult/inputHistories/1/bars/0/tradingValueKrw").longValue()).isZero();
        assertThat(tree.at("/evaluationResult/inputHistories/4/symbol").textValue()).isEqualTo("069500");
        assertThat(tree.at("/evaluationResult/request/eligibilityRequest/selectionCutoffAt").textValue())
                .isEqualTo(CUTOFF.toString());
        assertThat(tree.at("/evaluationResult/request/liquidityRequest/selectionAsOfDate").textValue())
                .isEqualTo(DATE.toString());
        assertThat(tree.at("/evaluationResult").has("candidateSymbols")).isFalse();
        assertThat(tree.at("/evaluationResult").has("unverifiedSymbols")).isFalse();
    }

    @Test
    void preservesExplicitNullUnknownEligibilityAndSkippedLiquidityRatherThanOmittingFields() throws Exception {
        String json = converter.toJson(snapshotFor("all-missing"));
        JsonNode tree = mapper.readTree(json);
        JsonNode unknown = tree.at("/evaluationResult/eligibilityResults/0/input");

        for (String field : List.of("asOfDate", "market", "securityType", "listingStatus", "sourceReference",
                "informationAvailableAt")) {
            assertThat(unknown.has(field)).isTrue();
            assertThat(unknown.get(field).isNull()).isTrue();
        }
        assertThat(unknown.get("evidenceStatus").textValue()).isEqualTo("UNVERIFIED");
        assertThat(tree.at("/evaluationResult").has("liquidityResult")).isTrue();
        assertThat(tree.at("/evaluationResult/liquidityResult").isNull()).isTrue();
        assertThat(converter.fromJson(json).evaluationResult().unverifiedSymbols()).containsExactly("000660", "005930");
    }

    @Test
    void preservesExplicitNullEvidenceStatusRatherThanReplacingItWithUnverifiedFlag() throws Exception {
        StockEligibilityInput unknown = new StockEligibilityInput("005930", DATE, StockMarket.KOSPI,
                StockSecurityType.COMMON_STOCK, StockListingStatus.LISTED, "synthetic-source-01", CUTOFF, null);
        StockCandidateEvaluationSnapshot original = StockCandidateEvaluationSnapshot.from(service().evaluate(
                request("005930"), List.of(unknown), List.of()));

        String json = converter.toJson(original);
        StockCandidateEvaluationSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.evaluationResult().eligibilityResults().getFirst().input().evidenceStatus()).isNull();
        assertThat(restored.evaluationResult().eligibilityResults().getFirst().reasonCode())
                .isEqualTo(StockEligibilityReasonCode.SOURCE_UNVERIFIED);
        assertThat(mapper.readTree(json).at("/evaluationResult/eligibilityResults/0/input").has("evidenceStatus")).isTrue();
    }

    @Test
    void preservesEmptyAbsentAndNullableHistoriesWithoutTurningThemIntoExclusions() throws Exception {
        StockCandidateEvaluationResult result = converter.fromJson(converter.toJson(
                snapshotFor("liquidity-incomplete"))).evaluationResult();

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.unverifiedSymbols()).containsExactly("000660", "005380", "035420");
        assertThat(result.inputHistories()).extracting(DailyPriceHistory::symbol)
                .containsExactly("000660", "005380", "005930", "069500");
        assertThat(result.inputHistories().getFirst().bars()).isEmpty();
        assertThat(result.inputHistories().get(1).bars().getFirst().tradingValueKrw()).isNull();
        assertThat(result.inputHistories().get(1).bars().getFirst().tradingVenueScope()).isNull();
        assertThat(result.eligibilityResults()).hasSize(5);
        assertThat(result.liquidityResult()).isNotNull();
        assertThat(result.candidateSymbols()).isEmpty();
        JsonNode nullableBar = mapper.readTree(converter.toJson(StockCandidateEvaluationSnapshot.from(result)))
                .at("/evaluationResult/inputHistories/1/bars/0");
        assertThat(nullableBar.has("tradingValueKrw")).isTrue();
        assertThat(nullableBar.has("tradingVenueScope")).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StockEligibilityEvidenceStatus.class, names = {"CURRENT_ONLY", "UNVERIFIED"})
    void preservesUnverifiedEvidenceStatusAndSpecificReasonWithoutApprovingIt(StockEligibilityEvidenceStatus evidence) {
        StockCandidateEvaluationSnapshot original = StockCandidateEvaluationSnapshot.from(service().evaluate(
                request("005930"), List.of(input("005930", StockSecurityType.COMMON_STOCK, evidence)),
                List.of(history("005930", 100L))));

        StockCandidateEvaluationResult result = converter.fromJson(converter.toJson(original)).evaluationResult();

        assertThat(result.status()).isEqualTo(StockCandidateEvaluationStatus.INCOMPLETE);
        assertThat(result.eligibilityResults().getFirst().input().evidenceStatus()).isEqualTo(evidence);
        assertThat(result.eligibilityResults().getFirst().reasonCode()).isEqualTo(
                evidence == StockEligibilityEvidenceStatus.CURRENT_ONLY
                        ? StockEligibilityReasonCode.CURRENT_INFORMATION_ONLY : StockEligibilityReasonCode.SOURCE_UNVERIFIED);
        assertThat(result.liquidityResult()).isNull();
        assertThat(result.candidateSymbols()).isEmpty();
    }

    @Test
    void preservesNanosecondCutoffNonDefaultSetsAndInputMetadata() throws Exception {
        Instant cutoff = CUTOFF.plusNanos(123456789);
        StockEligibilityRequest eligibility = new StockEligibilityRequest(DATE, cutoff,
                Set.of(StockMarket.KOSPI, StockMarket.KOSDAQ),
                Set.of(StockSecurityType.COMMON_STOCK, StockSecurityType.PREFERRED_STOCK));
        StockCandidateEvaluationRequest criteria = new StockCandidateEvaluationRequest(eligibility,
                new DailyTradingValueSelectionEvaluationRequest(List.of("005930"), DATE, List.of(DATE),
                        TradingVenueScope.KRX, 123L, 7));
        StockEligibilityInput preferred = new StockEligibilityInput("005930", DATE, StockMarket.KOSDAQ,
                StockSecurityType.PREFERRED_STOCK, StockListingStatus.LISTED, "synthetic-preferred-source", cutoff,
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
        DailyPriceHistory history = new DailyPriceHistory("005930", List.of(
                new DailyPriceBar(DATE, 100L, 110L, 90L, 100L, 10L, 123L, TradingVenueScope.KRX)));
        StockCandidateEvaluationSnapshot original = StockCandidateEvaluationSnapshot.from(service().evaluate(
                criteria, List.of(preferred), List.of(history)));

        String json = converter.toJson(original);
        StockCandidateEvaluationSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.evaluationResult().request().eligibilityRequest().selectionCutoffAt()).isEqualTo(cutoff);
        assertThat(restored.evaluationResult().eligibilityResults().getFirst().input()).isEqualTo(preferred);
        assertThat(mapper.readTree(json).at("/evaluationResult/request/eligibilityRequest/selectionCutoffAt").textValue())
                .isEqualTo("2026-09-23T09:00:00.123456789Z");
    }

    @Test
    void preservesIntegerTotalsBeyondLongRangeWithoutRounding() throws Exception {
        StockCandidateEvaluationSnapshot original = StockCandidateEvaluationSnapshot.from(service().evaluate(
                request(Long.MAX_VALUE, 1, "005930"), List.of(eligible("005930")),
                List.of(history("005930", Long.MAX_VALUE))));
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(DATES.size()));

        String json = converter.toJson(original);
        StockCandidateEvaluationSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.evaluationResult().liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(expected);
        assertThat(mapper.readTree(json).at("/evaluationResult/liquidityResult/calculatedAverages/0/totalTradingValueKrw")
                .bigIntegerValue()).isEqualTo(expected);
    }

    @Test
    void preservesOutOfWindowFutureBarButDoesNotUseItDuringReplay() {
        List<DailyPriceBar> bars = new ArrayList<>(history("005930", 100L).bars());
        bars.add(new DailyPriceBar(DATE.plusDays(1), 100L, 110L, 90L, 100L, 10L,
                Long.MAX_VALUE, TradingVenueScope.INTEGRATED));
        StockCandidateEvaluationSnapshot original = StockCandidateEvaluationSnapshot.from(service().evaluate(
                request("005930"), List.of(eligible("005930")), List.of(new DailyPriceHistory("005930", bars))));

        StockCandidateEvaluationResult restored = converter.fromJson(converter.toJson(original)).evaluationResult();
        StockCandidateEvaluationResult replayed = service().evaluate(restored.request(),
                restored.eligibilityResults().stream().map(StockEligibilityResult::input).toList(), restored.inputHistories());

        assertThat(replayed).isEqualTo(original.evaluationResult());
        assertThat(restored.inputHistories().getFirst().bars()).hasSize(4);
        assertThat(replayed.liquidityResult().calculatedAverages().getFirst().totalTradingValueKrw())
                .isEqualTo(BigInteger.valueOf(300L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-json", "{}", "[]", "null"})
    void rejectsInvalidDocumentRatherThanReturningDefaultSnapshot(String json) {
        assertThatThrownBy(() -> converter.fromJson(json)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, Integer.MAX_VALUE})
    void rejectsUnsupportedSchemaVersion(int version) throws Exception {
        ObjectNode tree = completeTree();
        tree.put("schemaVersion", version);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", ";evaluationResult", "/evaluationResult;request", "/evaluationResult;status",
            "/evaluationResult;eligibilityResults", "/evaluationResult;inputHistories", "/evaluationResult;liquidityResult",
            "/evaluationResult/request;eligibilityRequest", "/evaluationResult/request;liquidityRequest",
            "/evaluationResult/request/eligibilityRequest;selectionAsOfDate",
            "/evaluationResult/request/eligibilityRequest;selectionCutoffAt",
            "/evaluationResult/request/eligibilityRequest;eligibleMarkets",
            "/evaluationResult/request/eligibilityRequest;eligibleSecurityTypes",
            "/evaluationResult/request/liquidityRequest;targetSymbols",
            "/evaluationResult/request/liquidityRequest;minimumAverageTradingValueKrw",
            "/evaluationResult/request/liquidityRequest;maxCandidateCount",
            "/evaluationResult/eligibilityResults/0;request", "/evaluationResult/eligibilityResults/0;input",
            "/evaluationResult/eligibilityResults/0;status", "/evaluationResult/eligibilityResults/0;reasonCode",
            "/evaluationResult/eligibilityResults/0/input;symbol", "/evaluationResult/eligibilityResults/0/input;asOfDate",
            "/evaluationResult/eligibilityResults/0/input;market", "/evaluationResult/eligibilityResults/0/input;securityType",
            "/evaluationResult/eligibilityResults/0/input;listingStatus",
            "/evaluationResult/eligibilityResults/0/input;sourceReference",
            "/evaluationResult/eligibilityResults/0/input;informationAvailableAt",
            "/evaluationResult/eligibilityResults/0/input;evidenceStatus",
            "/evaluationResult/inputHistories/0/bars/0;volume",
            "/evaluationResult/inputHistories/0/bars/0;tradingValueKrw",
            "/evaluationResult/inputHistories/0/bars/0;tradingVenueScope",
            "/evaluationResult/liquidityResult;unverifiedSymbols"
    }, delimiter = ';')
    void rejectsMissingFieldsIncludingNullableEligibilityAndSkippedStage(String pointer, String field) throws Exception {
        ObjectNode tree = field.equals("liquidityResult")
                ? (ObjectNode) mapper.readTree(converter.toJson(snapshotFor("all-missing"))) : completeTree();
        node(tree, pointer).remove(field);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", ";evaluationResult", "/evaluationResult;request", "/evaluationResult;status",
            "/evaluationResult;eligibilityResults", "/evaluationResult;inputHistories", "/evaluationResult;liquidityResult",
            "/evaluationResult/request;eligibilityRequest", "/evaluationResult/request;liquidityRequest",
            "/evaluationResult/request/eligibilityRequest;selectionCutoffAt",
            "/evaluationResult/request/eligibilityRequest;eligibleMarkets",
            "/evaluationResult/request/eligibilityRequest;eligibleSecurityTypes",
            "/evaluationResult/eligibilityResults/0;request", "/evaluationResult/eligibilityResults/0;input",
            "/evaluationResult/eligibilityResults/0;status", "/evaluationResult/eligibilityResults/0;reasonCode",
            "/evaluationResult/eligibilityResults/0/input;symbol", "/evaluationResult/inputHistories/0/bars/0;volume"
    }, delimiter = ';')
    void rejectsNullRequiredFieldsRatherThanReplacingThemWithDefaults(String pointer, String field) throws Exception {
        ObjectNode tree = completeTree();
        node(tree, pointer).putNull(field);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", "/evaluationResult/request/liquidityRequest;minimumAverageTradingValueKrw",
            "/evaluationResult/inputHistories/0/bars/0;volume",
            "/evaluationResult/liquidityResult/calculatedAverages/0;totalTradingValueKrw"
    }, delimiter = ';')
    void rejectsFractionalIntegersRatherThanTruncatingThem(String pointer, String field) throws Exception {
        ObjectNode tree = completeTree();
        node(tree, pointer).put(field, 1.5);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";schemaVersion", "/evaluationResult/request/liquidityRequest;minimumAverageTradingValueKrw",
            "/evaluationResult/inputHistories/0/bars/0;volume",
            "/evaluationResult/liquidityResult/calculatedAverages/0;totalTradingValueKrw"
    }, delimiter = ';')
    void rejectsStringIntegersRatherThanCoercingThem(String pointer, String field) throws Exception {
        ObjectNode tree = completeTree();
        node(tree, pointer).put(field, "1");

        assertRejected(tree);
    }

    @Test
    void rejectsNumericSymbolRatherThanLosingLeadingZero() throws Exception {
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult/eligibilityResults/0/input").put("symbol", 660);

        assertRejected(tree);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/evaluationResult;status", "/evaluationResult/eligibilityResults/0;status",
            "/evaluationResult/eligibilityResults/0;reasonCode", "/evaluationResult/eligibilityResults/0/input;market",
            "/evaluationResult/eligibilityResults/0/input;securityType",
            "/evaluationResult/eligibilityResults/0/input;listingStatus",
            "/evaluationResult/eligibilityResults/0/input;evidenceStatus",
            "/evaluationResult/inputHistories/0/bars/0;tradingVenueScope"
    }, delimiter = ';')
    void rejectsUnknownAndOrdinalEnumsEvenForNullableMetadata(String pointer, String field) throws Exception {
        ObjectNode tree = completeTree();
        node(tree, pointer).put(field, "UNKNOWN");
        assertRejected(tree);
        node(tree, pointer).put(field, 0);
        assertRejected(tree);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/evaluationResult", "/evaluationResult/eligibilityResults/0/input"})
    void rejectsUnknownFieldsRatherThanDroppingEvidence(String pointer) throws Exception {
        ObjectNode tree = completeTree();
        node(tree, pointer).put("unknownEvidence", "must not be dropped");

        assertRejected(tree);
    }

    @Test
    void rejectsDuplicateNestedFieldsRatherThanChoosingOneValue() {
        String valid = converter.toJson(snapshotFor("selected"));
        String statusField = "\"status\":\"COMPLETE\"";
        String json = valid.replace(statusField, statusField + "," + statusField);

        assertThat(json).isNotEqualTo(valid);
        assertThatThrownBy(() -> converter.fromJson(json)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(FAILURE).hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsDuplicateRootFieldEvenWhenBothValuesMatch() {
        String valid = converter.toJson(snapshotFor("selected"));
        String json = "{\"schemaVersion\":1," + valid.substring(1);

        assertThatThrownBy(() -> converter.fromJson(json)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(FAILURE).hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsTrailingDocumentRatherThanIgnoringIt() {
        assertThatThrownBy(() -> converter.fromJson(converter.toJson(snapshotFor("selected")) + " {}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(FAILURE);
    }

    @Test
    void existingResultValidationStillRejectsMissingTargetClassification() throws Exception {
        ObjectNode tree = completeTree();
        ((ArrayNode) tree.at("/evaluationResult/eligibilityResults")).remove(0);

        assertRejected(tree);
    }

    @Test
    void existingResultValidationStillRejectsChangedEligibilityCriteria() throws Exception {
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult/eligibilityResults/0/request").put("selectionCutoffAt", CUTOFF.plusSeconds(1).toString());

        assertRejected(tree);
    }

    @Test
    void existingResultValidationStillRejectsEligibilityStatusAndReasonContradiction() throws Exception {
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult/eligibilityResults/0").put("status", "INELIGIBLE");

        assertRejected(tree);
    }

    @Test
    void existingResultValidationStillRejectsChangedLiquidityCriteria() throws Exception {
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult/liquidityResult/request").put("minimumAverageTradingValueKrw", 101);

        assertRejected(tree);
    }

    @Test
    void existingResultValidationStillRejectsReplacedEligibleHistory() throws Exception {
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult/liquidityResult/inputHistories/0/bars/0").put("tradingValueKrw", 201);

        assertRejected(tree);
    }

    @Test
    void existingResultValidationStillRejectsCompletionStatusContradiction() throws Exception {
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult").put("status", "INCOMPLETE");

        assertRejected(tree);
    }

    @Test
    void restoresStructureWithoutClaimingArithmeticOrSourceVerification() throws Exception {
        StockCandidateEvaluationSnapshot original = snapshotFor("selected");
        ObjectNode tree = completeTree();
        node(tree, "/evaluationResult/liquidityResult/calculatedAverages/0").put("totalTradingValueKrw", 601);
        node(tree, "/evaluationResult/liquidityResult/selectionResults/1/average").put("totalTradingValueKrw", 601);

        StockCandidateEvaluationResult restored = converter.fromJson(mapper.writeValueAsString(tree)).evaluationResult();
        StockCandidateEvaluationResult recalculated = service().evaluate(restored.request(),
                restored.eligibilityResults().stream().map(StockEligibilityResult::input).toList(), restored.inputHistories());

        assertThat(restored).isNotEqualTo(original.evaluationResult());
        assertThat(recalculated).isEqualTo(original.evaluationResult());
        assertThat(recalculated).isNotEqualTo(restored);
    }

    @Test
    void keepsExistingLiquidityFormatIndependentAndRejectsCrossTypeDocuments() {
        DailyTradingValueSelectionSnapshotJsonConverter existing = new DailyTradingValueSelectionSnapshotJsonConverter(mapper);
        DailyTradingValueSelectionSnapshot liquiditySnapshot = DailyTradingValueSelectionSnapshot.from(
                snapshotFor("selected").evaluationResult().liquidityResult());
        String liquidityJson = existing.toJson(liquiditySnapshot);
        String candidateJson = converter.toJson(snapshotFor("selected"));

        assertThat(existing.fromJson(liquidityJson)).isEqualTo(liquiditySnapshot);
        assertThat(liquiditySnapshot.schemaVersion()).isEqualTo(1);
        assertThatThrownBy(() -> converter.fromJson(liquidityJson))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(FAILURE);
        assertThatThrownBy(() -> existing.fromJson(candidateJson)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesSerializationFailureCauseInsteadOfReturningPartialJson() {
        StockCandidateEvaluationSnapshotJsonConverter withoutDateModule =
                new StockCandidateEvaluationSnapshotJsonConverter(new ObjectMapper());

        assertThatThrownBy(() -> withoutDateModule.toJson(snapshotFor("selected")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to serialize stock candidate evaluation snapshot.")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new StockCandidateEvaluationSnapshotJsonConverter(null))
                .isInstanceOf(NullPointerException.class).hasMessage("objectMapper must not be null.");
        assertThatThrownBy(() -> converter.toJson(null))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshot must not be null.");
        assertThatThrownBy(() -> converter.fromJson(null))
                .isInstanceOf(NullPointerException.class).hasMessage("json must not be null.");
    }

    @Test
    void keepsSharedMapperSettingsUnchangedAndOverridesPermissiveParsingLocally() throws Exception {
        ObjectMapper shared = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        StockCandidateEvaluationSnapshotJsonConverter local = new StockCandidateEvaluationSnapshotJsonConverter(shared);
        StockCandidateEvaluationSnapshot original = snapshotFor("all-missing");
        String json = local.toJson(original);

        assertThat(local.fromJson(json)).isEqualTo(original);
        assertThat(shared.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(shared.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)).isTrue();
        assertThat(shared.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)).isTrue();
        assertThat(shared.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isTrue();
        assertThat(shared.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion())
                .isEqualTo(JsonInclude.Include.NON_NULL);
        assertThat(shared.readValue("[1]", Integer.class)).isEqualTo(1);
        assertThat(shared.readValue("\"1\"", Integer.class)).isEqualTo(1);
        assertThat(shared.readValue("660", String.class)).isEqualTo("660");
        ObjectNode unknown = (ObjectNode) shared.readTree(json);
        node(unknown, "/evaluationResult/eligibilityResults/0/input").put("market", "UNKNOWN");
        assertThatThrownBy(() -> local.fromJson(shared.writeValueAsString(unknown)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(FAILURE);
        assertThatThrownBy(() -> local.fromJson("[" + json + "]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(FAILURE);
        ObjectNode scalar = (ObjectNode) shared.readTree(json);
        node(scalar, "/evaluationResult/request/eligibilityRequest").put("eligibleMarkets", "KOSPI");
        assertThatThrownBy(() -> local.fromJson(shared.writeValueAsString(scalar)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(FAILURE);
    }

    private void assertRejected(JsonNode tree) throws JsonProcessingException {
        String json = mapper.writeValueAsString(tree);
        assertThatThrownBy(() -> converter.fromJson(json)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(FAILURE).hasCauseInstanceOf(JsonProcessingException.class);
    }

    private ObjectNode completeTree() throws JsonProcessingException {
        return (ObjectNode) mapper.readTree(converter.toJson(snapshotFor("selected")));
    }

    private ObjectNode node(JsonNode tree, String pointer) {
        return (ObjectNode) tree.at(pointer == null ? "" : pointer);
    }

    private StockCandidateEvaluationSnapshot snapshotFor(String scenario) {
        StockCandidateEvaluationResult result = switch (scenario) {
            case "selected" -> service().evaluate(request("005930", "000660", "005380", "035420", "069500"),
                    List.of(eligible("005930"), eligible("000660"), eligible("005380"), eligible("035420"), excluded("069500")),
                    List.of(history("005930", 300L), history("000660", 200L), history("005380", 0L),
                            history("035420", 150L), history("069500", 9000L)));
            case "all-ineligible" -> service().evaluate(request("069500", "000660"),
                    List.of(excluded("069500"), input("000660", StockSecurityType.ETN,
                            StockEligibilityEvidenceStatus.AS_OF_VERIFIED)), List.of(history("069500", null, null)));
            case "eligibility-incomplete" -> service().evaluate(request("005930", "000660"),
                    List.of(eligible("005930")), List.of(history("005930", 100L), history("000660", 200L)));
            case "liquidity-incomplete" -> service().evaluate(request("005930", "000660", "005380", "035420", "069500"),
                    List.of(eligible("005930"), eligible("000660"), eligible("005380"), eligible("035420"), excluded("069500")),
                    List.of(history("005930", 100L), new DailyPriceHistory("000660", List.of()),
                            history("005380", null, null), history("069500", 9000L)));
            case "below-minimum" -> service().evaluate(request("005930"), List.of(eligible("005930")),
                    List.of(history("005930", 0L)));
            case "all-missing" -> service().evaluate(request("005930", "000660"), List.of(), List.of());
            default -> throw new IllegalArgumentException("Unknown synthetic scenario: " + scenario);
        };
        return StockCandidateEvaluationSnapshot.from(result);
    }

    private StockEligibilityInput excluded(String symbol) {
        return input(symbol, StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
    }
}
