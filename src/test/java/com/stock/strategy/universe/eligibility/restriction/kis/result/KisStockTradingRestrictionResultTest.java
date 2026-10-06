package com.stock.strategy.universe.eligibility.restriction.kis.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.KisStockTradingRestrictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.batch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockTradingRestrictionResultTest {
    private final KisStockTradingRestrictionResult result = observedResult("Y", "N");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankVersion(String value) {
        assertThatThrownBy(() -> rebuild(result.suspensionStatus(), result.liquidationStatus(), value, result.sourceRevision()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "A000000000000000000000000000000000000000",
            "000000000000000000000000000000000000000g", "00000000000000000000000000000000000000000"})
    void rejectsMalformedSourceRevision(String value) {
        assertThatThrownBy(() -> rebuild(result.suspensionStatus(), result.liquidationStatus(), result.restrictionVersion(), value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresSourceAndBothIndependentStatuses() {
        assertThatThrownBy(() -> new KisStockTradingRestrictionResult(null, result.suspensionStatus(), result.liquidationStatus(),
                result.restrictionVersion(), result.sourceRevision())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> rebuild(null, result.liquidationStatus(), result.restrictionVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> rebuild(result.suspensionStatus(), null, result.restrictionVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"Y|Y_OBSERVED", "N|N_OBSERVED", "' '|VALUE_UNVERIFIED", "y|VALUE_UNVERIFIED", "?|VALUE_UNVERIFIED"}, delimiter = '|')
    void rejectsEveryStatusThatDisagreesWithEitherRawField(String rawFlag, KisStockTradingFlagStatus expected) {
        var observed = observedResult(rawFlag, rawFlag);
        for (var candidate : KisStockTradingFlagStatus.values()) {
            if (candidate == expected) {
                assertThat(new KisStockTradingRestrictionResult(observed.typeResolution(), candidate, candidate,
                        observed.restrictionVersion(), observed.sourceRevision())).isEqualTo(observed);
            } else {
                assertThatThrownBy(() -> new KisStockTradingRestrictionResult(observed.typeResolution(), candidate, expected,
                        observed.restrictionVersion(), observed.sourceRevision())).isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("suspensionStatus must agree with its original KIS field value.");
                assertThatThrownBy(() -> new KisStockTradingRestrictionResult(observed.typeResolution(), expected, candidate,
                        observed.restrictionVersion(), observed.sourceRevision())).isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("liquidationStatus must agree with its original KIS field value.");
            }
        }
    }

    @Test
    void rejectsReplacingTheInputWhileKeepingOldObservations() {
        var changed = observedResult("N", "Y").typeResolution();
        assertThatThrownBy(() -> new KisStockTradingRestrictionResult(changed, result.suspensionStatus(), result.liquidationStatus(),
                result.restrictionVersion(), result.sourceRevision())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsValuePreservingJsonRoundTripsWithAndWithoutKrxMatches() throws Exception {
        var json = new ObjectMapper();
        var matchedInput = policy().resolve(matching(batch(KisStockMasterParsingFixture.row(KOSPI)),
                stock(KOSPI, "005930", "KR7005930003"))).rowResults().getFirst();
        var matched = new KisStockTradingRestrictionPolicy().evaluate(matchedInput);
        for (var original : new KisStockTradingRestrictionResult[]{result, observedResult(" ", "?"), matched}) {
            var restored = json.readValue(json.writeValueAsBytes(original), KisStockTradingRestrictionResult.class);
            assertThat(restored).isEqualTo(original);
            assertThat(restored.typeResolution()).isNotSameAs(original.typeResolution());
            assertThat(restored.typeResolution().kisClassification().rawRecord())
                    .isEqualTo(original.typeResolution().kisClassification().rawRecord());
        }
        assertThat(result.typeResolution().identityMatch()).isNull();
        assertThat(result.typeResolution().referenceSecurityType()).isNull();
        assertThat(matched.typeResolution().identityMatch()).isNotNull();
    }

    private KisStockTradingRestrictionResult rebuild(
            KisStockTradingFlagStatus suspension, KisStockTradingFlagStatus liquidation, String version, String revision
    ) {
        return new KisStockTradingRestrictionResult(result.typeResolution(), suspension, liquidation, version, revision);
    }

    private static KisStockTradingRestrictionResult observedResult(String suspension, String liquidation) {
        byte[] row = KisStockMasterParsingFixture.row(KOSPI);
        KisStockMasterParsingFixture.put(row, 121, suspension);
        KisStockMasterParsingFixture.put(row, 122, liquidation);
        var input = policy().resolve(matching(batch(row))).rowResults().getFirst();
        return new KisStockTradingRestrictionPolicy().evaluate(input);
    }
}
