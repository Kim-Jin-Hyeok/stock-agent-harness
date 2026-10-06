package com.stock.strategy.universe.eligibility.classification.kiskrx.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.baseBatch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.parsed;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.syntheticKis;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.syntheticKrx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisKrxStockTypeResolutionBatchResultTest {
    private final KisKrxStockTypeResolutionBatchResult result = policy().resolve(
            matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003")));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingResolutionVersion(String version) {
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionBatchResult(version, result.matchingResult(), result.rowResults()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullInputsOrRowElements() {
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionBatchResult(result.resolutionVersion(), null, result.rowResults()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionBatchResult(result.resolutionVersion(), result.matchingResult(), null))
                .isInstanceOf(NullPointerException.class);
        var rows = new ArrayList<KisKrxStockTypeResolutionResult>();
        rows.add(null);
        assertThatThrownBy(() -> rebuild(rows)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingAddedDuplicatedOrReorderedKisResults() {
        assertThatThrownBy(() -> rebuild(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rebuild(result.rowResults().subList(0, 2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rebuild(result.rowResults().reversed())).isInstanceOf(IllegalArgumentException.class);
        var added = new ArrayList<>(result.rowResults());
        added.add(result.rowResults().getFirst());
        assertThatThrownBy(() -> rebuild(added)).isInstanceOf(IllegalArgumentException.class);
        var replaced = new ArrayList<>(result.rowResults());
        replaced.set(1, replaced.getFirst());
        assertThatThrownBy(() -> rebuild(replaced)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsChangedKisMarketOrRawInputForAnUnmatchedRow() {
        var etf = result.rowResults().get(1);
        var rows = new ArrayList<>(result.rowResults());
        rows.set(1, new KisKrxStockTypeResolutionResult(syntheticKis(KOSDAQ, etf.kisClassification().rawRecord(), StockSecurityType.ETF),
                null, null, StockSecurityType.ETF, KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY));
        assertThatThrownBy(() -> rebuild(rows)).isInstanceOf(IllegalArgumentException.class);
        rows.set(1, new KisKrxStockTypeResolutionResult(syntheticKis(KOSPI, result.rowResults().getFirst().kisClassification().rawRecord(),
                StockSecurityType.ETF), null, null, StockSecurityType.ETF, KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY));
        assertThatThrownBy(() -> rebuild(rows)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDroppingAnExactMatchOrAttachingEvidenceOutsideTheMatchingInput() {
        var first = result.rowResults().getFirst();
        var rows = new ArrayList<>(result.rowResults());
        rows.set(0, new KisKrxStockTypeResolutionResult(first.kisClassification(), null, null, null,
                KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED));
        assertThatThrownBy(() -> rebuild(rows)).isInstanceOf(IllegalArgumentException.class);
        var changedRaw = parsed(stock(KOSPI, "005930", "KR7005930003").put("ISU_NM", "ALTERED_NAME")).records().getFirst();
        var changedMatch = new KisKrxStockIdentityMatchResult(KOSPI, changedRaw, first.identityMatch().matchedKisRecord(),
                first.identityMatch().reasonCode());
        rows.set(0, new KisKrxStockTypeResolutionResult(first.kisClassification(), changedMatch,
                syntheticKrx(changedRaw, StockSecurityType.COMMON_STOCK), StockSecurityType.COMMON_STOCK,
                KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED));
        assertThatThrownBy(() -> rebuild(rows)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesCallerListAndPreservesImmutableInputAndRowInstances() {
        var rows = new ArrayList<>(result.rowResults());
        var copied = rebuild(rows);
        rows.clear();
        assertThat(copied.rowResults()).hasSize(3);
        assertThat(copied.rowResults().getFirst()).isSameAs(result.rowResults().getFirst());
        assertThat(copied.matchingResult()).isSameAs(result.matchingResult());
        assertThatThrownBy(() -> copied.rowResults().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsValuePreservingJsonRoundTripWithAllEvidenceAndNulls() throws Exception {
        var json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var restored = json.readValue(json.writeValueAsBytes(result), KisKrxStockTypeResolutionBatchResult.class);

        assertThat(restored).isEqualTo(result);
        assertThat(restored.rowResults().getFirst().kisClassification()).isNotSameAs(result.rowResults().getFirst().kisClassification());
        assertThat(restored.rowResults().get(1).krxClassification()).isNull();
        assertThat(restored.rowResults().getLast().referenceSecurityType()).isNull();
        assertThat(restored.matchingResult().kisBatch().collection()).isEqualTo(result.matchingResult().kisBatch().collection());
    }

    private KisKrxStockTypeResolutionBatchResult rebuild(List<KisKrxStockTypeResolutionResult> rows) {
        return new KisKrxStockTypeResolutionBatchResult(result.resolutionVersion(), result.matchingResult(), rows);
    }
}
