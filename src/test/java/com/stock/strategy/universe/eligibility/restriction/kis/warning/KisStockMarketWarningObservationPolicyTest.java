package com.stock.strategy.universe.eligibility.restriction.kis.warning;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.multiRowSource;
import static com.stock.market.stock.master.provider.kis.parsing.warning.support.KisStockMasterMarketWarningFixture.source;
import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.N_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.INVESTMENT_CAUTION_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.INVESTMENT_RISK_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.INVESTMENT_WARNING_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.NO_WARNING_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningStatus.VALUE_UNVERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMarketWarningObservationPolicyTest {
    private final KisStockMasterMarketWarningParser parser = new KisStockMasterMarketWarningParser();
    private final KisStockMarketWarningObservationPolicy policy = new KisStockMarketWarningObservationPolicy();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void interpretsBothFieldsIndependentlyWithoutNormalizingUnverifiedValues(KisStockMasterMarket market) {
        var codes = new LinkedHashMap<String, KisStockMarketWarningStatus>();
        codes.put("00", NO_WARNING_OBSERVED);
        codes.put("01", INVESTMENT_CAUTION_OBSERVED);
        codes.put("02", INVESTMENT_WARNING_OBSERVED);
        codes.put("03", INVESTMENT_RISK_OBSERVED);
        for (String value : new String[]{"  ", "0 ", " 0", "??", "ab", "99"}) {
            codes.put(value, VALUE_UNVERIFIED);
        }
        var preannouncements = Map.of("Y", Y_OBSERVED, "N", N_OBSERVED,
                " ", KisStockTradingFlagStatus.VALUE_UNVERIFIED, "y", KisStockTradingFlagStatus.VALUE_UNVERIFIED,
                "n", KisStockTradingFlagStatus.VALUE_UNVERIFIED, "?", KisStockTradingFlagStatus.VALUE_UNVERIFIED,
                "0", KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        for (var code : codes.entrySet()) {
            for (var preannouncement : preannouncements.entrySet()) {
                var parsed = parser.parse(source(market, code.getKey(), preannouncement.getKey()));
                var result = policy.evaluate(parsed);
                var row = result.observations().getFirst();
                assertThat(result.source()).isSameAs(parsed);
                assertThat(row.rawRecord()).isSameAs(parsed.records().getFirst());
                assertThat(row.rawRecord().rawMarketWarningCode()).isEqualTo(code.getKey());
                assertThat(row.rawRecord().rawMarketWarningRiskPreannouncement()).isEqualTo(preannouncement.getKey());
                assertThat(row.warningStatus()).isEqualTo(code.getValue());
                assertThat(row.riskPreannouncementStatus()).isEqualTo(preannouncement.getValue());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void retainsEverySourceRowIdentifiersAndEvidenceWithoutChangingInvestmentCaution(KisStockMasterMarket market) {
        var original = multiRowSource(market);
        var parsed = parser.parse(original);
        var result = policy.evaluate(parsed);
        assertThat(result.source()).isSameAs(parsed);
        assertThat(result.source().source()).isSameAs(original);
        assertThat(result.observationVersion()).isEqualTo("KIS_STOCK_MARKET_WARNING_OBSERVATION_V1");
        assertThat(result.sourceRevision()).isEqualTo("277ec0eb7a9b7f63b6807829286c80f36649dad2");
        assertThat(result.observations()).extracting("rawRecord").containsExactlyElementsOf(parsed.records());
        assertThat(result.observations()).extracting("warningStatus")
                .containsExactly(NO_WARNING_OBSERVED, INVESTMENT_RISK_OBSERVED, VALUE_UNVERIFIED);
        assertThat(result.observations()).extracting("riskPreannouncementStatus")
                .containsExactly(N_OBSERVED, Y_OBSERVED, KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(original.records().getFirst().rawInvestmentCaution()).isEqualTo(market == KOSPI ? null : "N");
        assertThat(original.records().getFirst().rawManagement()).isEqualTo("?");
    }

    @Test
    void rejectsNullAndKeepsNoPreviousRunState() {
        var input = parser.parse(source(KOSPI, "02", "N"));
        var result = policy.evaluate(input);
        policy.evaluate(parser.parse(source(KisStockMasterMarket.KOSDAQ, "00", "Y")));
        assertThat(policy.evaluate(input)).isEqualTo(result);
        assertThatThrownBy(() -> policy.evaluate(null)).isInstanceOf(NullPointerException.class);
    }
}
