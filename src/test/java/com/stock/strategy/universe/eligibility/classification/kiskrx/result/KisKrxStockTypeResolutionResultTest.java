package com.stock.strategy.universe.eligibility.classification.kiskrx.result;

import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.baseBatch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.parsed;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.syntheticKis;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.syntheticKrx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisKrxStockTypeResolutionResultTest {
    private final KisKrxStockIdentityMatchResult match = matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"))
            .rowResults().getFirst();

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,NULL,NULL,TYPE_UNVERIFIED", "ETF,NULL,ETF,KIS_TYPE_ONLY",
            "NULL,COMMON_STOCK,COMMON_STOCK,KRX_TYPE_SUPPLEMENTED", "NULL,PREFERRED_STOCK,PREFERRED_STOCK,KRX_TYPE_SUPPLEMENTED",
            "COMMON_STOCK,COMMON_STOCK,COMMON_STOCK,SOURCES_AGREE", "ETF,ETF,ETF,SOURCES_AGREE",
            "ETF,COMMON_STOCK,NULL,TYPE_CONFLICT"
    }, nullValues = "NULL")
    void enforcesReasonAndReferenceTypeConsistencyForSyntheticSourceInterpretations(
            StockSecurityType kisType, StockSecurityType krxType, StockSecurityType reference,
            KisKrxStockTypeResolutionReasonCode reason
    ) {
        var kis = syntheticKis(KOSPI, match.matchedKisRecord(), kisType);
        var krx = syntheticKrx(match.krxRecord(), krxType);

        var result = new KisKrxStockTypeResolutionResult(kis, match, krx, reference, reason);

        assertThat(result.kisClassification()).isSameAs(kis);
        assertThat(result.krxClassification()).isSameAs(krx);
        assertThat(result.identityMatch()).isSameAs(match);
        assertThat(result.referenceSecurityType()).isEqualTo(reference);
        assertThat(result.reasonCode()).isEqualTo(reason);
        for (var invalidReason : KisKrxStockTypeResolutionReasonCode.values()) {
            if (invalidReason != reason) {
                assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(kis, match, krx, reference, invalidReason))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
        for (var invalidReference : StockSecurityType.values()) {
            if (invalidReference != reference) {
                assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(kis, match, krx, invalidReference, reason))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
        if (reference != null) {
            assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(kis, match, krx, null, reason))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsKisOnlyOrUnverifiedResultsWithoutInventingAKrxMatch() {
        var known = syntheticKis(KOSPI, match.matchedKisRecord(), StockSecurityType.ETF);
        var unknown = syntheticKis(KOSPI, match.matchedKisRecord(), null);
        assertThat(new KisKrxStockTypeResolutionResult(known, null, null, StockSecurityType.ETF,
                KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY).identityMatch()).isNull();
        assertThat(new KisKrxStockTypeResolutionResult(unknown, null, null, null,
                KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED).krxClassification()).isNull();
    }

    @Test
    void requiresNonNullKisClassificationAndReason() {
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(null, null, null, null,
                KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(syntheticKis(KOSPI, match.matchedKisRecord(), null),
                null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresBothKrxEvidenceAndItsExactMatch() {
        var kis = syntheticKis(KOSPI, match.matchedKisRecord(), null);
        var krx = syntheticKrx(match.krxRecord(), StockSecurityType.COMMON_STOCK);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(kis, null, krx, StockSecurityType.COMMON_STOCK,
                KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(kis, match, null, null,
                KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonexactIdentityEvidenceRatherThanTreatingItAsASupplement() {
        var failed = matching(baseBatch(), stock(KOSPI, "005930", "OTHER_STANDARD")).rowResults().getFirst();
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(syntheticKis(KOSPI, match.matchedKisRecord(), null), failed,
                syntheticKrx(failed.krxRecord(), StockSecurityType.COMMON_STOCK), StockSecurityType.COMMON_STOCK,
                KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsChangedKisMarketOrEitherRawRowEvenWhenIdentifiersStillAgree() {
        var kis = syntheticKis(KOSPI, match.matchedKisRecord(), null);
        var krx = syntheticKrx(match.krxRecord(), StockSecurityType.COMMON_STOCK);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(syntheticKis(KOSDAQ, match.matchedKisRecord(), null), match,
                krx, StockSecurityType.COMMON_STOCK, KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED))
                .isInstanceOf(IllegalArgumentException.class);
        var otherKis = baseBatch().marketResults().getFirst().records().get(1);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(syntheticKis(KOSPI, otherKis, null), match,
                krx, StockSecurityType.COMMON_STOCK, KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED))
                .isInstanceOf(IllegalArgumentException.class);
        var changed = parsed(stock(KOSPI, "005930", "KR7005930003").put("ISU_NM", "ALTERED_NAME")).records().getFirst();
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionResult(kis, match, syntheticKrx(changed, StockSecurityType.COMMON_STOCK),
                StockSecurityType.COMMON_STOCK, KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
