package com.stock.strategy.universe.eligibility.restriction.kiskrx;

import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.matching.kiskrx.KisKrxStockIdentityMatchingPolicy;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.restriction.kis.KisStockTradingRestrictionPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import com.stock.strategy.universe.eligibility.restriction.krx.KrxStockSectionRestrictionPolicy;
import com.stock.strategy.universe.eligibility.restriction.krx.result.KrxStockSectionRestrictionReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.baseBatch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.inputs;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.parsed;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisKrxStockRestrictionObservationPolicyTest {
    private final KisStockTradingRestrictionPolicy kisPolicy = new KisStockTradingRestrictionPolicy();
    private final KrxStockSectionRestrictionPolicy krxPolicy = new KrxStockSectionRestrictionPolicy();
    private final KisKrxStockRestrictionObservationPolicy observationPolicy =
            new KisKrxStockRestrictionObservationPolicy(kisPolicy, krxPolicy);

    @Test
    void delegatesExactlyOnceToBothPoliciesWithTheSameInputAndRetainsTheirResults() {
        var input = kosdaqInput("Y", "N", " ", "Y", "?", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)");
        var kis = mock(KisStockTradingRestrictionPolicy.class);
        var krx = mock(KrxStockSectionRestrictionPolicy.class);
        var kisResult = kisPolicy.evaluate(input);
        var krxResult = krxPolicy.evaluate(input);
        when(kis.evaluate(input)).thenReturn(kisResult);
        when(krx.evaluate(input)).thenReturn(krxResult);

        var result = new KisKrxStockRestrictionObservationPolicy(kis, krx).evaluate(input);

        assertThat(result.kisObservation()).isSameAs(kisResult);
        assertThat(result.krxObservation()).isSameAs(krxResult);
        assertThat(result.kisObservation().typeResolution()).isSameAs(input);
        assertThat(result.krxObservation().typeResolution()).isSameAs(input);
        assertThat(result.observationVersion()).isEqualTo("KIS_KRX_STOCK_RESTRICTION_OBSERVATION_V1");
        verify(kis, times(1)).evaluate(input);
        verify(krx, times(1)).evaluate(input);
    }

    @ParameterizedTest
    @CsvSource({
            "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c),SPAC_OBSERVED",
            "\uad00\ub9ac\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c),MANAGEMENT_DESIGNATION_OBSERVED",
            "\ud22c\uc790\uc8fc\uc758\ud658\uae30\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c),INVESTMENT_CAUTION_OBSERVED",
            "\uc6b0\ub7c9\uae30\uc5c5\ubd80,NO_TARGET_RESTRICTION_OBSERVED",
            "UNKNOWN_SECTION,SECTION_UNVERIFIED"
    })
    void retainsAllPositiveKisFlagsWithoutOverwritingDifferentOrUnverifiedKrxSections(
            String section, KrxStockSectionRestrictionReasonCode reason
    ) {
        var input = kosdaqInput("Y", "Y", "Y", "Y", "Y", section);

        var result = observationPolicy.evaluate(input);

        var kis = result.kisObservation();
        assertThat(List.of(kis.suspensionStatus(), kis.liquidationStatus(), kis.spacStatus(),
                kis.managementStatus(), kis.investmentCautionStatus())).containsOnly(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.krxObservation().reasonCode()).isEqualTo(reason);
        assertThat(result.krxObservation().typeResolution().identityMatch().krxRecord().rawSection()).isEqualTo(section);
        assertThat(kis.typeResolution()).isSameAs(input);
        assertThat(input.referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(observationPolicy.evaluate(input)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", " ", "?", "y"})
    void preservesNegativeAndUnverifiedKisFieldsEvenWhenKrxObservesASpac(String flag) {
        var input = kosdaqInput("N", "N", flag, flag, flag, "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)");

        var result = observationPolicy.evaluate(input);

        var expected = flag.equals("N") ? KisStockTradingFlagStatus.N_OBSERVED : KisStockTradingFlagStatus.VALUE_UNVERIFIED;
        assertThat(result.kisObservation().spacStatus()).isEqualTo(expected);
        assertThat(result.kisObservation().managementStatus()).isEqualTo(expected);
        assertThat(result.kisObservation().investmentCautionStatus()).isEqualTo(expected);
        assertThat(result.krxObservation().reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.SPAC_OBSERVED);
        assertThat(result.kisObservation().typeResolution().kisClassification().rawRecord().rawSpac()).isEqualTo(flag);
    }

    @Test
    void retainsEveryUnmatchedKisRowAndItsOriginalOrderWithoutRequiringKrxData() {
        var inputs = policy().resolve(matching(baseBatch())).rowResults();

        var results = inputs.stream().map(observationPolicy::evaluate).toList();

        assertThat(results).hasSize(3).extracting(result -> result.kisObservation().typeResolution().kisClassification().rawRecord().symbol())
                .containsExactly("005930", "111111", "0001A0");
        for (int index = 0; index < inputs.size(); index++) {
            assertThat(results.get(index).kisObservation()).isEqualTo(kisPolicy.evaluate(inputs.get(index)));
            assertThat(results.get(index).krxObservation()).isEqualTo(krxPolicy.evaluate(inputs.get(index)));
            assertThat(results.get(index).krxObservation().reasonCode())
                    .isEqualTo(KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED);
        }
        assertThat(results.get(1).kisObservation().typeResolution().referenceSecurityType()).isEqualTo(StockSecurityType.ETF);
    }

    @Test
    void preservesKospiCautionNotProvidedAndUnverifiedKrxSectionEvenForAnExactMatch() {
        var input = policy().resolve(matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003")
                .put("SECT_TP_NM", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)"))).rowResults().getFirst();

        var result = observationPolicy.evaluate(input);

        assertThat(result.kisObservation().investmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.FIELD_NOT_PROVIDED);
        assertThat(result.kisObservation().spacStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.krxObservation().reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED);
        assertThat(result.krxObservation().typeResolution()).isSameAs(input);
    }

    @Test
    void doesNotResolveTypeConflictsOrDiscardSourceMetadata() {
        var input = policy().resolve(matching(baseBatch(), stock(KOSPI, "111111", "KR7111111111"))).rowResults().get(1);

        var result = observationPolicy.evaluate(input);

        assertThat(input.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.TYPE_CONFLICT);
        assertThat(result.kisObservation().typeResolution()).isSameAs(input);
        assertThat(result.krxObservation().typeResolution()).isSameAs(input);
        assertThat(input.referenceSecurityType()).isNull();
        assertThat(result.kisObservation().restrictionVersion()).isEqualTo(KisStockTradingRestrictionPolicy.RESTRICTION_VERSION);
        assertThat(result.kisObservation().sourceRevision()).isEqualTo(KisStockTradingRestrictionPolicy.SOURCE_REVISION);
        assertThat(result.krxObservation().restrictionVersion()).isEqualTo(KrxStockSectionRestrictionPolicy.RESTRICTION_VERSION);
        assertThat(result.krxObservation().sourceReference()).isEqualTo(KrxStockSectionRestrictionPolicy.SOURCE_REFERENCE);
        assertThat(result.krxObservation().sourceSha256()).isEqualTo(KrxStockSectionRestrictionPolicy.SOURCE_SHA256);
    }

    @Test
    void keepsRepeatedCallsIndependentAcrossPositiveNegativeAndUnmatchedInputs() {
        var positive = kosdaqInput("Y", "Y", "Y", "Y", "Y", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)");
        var negative = kosdaqInput("N", "N", "N", "N", "N", "\uc6b0\ub7c9\uae30\uc5c5\ubd80");
        var first = observationPolicy.evaluate(positive);
        var second = observationPolicy.evaluate(negative);
        var unmatched = observationPolicy.evaluate(policy().resolve(matching(baseBatch())).rowResults().getFirst());

        assertThat(first.kisObservation().managementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(second.kisObservation().managementStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(first.krxObservation().reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.SPAC_OBSERVED);
        assertThat(second.krxObservation().reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.NO_TARGET_RESTRICTION_OBSERVED);
        assertThat(unmatched.krxObservation().reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED);
        assertThat(observationPolicy.evaluate(positive)).isEqualTo(first);
    }

    @Test
    void rejectsNullDependenciesAndInputWithoutInvokingSourcePolicies() {
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationPolicy(null, krxPolicy))
                .isInstanceOf(NullPointerException.class).hasMessage("kisPolicy must not be null.");
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationPolicy(kisPolicy, null))
                .isInstanceOf(NullPointerException.class).hasMessage("krxPolicy must not be null.");
        var kis = mock(KisStockTradingRestrictionPolicy.class);
        var krx = mock(KrxStockSectionRestrictionPolicy.class);
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationPolicy(kis, krx).evaluate(null))
                .isInstanceOf(NullPointerException.class).hasMessage("typeResolution must not be null.");
        verifyNoInteractions(kis, krx);
    }

    @Test
    void rejectsNullSourceResultsInsteadOfSynthesizingUnknownOrNegativeObservations() {
        var input = policy().resolve(matching(baseBatch())).rowResults().getFirst();
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationPolicy(mock(KisStockTradingRestrictionPolicy.class), krxPolicy)
                .evaluate(input)).isInstanceOf(NullPointerException.class).hasMessage("KIS observation must not be null.");
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationPolicy(kisPolicy, mock(KrxStockSectionRestrictionPolicy.class))
                .evaluate(input)).isInstanceOf(NullPointerException.class).hasMessage("KRX observation must not be null.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"KIS", "KRX", "BOTH"})
    void rejectsChangedInputFromEitherPolicyEvenWhenBothReturnedInputsAgree(String source) {
        var inputs = policy().resolve(matching(baseBatch())).rowResults();
        var requested = inputs.getFirst();
        var other = inputs.getLast();
        var kis = mock(KisStockTradingRestrictionPolicy.class);
        var krx = mock(KrxStockSectionRestrictionPolicy.class);
        when(kis.evaluate(requested)).thenReturn(kisPolicy.evaluate(source.equals("KRX") ? requested : other));
        when(krx.evaluate(requested)).thenReturn(krxPolicy.evaluate(source.equals("KIS") ? requested : other));

        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationPolicy(kis, krx).evaluate(requested))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source observations must preserve the requested type resolution.");
    }

    private static KisKrxStockTypeResolutionResult kosdaqInput(
            String suspension, String liquidation, String spac, String management, String caution, String section
    ) {
        var batch = baseBatch();
        byte[] row = KisStockMasterParsingFixture.row(KOSDAQ, "0001A0", "KR70001A0001", "ALPHA");
        KisStockMasterParsingFixture.put(row, 116, suspension);
        KisStockMasterParsingFixture.put(row, 117, liquidation);
        KisStockMasterParsingFixture.put(row, 85, spac);
        KisStockMasterParsingFixture.put(row, 118, management);
        KisStockMasterParsingFixture.put(row, 91, caution);
        byte[] content = KisStockMasterParsingFixture.content(row);
        var parsedKis = new KisStockMasterParser().parse(KOSDAQ, content);
        var original = batch.collection();
        var files = original.files().stream().map(file -> file.market() == KOSPI ? file
                : new StockMasterCollectionResult.FileObservation(file.market(), file.sourceUri(), file.startedAt(), file.finishedAt(),
                file.archivePath(), file.archiveBytes(), file.archiveSha256(), file.extractedPath(), content.length, parsedKis.inputSha256())).toList();
        var collection = new StockMasterCollectionResult(original.formatVersion(), original.collectionId(), original.evidenceScope(),
                original.startedAt(), original.finishedAt(), files);
        var changed = new StockMasterBatchParseResult(collection, List.of(batch.marketResults().getFirst(), parsedKis));
        var krx = inputs();
        krx.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001").put("SECT_TP_NM", section)));
        return policy().resolve(new KisKrxStockIdentityMatchingPolicy().match(changed, krx)).rowResults().getLast();
    }
}
