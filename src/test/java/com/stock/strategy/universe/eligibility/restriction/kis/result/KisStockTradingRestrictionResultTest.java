package com.stock.strategy.universe.eligibility.restriction.kis.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.KisStockTradingRestrictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
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
    void requiresSourceAndAllFiveIndependentStatuses() {
        assertThatThrownBy(() -> new KisStockTradingRestrictionResult(null, result.suspensionStatus(), result.liquidationStatus(),
                result.spacStatus(), result.managementStatus(), result.investmentCautionStatus(),
                result.restrictionVersion(), result.sourceRevision())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> rebuild(null, result.liquidationStatus(), result.restrictionVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> rebuild(result.suspensionStatus(), null, result.restrictionVersion(), result.sourceRevision()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> copyAdditional(result, null, result.managementStatus(), result.investmentCautionStatus()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("spacStatus");
        assertThatThrownBy(() -> copyAdditional(result, result.spacStatus(), null, result.investmentCautionStatus()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("managementStatus");
        assertThatThrownBy(() -> copyAdditional(result, result.spacStatus(), result.managementStatus(), null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("investmentCautionStatus");
    }

    @ParameterizedTest
    @CsvSource(value = {"Y|Y_OBSERVED", "N|N_OBSERVED", "' '|VALUE_UNVERIFIED", "y|VALUE_UNVERIFIED", "?|VALUE_UNVERIFIED"}, delimiter = '|')
    void rejectsEveryStatusThatDisagreesWithEitherRawField(String rawFlag, KisStockTradingFlagStatus expected) {
        var observed = observedResult(rawFlag, rawFlag);
        for (var candidate : KisStockTradingFlagStatus.values()) {
            if (candidate == expected) {
                assertThat(new KisStockTradingRestrictionResult(observed.typeResolution(), candidate, candidate,
                        observed.spacStatus(), observed.managementStatus(), observed.investmentCautionStatus(),
                        observed.restrictionVersion(), observed.sourceRevision())).isEqualTo(observed);
            } else {
                assertThatThrownBy(() -> new KisStockTradingRestrictionResult(observed.typeResolution(), candidate, expected,
                        observed.spacStatus(), observed.managementStatus(), observed.investmentCautionStatus(),
                        observed.restrictionVersion(), observed.sourceRevision())).isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("suspensionStatus must agree with its original KIS field value.");
                assertThatThrownBy(() -> new KisStockTradingRestrictionResult(observed.typeResolution(), expected, candidate,
                        observed.spacStatus(), observed.managementStatus(), observed.investmentCautionStatus(),
                        observed.restrictionVersion(), observed.sourceRevision())).isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("liquidationStatus must agree with its original KIS field value.");
            }
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"Y|Y_OBSERVED", "N|N_OBSERVED", "' '|VALUE_UNVERIFIED", "y|VALUE_UNVERIFIED", "?|VALUE_UNVERIFIED"}, delimiter = '|')
    void rejectsEveryStatusThatDisagreesWithAnAdditionalRawField(String rawFlag, KisStockTradingFlagStatus expected) {
        var observed = observedResult(KOSDAQ, rawFlag, rawFlag, rawFlag);
        for (var candidate : KisStockTradingFlagStatus.values()) {
            if (candidate == expected) {
                assertThat(copyAdditional(observed, candidate, candidate, candidate)).isEqualTo(observed);
            } else {
                assertThatThrownBy(() -> copyAdditional(observed, candidate, expected, expected))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("spacStatus must agree with its original KIS field value.");
                assertThatThrownBy(() -> copyAdditional(observed, expected, candidate, expected))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("managementStatus must agree with its original KIS field value.");
                assertThatThrownBy(() -> copyAdditional(observed, expected, expected, candidate))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("investmentCautionStatus must agree with its original KIS field value.");
            }
        }
    }

    @Test
    void acceptsOnlyUnprovidedStatusForTheMissingKospiCautionField() {
        for (var candidate : KisStockTradingFlagStatus.values()) {
            if (candidate == KisStockTradingFlagStatus.FIELD_NOT_PROVIDED) {
                assertThat(copyAdditional(result, result.spacStatus(), result.managementStatus(), candidate)).isEqualTo(result);
            } else {
                assertThatThrownBy(() -> copyAdditional(result, result.spacStatus(), result.managementStatus(), candidate))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("investmentCautionStatus must agree with its original KIS field value.");
            }
        }
    }

    @Test
    void rejectsMarketAndCautionPresenceMismatchesEvenWithoutAParseResult() {
        for (var market : KisStockMasterMarket.values()) {
            var other = market == KOSPI ? KOSDAQ : KOSPI;
            var source = observedResult(other, "N", "N", other == KOSPI ? null : "N");
            var classified = source.typeResolution().kisClassification();
            var mislabeled = new KisStockMasterTypeClassificationResult(
                    market, classified.rawRecord(), classified.securityType(), classified.reasonCode(),
                    classified.classificationVersion(), classified.sourceRevision());
            var input = new KisKrxStockTypeResolutionResult(mislabeled, null, null,
                    source.typeResolution().referenceSecurityType(), source.typeResolution().reasonCode());

            assertThatThrownBy(() -> new KisStockTradingRestrictionResult(input, source.suspensionStatus(), source.liquidationStatus(),
                    source.spacStatus(), source.managementStatus(), source.investmentCautionStatus(),
                    source.restrictionVersion(), source.sourceRevision()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field presence");
            assertThatThrownBy(() -> new KisStockTradingRestrictionPolicy().evaluate(input))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field presence");
        }
    }

    @Test
    void rejectsReplacingTheInputWhileKeepingOldObservations() {
        var changed = observedResult("N", "Y").typeResolution();
        assertThatThrownBy(() -> new KisStockTradingRestrictionResult(changed, result.suspensionStatus(), result.liquidationStatus(),
                result.spacStatus(), result.managementStatus(), result.investmentCautionStatus(),
                result.restrictionVersion(), result.sourceRevision())).isInstanceOf(IllegalArgumentException.class);
        var positive = observedResult(KOSDAQ, "Y", "Y", "Y");
        var negative = observedResult(KOSDAQ, "N", "N", "N");
        assertThatThrownBy(() -> new KisStockTradingRestrictionResult(negative.typeResolution(), negative.suspensionStatus(),
                negative.liquidationStatus(), positive.spacStatus(), positive.managementStatus(), positive.investmentCautionStatus(),
                positive.restrictionVersion(), positive.sourceRevision())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsValuePreservingJsonRoundTripsWithAndWithoutKrxMatches() throws Exception {
        var json = new ObjectMapper();
        var matchedInput = policy().resolve(matching(batch(KisStockMasterParsingFixture.row(KOSPI)),
                stock(KOSPI, "005930", "KR7005930003"))).rowResults().getFirst();
        var matched = new KisStockTradingRestrictionPolicy().evaluate(matchedInput);
        for (var original : new KisStockTradingRestrictionResult[]{result, observedResult(" ", "?"), matched,
                observedResult(KOSDAQ, "Y", "N", " "), observedResult(KOSDAQ, "?", "y", "Y")}) {
            var restored = json.readValue(json.writeValueAsBytes(original), KisStockTradingRestrictionResult.class);
            assertThat(restored).isEqualTo(original);
            assertThat(restored.typeResolution()).isNotSameAs(original.typeResolution());
            assertThat(restored.typeResolution().kisClassification().rawRecord())
                    .isEqualTo(original.typeResolution().kisClassification().rawRecord());
        }
        assertThat(result.typeResolution().identityMatch()).isNull();
        assertThat(result.typeResolution().referenceSecurityType()).isNull();
        assertThat(matched.typeResolution().identityMatch()).isNotNull();
        assertThat(json.valueToTree(result).get("investmentCautionStatus").textValue()).isEqualTo("FIELD_NOT_PROVIDED");
    }

    private KisStockTradingRestrictionResult rebuild(
            KisStockTradingFlagStatus suspension, KisStockTradingFlagStatus liquidation, String version, String revision
    ) {
        return new KisStockTradingRestrictionResult(result.typeResolution(), suspension, liquidation,
                result.spacStatus(), result.managementStatus(), result.investmentCautionStatus(), version, revision);
    }

    private static KisStockTradingRestrictionResult copyAdditional(
            KisStockTradingRestrictionResult source, KisStockTradingFlagStatus spac,
            KisStockTradingFlagStatus management, KisStockTradingFlagStatus caution
    ) {
        return new KisStockTradingRestrictionResult(source.typeResolution(), source.suspensionStatus(), source.liquidationStatus(),
                spac, management, caution, source.restrictionVersion(), source.sourceRevision());
    }

    private static KisStockTradingRestrictionResult observedResult(
            KisStockMasterMarket market, String spac, String management, String caution
    ) {
        byte[] row = KisStockMasterParsingFixture.row(market);
        KisStockMasterParsingFixture.put(row, market == KOSPI ? 90 : 85, spac);
        KisStockMasterParsingFixture.put(row, market == KOSPI ? 123 : 118, management);
        if (market == KOSDAQ) {
            KisStockMasterParsingFixture.put(row, 91, caution);
        }
        var raw = new KisStockMasterParser().parse(market, KisStockMasterParsingFixture.content(row)).records().getFirst();
        var classified = new KisStockMasterTypeClassificationPolicy().classify(market, raw);
        var input = new KisKrxStockTypeResolutionResult(classified, null, null, classified.securityType(),
                classified.securityType() == null ? KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED
                        : KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY);
        return new KisStockTradingRestrictionPolicy().evaluate(input);
    }

    private static KisStockTradingRestrictionResult observedResult(String suspension, String liquidation) {
        byte[] row = KisStockMasterParsingFixture.row(KOSPI);
        KisStockMasterParsingFixture.put(row, 121, suspension);
        KisStockMasterParsingFixture.put(row, 122, liquidation);
        var input = policy().resolve(matching(batch(row))).rowResults().getFirst();
        return new KisStockTradingRestrictionPolicy().evaluate(input);
    }
}
