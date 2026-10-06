package com.stock.strategy.universe.eligibility.classification.kiskrx;

import com.stock.market.stock.master.matching.kiskrx.KisKrxStockIdentityMatchingPolicy;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchReasonCode;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.krx.KrxStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.baseBatch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.batch;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.inputs;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.parsed;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.stock;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.syntheticKis;
import static com.stock.strategy.universe.eligibility.classification.kiskrx.support.KisKrxStockTypeResolutionFixture.syntheticKrx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisKrxStockTypeResolutionPolicyTest {
    @Test
    void supplementsBothMarketsWithoutOverwritingSourceInterpretationsOrDiscardingUnmatchedRows() {
        var batch = baseBatch();
        var inputs = inputs(stock(KOSPI, "005930", "KR7005930003"));
        inputs.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")
                .put("KIND_STKCERT_TP_NM", "\uc2e0\ud615\uc6b0\uc120\uc8fc")));
        var matching = new KisKrxStockIdentityMatchingPolicy().match(batch, inputs);

        var result = policy().resolve(matching);

        assertThat(result.resolutionVersion()).isEqualTo("KIS_KRX_CURRENT_TYPE_RESOLUTION_V1");
        assertThat(result.matchingResult()).isSameAs(matching);
        assertThat(result.rowResults()).hasSize(3).extracting(row -> row.kisClassification().rawRecord().symbol())
                .containsExactly("005930", "111111", "0001A0");
        assertThat(result.rowResults()).extracting(row -> row.referenceSecurityType())
                .containsExactly(StockSecurityType.COMMON_STOCK, StockSecurityType.ETF, StockSecurityType.PREFERRED_STOCK);
        var common = result.rowResults().getFirst();
        assertThat(common.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.KRX_TYPE_SUPPLEMENTED);
        assertThat(common.kisClassification().securityType()).isNull();
        assertThat(common.kisClassification().reasonCode()).isEqualTo(KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        assertThat(common.kisClassification().rawRecord().rawEtp()).isEqualTo(" ");
        assertThat(common.kisClassification().rawRecord()).isSameAs(batch.marketResults().getFirst().records().getFirst());
        assertThat(common.identityMatch()).isSameAs(matching.rowResults().getFirst());
        assertThat(common.krxClassification().rawRecord()).isSameAs(inputs.get(KOSPI).records().getFirst());
        assertThat(common.kisClassification().sourceRevision()).isEqualTo(KisStockMasterTypeClassificationPolicy.SOURCE_REVISION);
        assertThat(common.krxClassification().sourceSha256()).isEqualTo(KrxStockBasicInfoTypeClassificationPolicy.SOURCE_SHA256);
        assertThat(result.rowResults().get(1).reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY);
        assertThat(result.rowResults().get(1).identityMatch()).isNull();
        assertThat(result.rowResults().get(1).krxClassification()).isNull();
        assertThat(result.matchingResult().kisBatch().collection().finishedAt()).isEqualTo(Instant.parse("2026-10-05T09:32:36Z"));
        assertThat(result.matchingResult().krxInputs().get(KOSPI).inputSha256()).isEqualTo(inputs.get(KOSPI).inputSha256());
        assertThat(policy().resolve(matching)).isEqualTo(result);
    }

    @ParameterizedTest
    @CsvSource({"\ubcf4\ud1b5\uc8fc,COMMON_STOCK", "\uad6c\ud615\uc6b0\uc120\uc8fc,PREFERRED_STOCK",
            "\uc2e0\ud615\uc6b0\uc120\uc8fc,PREFERRED_STOCK"})
    void reusesKrxSupportedKindsRatherThanInventingKisMappings(String kind, StockSecurityType type) {
        var result = policy().resolve(matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003")
                .put("KIND_STKCERT_TP_NM", kind))).rowResults().getFirst();
        assertThat(result.referenceSecurityType()).isEqualTo(type);
        assertThat(result.kisClassification().securityType()).isNull();
        assertThat(result.krxClassification().classificationVersion()).isEqualTo(KrxStockBasicInfoTypeClassificationPolicy.CLASSIFICATION_VERSION);
    }

    @Test
    void retainsBothUnverifiedInterpretationsAndTheirOriginalReasons() {
        var result = policy().resolve(matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003")
                .put("KIND_STKCERT_TP_NM", "\uc885\ub958\uc8fc\uad8c"))).rowResults().getFirst();
        assertThat(result.referenceSecurityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.TYPE_UNVERIFIED);
        assertThat(result.identityMatch()).isNotNull();
        assertThat(result.kisClassification().securityType()).isNull();
        assertThat(result.krxClassification().securityType()).isNull();
        assertThat(result.krxClassification().reasonCode().name()).isEqualTo("TYPE_COMBINATION_UNSUPPORTED");
    }

    @Test
    void retainsKisTypeAndUnverifiedKrxEvidenceForAnExactMatch() {
        var result = policy().resolve(matching(baseBatch(), stock(KOSPI, "111111", "KR7111111111")
                .put("SECUGRP_NM", "UNKNOWN_GROUP"))).rowResults().get(1);
        assertThat(result.referenceSecurityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.KIS_TYPE_ONLY);
        assertThat(result.identityMatch()).isNotNull();
        assertThat(result.krxClassification().securityType()).isNull();
        assertThat(result.krxClassification().rawRecord().rawSecurityGroup()).isEqualTo("UNKNOWN_GROUP");
    }

    @Test
    void leavesReferenceTypeNullForConflictingSourceTypes() {
        var result = policy().resolve(matching(baseBatch(), stock(KOSPI, "111111", "KR7111111111"))).rowResults().get(1);
        assertThat(result.referenceSecurityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.TYPE_CONFLICT);
        assertThat(result.kisClassification().securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.krxClassification().securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
    }

    @Test
    void handlesAgreementUsingSyntheticInterpretationsWithoutExpandingRealClassifierSupport() {
        var input = matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"));
        var kis = spy(new KisStockMasterTypeClassificationPolicy());
        var raw = input.kisBatch().marketResults().getFirst().records().getFirst();
        doReturn(syntheticKis(KOSPI, raw, StockSecurityType.COMMON_STOCK)).when(kis).classify(KOSPI, raw);

        var result = new KisKrxStockTypeResolutionPolicy(kis, new KrxStockBasicInfoTypeClassificationPolicy()).resolve(input);

        assertThat(result.rowResults().getFirst().reasonCode()).isEqualTo(KisKrxStockTypeResolutionReasonCode.SOURCES_AGREE);
        assertThat(result.rowResults().getFirst().referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(new KisStockMasterTypeClassificationPolicy().classify(KOSPI, raw).securityType()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "ISU_CD|''|KRX_IDENTIFIER_BLANK", "ISU_CD|OTHER_STANDARD|SYMBOL_ONLY_MATCH",
            "ISU_SRT_CD|OTHER_SYMBOL|SYMBOL_MISMATCH", "MKT_TP_NM|KOSDAQ|REQUEST_MARKET_MISMATCH"
    }, delimiter = '|', emptyValue = "")
    void neverUsesKrxTypeWhenIdentityMatchingFails(String field, String value, KisKrxStockIdentityMatchReasonCode reason) {
        var input = matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003").put(field, value));
        var krx = spy(new KrxStockBasicInfoTypeClassificationPolicy());

        var result = new KisKrxStockTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), krx).resolve(input);

        assertThat(input.rowResults().getFirst().reasonCode()).isEqualTo(reason);
        assertThat(result.rowResults().getFirst().referenceSecurityType()).isNull();
        assertThat(result.rowResults().getFirst().identityMatch()).isNull();
        assertThat(result.rowResults().getFirst().krxClassification()).isNull();
        assertThat(result.matchingResult()).isSameAs(input);
        verifyNoInteractions(krx);
    }

    @Test
    void retainsAllDuplicateUnknownAndMarketConflictingKrxDiagnosticsWithoutSupplementingAnyKisRow() {
        var input = matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"),
                stock(KOSPI, "005930", "KR7005930003"), stock(KOSPI, "999999", "KR7999999999"),
                stock(KOSPI, "0001A0", "KR70001A0001"));
        var krx = mock(KrxStockBasicInfoTypeClassificationPolicy.class);

        var result = new KisKrxStockTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), krx).resolve(input);

        assertThat(result.rowResults()).hasSize(3).allSatisfy(row -> {
            assertThat(row.identityMatch()).isNull();
            assertThat(row.krxClassification()).isNull();
        });
        assertThat(result.matchingResult().rowResults()).hasSize(4).extracting(row -> row.reasonCode())
                .containsExactly(KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED,
                        KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED,
                        KisKrxStockIdentityMatchReasonCode.STANDARD_CODE_NOT_FOUND,
                        KisKrxStockIdentityMatchReasonCode.MARKET_MISMATCH);
        assertThat(result.rowResults().get(1).referenceSecurityType()).isEqualTo(StockSecurityType.ETF);
        verifyNoInteractions(krx);
    }

    @Test
    void classifiesEveryKisRowEvenWhenKrxResponsesAreEmpty() {
        var input = matching(baseBatch());
        var kis = spy(new KisStockMasterTypeClassificationPolicy());
        var krx = mock(KrxStockBasicInfoTypeClassificationPolicy.class);

        var result = new KisKrxStockTypeResolutionPolicy(kis, krx).resolve(input);

        assertThat(result.rowResults()).hasSize(3);
        verify(kis, times(3)).classify(any(), any());
        for (var market : input.kisBatch().marketResults()) {
            for (var raw : market.records()) {
                verify(kis).classify(market.market(), raw);
            }
        }
        verifyNoInteractions(krx);
    }

    @Test
    void preservesCanonicalKisOrderRatherThanKrxOrCallerMarketOrder() {
        var batch = baseBatch();
        var reversed = new StockMasterBatchParseResult(batch.collection(), batch.marketResults().reversed());
        var input = matching(reversed, stock(KOSPI, "111111", "KR7111111111"), stock(KOSPI, "005930", "KR7005930003"));
        var result = policy().resolve(input);
        assertThat(result.rowResults()).extracting(row -> row.kisClassification().rawRecord().symbol())
                .containsExactly("005930", "111111", "0001A0");
        assertThat(result.rowResults().getFirst().identityMatch()).isSameAs(input.rowResults().getLast());
    }

    @Test
    void doesNotInferListingOrTradingPermissionFromAReferenceType() {
        byte[] bytes = KisStockMasterParsingFixture.row(KOSPI);
        KisStockMasterParsingFixture.put(bytes, 121, "Y");
        KisStockMasterParsingFixture.put(bytes, 122, "Y");
        KisStockMasterParsingFixture.put(bytes, 166, "99999999");
        var input = matching(batch(bytes), stock(KOSPI, "005930", "KR7005930003")
                .put("SECT_TP_NM", "UNKNOWN_RESTRICTION").put("LIST_DD", "NOT_A_DATE"));

        var result = policy().resolve(input).rowResults().getFirst();

        assertThat(result.referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.kisClassification().rawRecord().rawSuspension()).isEqualTo("Y");
        assertThat(result.kisClassification().rawRecord().rawLiquidation()).isEqualTo("Y");
        assertThat(result.kisClassification().rawRecord().rawListingDate()).isEqualTo("99999999");
        assertThat(result.krxClassification().rawRecord().rawSection()).isEqualTo("UNKNOWN_RESTRICTION");
        assertThat(result.krxClassification().rawRecord().rawListingDate()).isEqualTo("NOT_A_DATE");
    }

    @Test
    void rejectsNullDependenciesInputOrClassifierResults() {
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionPolicy(null, new KrxStockBasicInfoTypeClassificationPolicy()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy().resolve(null)).isInstanceOf(NullPointerException.class);
        var input = matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"));
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionPolicy(mock(KisStockMasterTypeClassificationPolicy.class),
                new KrxStockBasicInfoTypeClassificationPolicy()).resolve(input)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(),
                mock(KrxStockBasicInfoTypeClassificationPolicy.class)).resolve(input)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MARKET", "RAW"})
    void rejectsClassifierResultsThatChangeTheKisInputEvenForUnmatchedRows(String field) {
        var input = matching(baseBatch());
        var kis = mock(KisStockMasterTypeClassificationPolicy.class);
        when(kis.classify(any(), any())).thenAnswer(call -> syntheticKis(call.getArgument(0), call.getArgument(1), null));
        var raw = input.kisBatch().marketResults().getFirst().records().getFirst();
        var changedRaw = baseBatch().marketResults().getFirst().records().get(1);
        when(kis.classify(KOSPI, raw)).thenReturn(syntheticKis(field.equals("MARKET") ? KOSDAQ : KOSPI,
                field.equals("RAW") ? changedRaw : raw, null));
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionPolicy(kis, new KrxStockBasicInfoTypeClassificationPolicy()).resolve(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsKrxClassificationForADifferentRawRow() {
        var input = matching(baseBatch(), stock(KOSPI, "005930", "KR7005930003"));
        var krx = mock(KrxStockBasicInfoTypeClassificationPolicy.class);
        var changed = parsed(stock(KOSPI, "005930", "KR7005930003").put("ISU_NM", "ALTERED_NAME")).records().getFirst();
        when(krx.classify(any())).thenReturn(syntheticKrx(changed, StockSecurityType.COMMON_STOCK));
        assertThatThrownBy(() -> new KisKrxStockTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), krx).resolve(input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
