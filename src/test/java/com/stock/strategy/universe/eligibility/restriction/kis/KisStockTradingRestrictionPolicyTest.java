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
        assertThat(result.restrictionVersion()).isEqualTo("KIS_STOCK_TRADING_FLAG_OBSERVATION_V1");
        assertThat(result.sourceRevision()).isEqualTo("277ec0eb7a9b7f63b6807829286c80f36649dad2");
        assertThat(restrictionPolicy.evaluate(input)).isEqualTo(result);
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
        var input = policy().resolve(matching(batch(row), stock(KOSPI, "111111", "KR7111111111")))
                .rowResults().getFirst();

        var result = restrictionPolicy.evaluate(input);

        assertThat(input.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.TYPE_CONFLICT);
        assertThat(input.referenceSecurityType()).isNull();
        assertThat(input.identityMatch()).isNotNull();
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.suspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.liquidationStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
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

    private static KisStockTradingFlagStatus expected(String value) {
        return "Y".equals(value) ? KisStockTradingFlagStatus.Y_OBSERVED
                : "N".equals(value) ? KisStockTradingFlagStatus.N_OBSERVED : KisStockTradingFlagStatus.VALUE_UNVERIFIED;
    }

    private static KisKrxStockTypeResolutionResult resolution(KisStockMasterMarket market, String suspension, String liquidation) {
        var raw = new KisStockMasterParser().parse(market,
                KisStockMasterParsingFixture.content(flaggedRow(market, suspension, liquidation))).records().getFirst();
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
