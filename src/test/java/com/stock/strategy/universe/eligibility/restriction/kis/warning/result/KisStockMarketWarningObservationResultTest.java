package com.stock.strategy.universe.eligibility.restriction.kis.warning.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.market.stock.master.provider.kis.parsing.warning.record.KisStockMasterMarketWarningRawRecord;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.multiRowSource;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMarketWarningObservationResultTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final KisStockMasterMarketWarningParser parser = new KisStockMasterMarketWarningParser();
    private final KisStockMarketWarningObservationPolicy policy = new KisStockMarketWarningObservationPolicy();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void restoresFullSourceAndObservationsAcrossJsonRoundTrip(KisStockMasterMarket market) throws Exception {
        var result = policy.evaluate(parser.parse(multiRowSource(market)));
        assertThat(mapper.readValue(mapper.writeValueAsBytes(result), KisStockMarketWarningObservationResult.class)).isEqualTo(result);
    }

    @Test
    void copiesTheObservationListAndRejectsMissingDependencies() {
        var original = result();
        var mutable = new ArrayList<>(original.observations());
        var copied = copy(original, mutable);
        mutable.clear();
        assertThat(copied.observations()).isEqualTo(original.observations());
        assertThatThrownBy(() -> copied.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
        mutable.add(null);
        assertThatThrownBy(() -> copy(original, mutable)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> copy(original, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMarketWarningObservationResult(null, original.observations(), original.observationVersion(), original.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "removed", "duplicated", "reordered", "symbol", "standardCode", "lineNumber", "code", "preannouncement"})
    void rejectsMissingReorderedOrReplacedRowsEvenWhenEachReplacementIsInternallyConsistent(String change) {
        var original = result();
        var observations = new ArrayList<>(original.observations());
        var raw = observations.getFirst().rawRecord();
        switch (change) {
            case "empty" -> observations.clear();
            case "removed" -> observations.removeFirst();
            case "duplicated" -> observations.set(1, observations.getFirst());
            case "reordered" -> Collections.reverse(observations);
            default -> {
                var replaced = new KisStockMasterMarketWarningRawRecord(change.equals("lineNumber") ? 2 : raw.lineNumber(),
                        change.equals("symbol") ? "999999" : raw.symbol(),
                        change.equals("standardCode") ? "KR7999999999" : raw.standardCode(),
                        change.equals("code") ? "02" : raw.rawMarketWarningCode(),
                        change.equals("preannouncement") ? "Y" : raw.rawMarketWarningRiskPreannouncement());
                observations.set(0, new KisStockMarketWarningObservation(replaced,
                        KisStockMarketWarningStatus.fromRawValue(replaced.rawMarketWarningCode()),
                        KisStockTradingFlagStatus.fromRawValue(replaced.rawMarketWarningRiskPreannouncement())));
            }
        }
        assertThatThrownBy(() -> copy(original, observations)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"observationVersion", "sourceRevision"})
    void rejectsUnknownBlankOrMissingInterpretationProvenance(String field) {
        var original = result();
        for (String value : new String[]{null, "", " ", "UNKNOWN", "0".repeat(40)}) {
            assertThatThrownBy(() -> new KisStockMarketWarningObservationResult(original.source(), original.observations(),
                    field.equals("observationVersion") ? value : original.observationVersion(),
                    field.equals("sourceRevision") ? value : original.sourceRevision())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"warningStatus", "preannouncementStatus", "source", "sourceHash", "sourceVersion", "rawVersion", "rawRevision", "observationVersion", "sourceRevision"})
    void rejectsJsonWithAlteredStatusesSourceOrVersions(String change) {
        var original = result();
        ObjectNode node = mapper.valueToTree(original);
        ObjectNode row = (ObjectNode) node.withArray("observations").get(0);
        ObjectNode source = (ObjectNode) node.get("source");
        ObjectNode master = (ObjectNode) source.get("source");
        switch (change) {
            case "warningStatus" -> row.put("warningStatus", "INVESTMENT_WARNING_OBSERVED");
            case "preannouncementStatus" -> row.put("riskPreannouncementStatus", "Y_OBSERVED");
            case "source" -> node.set("source", mapper.valueToTree(parser.parse(source(KOSPI, "02", "Y"))));
            case "sourceHash" -> master.put("inputSha256", "0".repeat(64));
            case "sourceVersion" -> master.put("layoutVersion", "UNKNOWN");
            case "rawVersion" -> source.put("parserVersion", "UNKNOWN");
            case "rawRevision" -> source.put("sourceRevision", "0".repeat(40));
            case "observationVersion" -> node.put("observationVersion", "UNKNOWN");
            case "sourceRevision" -> node.put("sourceRevision", "0".repeat(40));
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }
        assertThatThrownBy(() -> mapper.treeToValue(node, KisStockMarketWarningObservationResult.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private KisStockMarketWarningObservationResult result() {
        return policy.evaluate(parser.parse(multiRowSource(KOSPI)));
    }

    private static KisStockMarketWarningObservationResult copy(KisStockMarketWarningObservationResult original,
                                                               List<KisStockMarketWarningObservation> observations) {
        return new KisStockMarketWarningObservationResult(original.source(), observations, original.observationVersion(), original.sourceRevision());
    }
}
