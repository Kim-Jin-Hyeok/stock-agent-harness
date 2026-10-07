package com.stock.strategy.universe.eligibility.restriction.kis.warning.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.kis.parsing.warning.record.KisStockMasterMarketWarningRawRecord;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.N_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.INVESTMENT_WARNING_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.VALUE_UNVERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMarketWarningObservationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(KisStockMarketWarningStatus.class)
    void rejectsEveryWarningStatusThatDisagreesWithTheRawCode(KisStockMarketWarningStatus status) {
        var raw = raw("02", "N");
        if (status == INVESTMENT_WARNING_OBSERVED) {
            assertThat(new KisStockMarketWarningObservation(raw, status, N_OBSERVED).rawRecord()).isSameAs(raw);
        } else {
            assertThatThrownBy(() -> new KisStockMarketWarningObservation(raw, status, N_OBSERVED))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockTradingFlagStatus.class)
    void rejectsEveryPreannouncementStatusThatDisagreesWithItsProvidedValue(KisStockTradingFlagStatus status) {
        var raw = raw("02", "Y");
        if (status == Y_OBSERVED) {
            assertThat(new KisStockMarketWarningObservation(raw, INVESTMENT_WARNING_OBSERVED, status).rawRecord()).isSameAs(raw);
        } else {
            assertThatThrownBy(() -> new KisStockMarketWarningObservation(raw, INVESTMENT_WARNING_OBSERVED, status))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void keepsUnknownFieldsUnknownAndRejectsMissingDependencies() {
        var raw = raw(" ?", "y");
        var observation = new KisStockMarketWarningObservation(raw, VALUE_UNVERIFIED, KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(observation.rawRecord()).isSameAs(raw);
        assertThatThrownBy(() -> new KisStockMarketWarningObservation(raw, INVESTMENT_WARNING_OBSERVED, KisStockTradingFlagStatus.VALUE_UNVERIFIED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockMarketWarningObservation(raw, VALUE_UNVERIFIED, N_OBSERVED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockMarketWarningObservation(null, VALUE_UNVERIFIED, N_OBSERVED)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMarketWarningObservation(raw, null, N_OBSERVED)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMarketWarningObservation(raw, VALUE_UNVERIFIED, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"warningStatus", "preannouncementStatus", "rawCode", "rawPreannouncement"})
    void rejectsJsonThatChangesOnlyTheInterpretationOrOnlyTheRawValue(String change) {
        var original = new KisStockMarketWarningObservation(raw("02", "Y"), INVESTMENT_WARNING_OBSERVED, Y_OBSERVED);
        ObjectNode node = mapper.valueToTree(original);
        switch (change) {
            case "warningStatus" -> node.put("warningStatus", "NO_WARNING_OBSERVED");
            case "preannouncementStatus" -> node.put("riskPreannouncementStatus", "N_OBSERVED");
            case "rawCode" -> ((ObjectNode) node.get("rawRecord")).put("rawMarketWarningCode", "00");
            case "rawPreannouncement" -> ((ObjectNode) node.get("rawRecord")).put("rawMarketWarningRiskPreannouncement", "N");
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }
        assertThatThrownBy(() -> mapper.treeToValue(node, KisStockMarketWarningObservation.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static KisStockMasterMarketWarningRawRecord raw(String code, String preannouncement) {
        return new KisStockMasterMarketWarningRawRecord(1, "005930", "KR7005930003", code, preannouncement);
    }
}
