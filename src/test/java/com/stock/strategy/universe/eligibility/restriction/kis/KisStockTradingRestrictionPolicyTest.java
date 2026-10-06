package com.stock.strategy.universe.eligibility.restriction.kis;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.baseBatch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.batch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.etfRow;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockTradingRestrictionPolicyTest {
    private final KisStockTradingRestrictionPolicy restrictionPolicy = new KisStockTradingRestrictionPolicy();

    @ParameterizedTest
    @MethodSource("flagCombinations")
    void observesBothFieldsIndependentlyForBothMarkets(
            KisStockMasterMarket market, String suspension, String liquidation,
            KisStockTradingFlagStatus expectedSuspension, KisStockTradingFlagStatus expectedLiquidation
    ) {
        var input = resolution(market, suspension, liquidation);

        var result = restrictionPolicy.evaluate(input);

        assertThat(result.suspensionStatus()).isEqualTo(expectedSuspension);
        assertThat(result.liquidationStatus()).isEqualTo(expectedLiquidation);
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().kisClassification().rawRecord().rawSuspension()).isEqualTo(suspension);
        assertThat(result.typeResolution().kisClassification().rawRecord().rawLiquidation()).isEqualTo(liquidation);
        assertThat(result.restrictionVersion()).isEqualTo("KIS_STOCK_TRADING_FLAG_OBSERVATION_V2");
        assertThat(result.sourceRevision()).isEqualTo("277ec0eb7a9b7f63b6807829286c80f36649dad2");
        assertThat(restrictionPolicy.evaluate(input)).isEqualTo(result);
    }

    @ParameterizedTest
    @MethodSource("additionalFlagCombinations")
    void observesAdditionalFieldsIndependentlyWithoutChangingTheInput(
            KisStockMasterMarket market, String spac, String management, String caution
    ) {
        var input = resolution(market, "Y", "N", spac, management, caution);

        var result = restrictionPolicy.evaluate(input);

        assertThat(result.spacStatus()).isEqualTo(expected(spac));
        assertThat(result.managementStatus()).isEqualTo(expected(management));
        assertThat(result.investmentCautionStatus()).isEqualTo(expected(caution));
        assertThat(result.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().kisClassification().rawRecord().rawSpac()).isEqualTo(spac);
        assertThat(result.typeResolution().kisClassification().rawRecord().rawManagement()).isEqualTo(management);
        assertThat(result.typeResolution().kisClassification().rawRecord().rawInvestmentCaution()).isEqualTo(caution);
        assertThat(restrictionPolicy.evaluate(input)).isEqualTo(result);
    }

    @ParameterizedTest
    @MethodSource("unreviewedFlags")
    void preservesOtherObservationsWhenAnAdditionalFieldIsUnverified(KisStockMasterMarket market, String rawFlag) {
        String caution = market == KOSPI ? null : "Y";
        var spacUnknown = restrictionPolicy.evaluate(resolution(market, "Y", "N", rawFlag, "Y", caution));
        var managementUnknown = restrictionPolicy.evaluate(resolution(market, "Y", "N", "Y", rawFlag, caution));

        assertThat(spacUnknown.spacStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(spacUnknown.managementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(spacUnknown.investmentCautionStatus()).isEqualTo(expected(caution));
        assertThat(managementUnknown.spacStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(managementUnknown.managementStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(managementUnknown.investmentCautionStatus()).isEqualTo(expected(caution));
        if (market == KOSDAQ) {
            var cautionUnknown = restrictionPolicy.evaluate(resolution(market, "Y", "N", "Y", "Y", rawFlag));
            assertThat(cautionUnknown.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
            assertThat(cautionUnknown.spacStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
            assertThat(cautionUnknown.managementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        }
    }

    @Test
    void distinguishesUnprovidedKospiCautionFromBlankAndNegativeKosdaqObservations() {
        var kospi = restrictionPolicy.evaluate(resolution(KOSPI, "N", "N", "N", "N", null));
        var blank = restrictionPolicy.evaluate(resolution(KOSDAQ, "N", "N", "N", "N", " "));
        var negative = restrictionPolicy.evaluate(resolution(KOSDAQ, "N", "N", "N", "N", "N"));

        assertThat(kospi.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.FIELD_NOT_PROVIDED);
        assertThat(blank.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(negative.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
    }

    @Test
    void retainsAllFiveSimultaneousPositiveObservations() {
        var result = restrictionPolicy.evaluate(resolution(KOSDAQ, "Y", "Y", "Y", "Y", "Y"));

        assertThat(result.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.spacStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.managementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
    }

    @ParameterizedTest
    @MethodSource("unreviewedFlags")
    void neverNormalizesOrDefaultsAnUnknownFieldToNormal(KisStockMasterMarket market, String rawFlag) {
        var suspensionUnknown = restrictionPolicy.evaluate(resolution(market, rawFlag, "Y"));
        var liquidationUnknown = restrictionPolicy.evaluate(resolution(market, "N", rawFlag));

        assertThat(suspensionUnknown.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(suspensionUnknown.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(liquidationUnknown.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(liquidationUnknown.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(suspensionUnknown.typeResolution().kisClassification().rawRecord().rawSuspension()).isEqualTo(rawFlag);
        assertThat(liquidationUnknown.typeResolution().kisClassification().rawRecord().rawLiquidation()).isEqualTo(rawFlag);
    }

    @Test
    void preservesAllUnmatchedKisRowsIncludingEtfsWithoutRequiringKrxIdentity() {
        var inputs = policy().resolve(matching(baseBatch())).rowResults();

        var results = inputs.stream().map(restrictionPolicy::evaluate).toList();

        assertThat(results).hasSize(3).allSatisfy(result -> {
            assertThat(result.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
            assertThat(result.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
            assertThat(result.typeResolution().identityMatch()).isNull();
        });
        assertThat(results).extracting(result -> result.typeResolution().kisClassification().rawRecord().symbol())
                .containsExactly("005930", "111111", "0001A0");
        for (int i = 0; i < inputs.size(); i++) {
            assertThat(results.get(i).typeResolution()).isSameAs(inputs.get(i));
        }
        assertThat(results.get(1).typeResolution().referenceSecurityType()).isEqualTo(StockSecurityType.ETF);
    }

    @Test
    void preservesAnExactKrxMatchAndConflictingTypesWithoutChangingFlagObservations() {
        byte[] row = etfRow();
        KisStockMasterParsingFixture.put(row, 121, "Y");
        KisStockMasterParsingFixture.put(row, 122, "Y");
        KisStockMasterParsingFixture.put(row, 90, "Y");
        KisStockMasterParsingFixture.put(row, 123, "?");
        var input = policy().resolve(matching(batch(row), stock(KOSPI, "111111", "KR7111111111")))
                .rowResults().getFirst();

        var result = restrictionPolicy.evaluate(input);

        assertThat(input.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.TYPE_CONFLICT);
        assertThat(input.referenceSecurityType()).isNull();
        assertThat(input.identityMatch()).isNotNull();
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.spacStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.managementStatus()).isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(result.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.FIELD_NOT_PROVIDED);
    }

    @Test
    void doesNotInferListingStatusOrEffectiveDatesFromTradingFlags() {
        byte[] row = flaggedRow(KOSPI, "Y", "N");
        KisStockMasterParsingFixture.put(row, 166, "NOT_DATE");
        KisStockMasterParsingFixture.put(row, 265, "        ");
        var input = policy().resolve(matching(batch(row))).rowResults().getFirst();

        var result = restrictionPolicy.evaluate(input);

        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().kisClassification().rawRecord().rawListingDate()).isEqualTo("NOT_DATE");
        assertThat(result.typeResolution().kisClassification().rawRecord().rawBaseDate()).isEqualTo("        ");
        assertThat(result.typeResolution().referenceSecurityType()).isNull();
        assertThat(result.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
    }

    @Test
    void neverCarriesObservationsBetweenCalls() {
        var first = restrictionPolicy.evaluate(resolution(KOSPI, "Y", "N"));
        var second = restrictionPolicy.evaluate(resolution(KOSPI, "N", "Y"));

        assertThat(first.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(first.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(second.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(second.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        var positive = restrictionPolicy.evaluate(resolution(KOSDAQ, "Y", "Y", "Y", "Y", "Y"));
        var negative = restrictionPolicy.evaluate(resolution(KOSDAQ, "N", "N", "N", "N", "N"));
        assertThat(positive.spacStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(positive.managementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(positive.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(negative.spacStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(negative.managementStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(negative.investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> restrictionPolicy.evaluate(null)).isInstanceOf(NullPointerException.class);
    }

    private static Stream<Arguments> flagCombinations() {
        return Stream.of(KisStockMasterMarket.values()).flatMap(market -> Stream.of("Y", "N", " ")
                .flatMap(suspension -> Stream.of("Y", "N", " ").map(liquidation -> Arguments.of(
                        market, suspension, liquidation, expected(suspension), expected(liquidation)))));
    }

    private static Stream<Arguments> unreviewedFlags() {
        return Stream.of(KisStockMasterMarket.values()).flatMap(market -> Stream.of(" ", "y", "n", "?", "0", "9")
                .map(flag -> Arguments.of(market, flag)));
    }

    private static Stream<Arguments> additionalFlagCombinations() {
        return Stream.of(KisStockMasterMarket.values()).flatMap(market -> Stream.of("Y", "N", " ")
                .flatMap(spac -> Stream.of("Y", "N", " ").flatMap(management ->
                        (market == KOSPI ? Stream.<String>of((String) null) : Stream.of("Y", "N", " "))
                                .map(caution -> Arguments.of(market, spac, management, caution)))));
    }

    private static KisStockTradingFlagStatus expected(String value) {
        if (value == null) {
            return KisStockTradingFlagStatus.FIELD_NOT_PROVIDED;
        }
        return "Y".equals(value) ? KisStockTradingFlagStatus.Y_OBSERVED
                : "N".equals(value) ? KisStockTradingFlagStatus.N_OBSERVED : KisStockTradingFlagStatus.VALUE_UNVERIFIED;
    }

    private static KisKrxStockTypeResolutionResult resolution(KisStockMasterMarket market, String suspension, String liquidation) {
        return resolution(market, suspension, liquidation, "N", "N", market == KOSPI ? null : "N");
    }

    private static KisKrxStockTypeResolutionResult resolution(
            KisStockMasterMarket market, String suspension, String liquidation, String spac, String management, String caution
    ) {
        byte[] row = flaggedRow(market, suspension, liquidation);
        KisStockMasterParsingFixture.put(row, market == KOSPI ? 90 : 85, spac);
        KisStockMasterParsingFixture.put(row, market == KOSPI ? 123 : 118, management);
        if (market == KOSDAQ) {
            KisStockMasterParsingFixture.put(row, 91, caution);
        }
        var raw = new KisStockMasterParser().parse(market, KisStockMasterParsingFixture.content(row)).records().getFirst();
        var classified = new KisStockMasterTypeClassificationPolicy().classify(market, raw);
        return new KisKrxStockTypeResolutionResult(classified, null, null, classified.securityType(),
                classified.securityType() == null ? KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED
                        : KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY);
    }

    private static byte[] flaggedRow(KisStockMasterMarket market, String suspension, String liquidation) {
        byte[] row = KisStockMasterParsingFixture.row(market);
        KisStockMasterParsingFixture.put(row, market == KOSPI ? 121 : 116, suspension);
        KisStockMasterParsingFixture.put(row, market == KOSPI ? 122 : 117, liquidation);
        return row;
    }
}
