package com.stock.strategy.universe.eligibility.restriction.kiskrx.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.master.matching.kiskrx.KisKrxStockIdentityMatchingPolicy;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.restriction.kis.KisStockTradingRestrictionPolicy;
import com.stock.strategy.universe.eligibility.restriction.kiskrx.KisKrxStockRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.krx.KrxStockSectionRestrictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.baseBatch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.batch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.inputs;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.parsed;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisKrxStockRestrictionObservationResultTest {
    private final KisStockTradingRestrictionPolicy kisPolicy = new KisStockTradingRestrictionPolicy();
    private final KrxStockSectionRestrictionPolicy krxPolicy = new KrxStockSectionRestrictionPolicy();
    private final KisKrxStockRestrictionObservationPolicy observationPolicy =
            new KisKrxStockRestrictionObservationPolicy(kisPolicy, krxPolicy);
    private final KisKrxStockTypeResolutionResult input = matchedKospi();
    private final KisKrxStockRestrictionObservationResult result = observationPolicy.evaluate(input);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankObservationVersion(String version) {
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationResult(result.kisObservation(), result.krxObservation(), version))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("observationVersion must not be blank.");
    }

    @Test
    void requiresBothSourceResultsEvenWhenKrxHasNoMatch() {
        var unmatched = observationPolicy.evaluate(policy().resolve(matching(baseBatch())).rowResults().getFirst());
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationResult(null, unmatched.krxObservation(), result.observationVersion()))
                .isInstanceOf(NullPointerException.class).hasMessage("kisObservation must not be null.");
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationResult(unmatched.kisObservation(), null, result.observationVersion()))
                .isInstanceOf(NullPointerException.class).hasMessage("krxObservation must not be null.");
        assertThat(unmatched.krxObservation().typeResolution().identityMatch()).isNull();
    }

    @Test
    void rejectsCombiningDifferentStocksInEitherDirection() {
        var other = policy().resolve(matching(baseBatch())).rowResults().getLast();
        assertMismatchedInputsRejected(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NAME", "FLAG", "BASE_DATE"})
    void rejectsDifferentKisRawEvidenceEvenWhenBothIdentifiersAreEqual(String field) {
        byte[] row = KisStockMasterParsingFixture.row(KOSPI);
        if (field.equals("NAME")) {
            row = KisStockMasterParsingFixture.row(KOSPI, "005930", "KR7005930003", "CHANGED NAME");
        } else {
            KisStockMasterParsingFixture.put(row, field.equals("FLAG") ? 123 : 265, field.equals("FLAG") ? "Y" : "20260701");
        }
        var other = policy().resolve(matching(batch(row), stock(KOSPI, "005930", "KR7005930003"))).rowResults().getFirst();
        assertThat(other.kisClassification().rawRecord().symbol()).isEqualTo(input.kisClassification().rawRecord().symbol());
        assertThat(other.kisClassification().rawRecord().standardCode()).isEqualTo(input.kisClassification().rawRecord().standardCode());
        assertMismatchedInputsRejected(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISU_NM", "SECT_TP_NM", "LIST_DD"})
    void rejectsDifferentKrxRawEvidenceEvenWhenTheMatchedKisRowIsIdentical(String field) {
        var other = policy().resolve(matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003")
                .put(field, "CHANGED_VALUE"))).rowResults().getFirst();
        assertThat(other.kisClassification()).isEqualTo(input.kisClassification());
        assertMismatchedInputsRejected(other);
    }

    @Test
    void rejectsReplacingAnExactMatchWithAnUnmatchedInputForTheSameStock() {
        var unmatched = policy().resolve(matching(baseBatch())).rowResults().getFirst();
        assertThat(unmatched.kisClassification()).isEqualTo(input.kisClassification());
        assertMismatchedInputsRejected(unmatched);
    }

    @Test
    void rejectsDifferentTypeRuleMetadataWithoutChangingEitherRawRecord() {
        var classified = input.kisClassification();
        var changed = new KisStockMasterTypeClassificationResult(classified.market(), classified.rawRecord(), classified.securityType(),
                classified.reasonCode(), "CHANGED_TYPE_RULE_VERSION", classified.sourceRevision());
        var other = new KisKrxStockTypeResolutionResult(changed, input.identityMatch(), input.krxClassification(),
                input.referenceSecurityType(), input.reasonCode());
        assertMismatchedInputsRejected(other);
    }

    @Test
    void acceptsSeparatelyRestoredButValueEqualInputsAndPreservesSourceObjects() throws Exception {
        var json = new ObjectMapper();
        var restoredInput = json.readValue(json.writeValueAsBytes(input), KisKrxStockTypeResolutionResult.class);
        assertThat(restoredInput).isEqualTo(input).isNotSameAs(input);
        var kis = kisPolicy.evaluate(input);
        var krx = krxPolicy.evaluate(restoredInput);

        var combined = new KisKrxStockRestrictionObservationResult(kis, krx, result.observationVersion());

        assertThat(combined).isEqualTo(result);
        assertThat(combined.kisObservation()).isSameAs(kis);
        assertThat(combined.krxObservation()).isSameAs(krx);
    }

    @Test
    void roundTripsMatchedUnmatchedUnverifiedAndConflictingInputsWithoutAddingApprovalFields() throws Exception {
        var json = new ObjectMapper();
        var krx = inputs();
        krx.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")
                .put("SECT_TP_NM", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)")));
        var matchedKosdaq = policy().resolve(new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), krx)).rowResults().getLast();
        var conflict = policy().resolve(matching(baseBatch(), stock(KOSPI, "111111", "KR7111111111"))).rowResults().get(1);
        var unknown = policy().resolve(matching(baseBatch())).rowResults().getFirst();
        for (var source : new KisKrxStockTypeResolutionResult[]{input, matchedKosdaq, conflict, unknown}) {
            var original = observationPolicy.evaluate(source);
            var restored = json.readValue(json.writeValueAsBytes(original), KisKrxStockRestrictionObservationResult.class);
            assertThat(restored).isEqualTo(original);
            assertThat(restored.kisObservation().typeResolution()).isEqualTo(restored.krxObservation().typeResolution())
                    .isNotSameAs(restored.krxObservation().typeResolution());
            assertThat(json.valueToTree(original).properties()).extracting(entry -> entry.getKey())
                    .containsExactlyInAnyOrder("kisObservation", "krxObservation", "observationVersion");
        }
    }

    private void assertMismatchedInputsRejected(KisKrxStockTypeResolutionResult other) {
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationResult(result.kisObservation(), krxPolicy.evaluate(other),
                result.observationVersion())).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KIS and KRX observations must share the same type resolution.");
        assertThatThrownBy(() -> new KisKrxStockRestrictionObservationResult(kisPolicy.evaluate(other), result.krxObservation(),
                result.observationVersion())).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KIS and KRX observations must share the same type resolution.");
    }

    private static KisKrxStockTypeResolutionResult matchedKospi() {
        return policy().resolve(matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"))).rowResults().getFirst();
    }
}
