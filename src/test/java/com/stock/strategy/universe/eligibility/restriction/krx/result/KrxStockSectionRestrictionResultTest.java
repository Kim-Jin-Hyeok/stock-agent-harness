package com.stock.strategy.universe.eligibility.restriction.krx.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.master.matching.kiskrx.KisKrxStockIdentityMatchingPolicy;
import com.stock.strategy.universe.eligibility.restriction.krx.KrxStockSectionRestrictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

class KrxStockSectionRestrictionResultTest {
    private final KrxStockSectionRestrictionResult result = observedResult();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingVersionOrSourceReference(String value) {
        assertThatThrownBy(() -> rebuild(result.reasonCode(), value, result.sourceReference(), result.sourceSha256()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rebuild(result.reasonCode(), result.restrictionVersion(), value, result.sourceSha256()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "A000000000000000000000000000000000000000000000000000000000000000",
            "000000000000000000000000000000000000000000000000000000000000000g",
            "00000000000000000000000000000000000000000000000000000000000000000"})
    void rejectsMalformedSourceHash(String value) {
        assertThatThrownBy(() -> rebuild(result.reasonCode(), result.restrictionVersion(), result.sourceReference(), value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresSourceResolutionAndReason() {
        assertThatThrownBy(() -> new KrxStockSectionRestrictionResult(null, result.reasonCode(), result.restrictionVersion(),
                result.sourceReference(), result.sourceSha256())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> rebuild(null, result.restrictionVersion(), result.sourceReference(), result.sourceSha256()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void forbidsUsingSectionInterpretationWithoutAnExactMatch() {
        var input = policy().resolve(matching(baseBatch())).rowResults().getLast();
        for (var reason : KrxStockSectionRestrictionReasonCode.values()) {
            if (reason != KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED) {
                assertThatThrownBy(() -> new KrxStockSectionRestrictionResult(input, reason,
                        result.restrictionVersion(), result.sourceReference(), result.sourceSha256()))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThat(new KrxStockSectionRestrictionResult(input, KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED,
                result.restrictionVersion(), result.sourceReference(), result.sourceSha256()).typeResolution()).isSameAs(input);
        assertThatThrownBy(() -> rebuild(KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED,
                result.restrictionVersion(), result.sourceReference(), result.sourceSha256())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forbidsObservedKosdaqInterpretationsForKospiInputs() {
        var input = policy().resolve(matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"))).rowResults().getFirst();
        for (var reason : KrxStockSectionRestrictionReasonCode.values()) {
            if (reason != KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED
                    && reason != KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED) {
                assertThatThrownBy(() -> new KrxStockSectionRestrictionResult(input, reason,
                        result.restrictionVersion(), result.sourceReference(), result.sourceSha256()))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThat(new KrxStockSectionRestrictionResult(input, KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED,
                result.restrictionVersion(), result.sourceReference(), result.sourceSha256()).typeResolution()).isSameAs(input);
    }

    @Test
    void supportsValuePreservingJsonRoundTripForObservedAndUnmatchedResults() throws Exception {
        var json = new ObjectMapper();
        var restored = json.readValue(json.writeValueAsBytes(result), KrxStockSectionRestrictionResult.class);
        assertThat(restored).isEqualTo(result);
        assertThat(restored.typeResolution()).isNotSameAs(result.typeResolution());
        assertThat(restored.typeResolution().identityMatch().krxRecord()).isEqualTo(result.typeResolution().identityMatch().krxRecord());
        var unmatched = new KrxStockSectionRestrictionPolicy().evaluate(policy().resolve(matching(baseBatch())).rowResults().get(1));
        assertThat(json.readValue(json.writeValueAsBytes(unmatched), KrxStockSectionRestrictionResult.class)).isEqualTo(unmatched);
    }

    private KrxStockSectionRestrictionResult rebuild(KrxStockSectionRestrictionReasonCode reason, String version, String reference, String hash) {
        return new KrxStockSectionRestrictionResult(result.typeResolution(), reason, version, reference, hash);
    }

    private static KrxStockSectionRestrictionResult observedResult() {
        var inputs = inputs();
        inputs.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")
                .put("SECT_TP_NM", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)")));
        var input = policy().resolve(new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), inputs)).rowResults().getLast();
        return new KrxStockSectionRestrictionPolicy().evaluate(input);
    }
}
