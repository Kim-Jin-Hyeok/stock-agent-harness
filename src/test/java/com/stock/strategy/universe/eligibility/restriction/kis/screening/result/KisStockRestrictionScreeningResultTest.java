package com.stock.strategy.universe.eligibility.restriction.kis.screening.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.inputs;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionScreeningResultTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final KisStockRestrictionScreeningPolicy policy = new KisStockRestrictionScreeningPolicy();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void roundTripsFullInputsAndAllStatusesIncludingDisconnectedEvidence(KisStockMasterMarket market) throws Exception {
        var clean = inputs(market, "00", "N", Map.of(), Map.of());
        var excluded = inputs(market, "02", "Y", Map.of("suspension", "Y"), Map.of("admn_item_yn", "?"));
        var review = inputs(market, "??", "y", Map.of(), Map.of());
        var master = clean.basicInfo().observation().typeResolution().matchingResult().masterBatch();
        for (var result : List.of(policy.evaluate(clean.basicInfo(), clean.warnings()), policy.evaluate(excluded.basicInfo(), excluded.warnings()),
                policy.evaluate(review.basicInfo(), review.warnings()), policy.evaluate(clean.basicInfo(), warnings(master, market == KOSPI ? KOSDAQ : KOSPI)))) {
            assertThat(mapper.readValue(mapper.writeValueAsBytes(result), KisStockRestrictionScreeningResult.class)).isEqualTo(result);
        }
    }

    @Test
    void copiesReasonsAndRejectsNullInputsStatusListsAndMembers() {
        var result = result();
        var reasons = new ArrayList<>(result.reasonCodes());
        var copied = copy(result, result.status(), reasons, result.screeningVersion());
        reasons.clear();
        assertThat(copied.reasonCodes()).isEqualTo(result.reasonCodes());
        assertThatThrownBy(() -> copied.reasonCodes().clear()).isInstanceOf(UnsupportedOperationException.class);
        reasons.add(null);
        assertThatThrownBy(() -> copy(result, result.status(), reasons, result.screeningVersion())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> copy(result, result.status(), null, result.screeningVersion())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> copy(result, null, result.reasonCodes(), result.screeningVersion())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockRestrictionScreeningResult(null, result.marketWarningObservation(), result.status(), result.reasonCodes(), result.screeningVersion()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockRestrictionScreeningResult(result.basicInfoScreening(), null, result.status(), result.reasonCodes(), result.screeningVersion()))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"removed", "duplicated", "reordered", "added", "status", "version"})
    void rejectsIncompleteReorderedOrForgedResultMetadata(String change) {
        var result = result();
        var reasons = new ArrayList<>(result.reasonCodes());
        switch (change) {
            case "removed" -> reasons.removeFirst();
            case "duplicated" -> reasons.add(reasons.getFirst());
            case "reordered" -> Collections.reverse(reasons);
            case "added" -> reasons.add(KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED);
            default -> {
            }
        }
        assertThatThrownBy(() -> copy(result, change.equals("status") ? KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED : result.status(),
                reasons, change.equals("version") ? "UNKNOWN" : result.screeningVersion())).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "UNKNOWN"})
    void rejectsBlankOrUnknownVersions(String version) {
        var result = result();
        assertThatThrownBy(() -> copy(result, result.status(), result.reasonCodes(), version)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingVersion() {
        var result = result();
        assertThatThrownBy(() -> copy(result, result.status(), result.reasonCodes(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "reason", "source", "baseSource", "version", "baseVersion", "warningVersion", "warningRevision"})
    void rejectsJsonWithAlteredVerdictsOrInputs(String change) {
        var result = result();
        ObjectNode node = mapper.valueToTree(result);
        switch (change) {
            case "status" -> node.put("status", "NO_EXCLUSION_SIGNAL_OBSERVED");
            case "reason" -> node.withArray("reasonCodes").removeAll();
            case "source" -> node.set("marketWarningObservation", mapper.valueToTree(inputs(KOSDAQ, "00", "N", Map.of(), Map.of()).warnings()));
            case "baseSource" -> node.set("basicInfoScreening", mapper.valueToTree(inputs(KOSDAQ, "02", "Y", Map.of(), Map.of()).basicInfo()));
            case "version" -> node.put("screeningVersion", "UNKNOWN");
            case "baseVersion" -> ((ObjectNode) node.get("basicInfoScreening")).put("screeningVersion", "UNKNOWN");
            case "warningVersion" -> ((ObjectNode) node.get("marketWarningObservation")).put("observationVersion", "UNKNOWN");
            case "warningRevision" -> ((ObjectNode) node.get("marketWarningObservation")).put("sourceRevision", "0".repeat(40));
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }
        assertThatThrownBy(() -> mapper.treeToValue(node, KisStockRestrictionScreeningResult.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private KisStockRestrictionScreeningResult result() {
        var input = inputs(KOSDAQ, "02", "Y", Map.of("suspension", "Y"), Map.of("admn_item_yn", "?"));
        return policy.evaluate(input.basicInfo(), input.warnings());
    }

    private static KisStockRestrictionScreeningResult copy(KisStockRestrictionScreeningResult result,
                                                           KisStockRestrictionScreeningStatus status,
                                                           List<KisStockRestrictionScreeningReasonCode> reasons, String version) {
        return new KisStockRestrictionScreeningResult(result.basicInfoScreening(), result.marketWarningObservation(), status, reasons, version);
    }
}
