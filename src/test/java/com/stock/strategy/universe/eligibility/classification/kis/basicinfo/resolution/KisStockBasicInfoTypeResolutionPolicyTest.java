package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution;

import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.KisStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.commonMatch;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.syntheticMaster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockBasicInfoTypeResolutionPolicyTest {
    @ParameterizedTest
    @CsvSource({"005930,KR7005930003,STK,101,COMMON_STOCK", "005930,KR7005930003,STK,201,PREFERRED_STOCK",
            "005930,KR7005930003,STK,202,PREFERRED_STOCK", "0004Y0,KR70004Y0000,KSQ,101,COMMON_STOCK",
            "0004Y0,KR70004Y0000,KSQ,201,PREFERRED_STOCK", "0004Y0,KR70004Y0000,KSQ,202,PREFERRED_STOCK"})
    void supplementsBothMarketsWithoutChangingTheMastersUnverifiedInterpretation(
            String symbol, String standardCode, String market, String kind, StockSecurityType expectedType
    ) {
        var input = matching(symbol, standardCode, market, "ST", kind);
        var result = policy().resolve(input);

        assertThat(result.referenceSecurityType()).isEqualTo(expectedType);
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        assertThat(result.matchingResult()).isSameAs(input);
        assertThat(result.masterClassification().securityType()).isNull();
        assertThat(result.masterClassification().reasonCode()).isEqualTo(KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        assertThat(result.masterClassification().rawRecord()).isSameAs(input.comparedRecord());
        assertThat(result.masterClassification().rawRecord().rawEtp()).isEqualTo(" ");
        assertThat(result.basicInfoClassification().parseResult()).isSameAs(input.apiInput());
        assertThat(result.basicInfoClassification().securityType()).isEqualTo(expectedType);
        assertThat(result.resolutionVersion()).isEqualTo("KIS_STOCK_BASIC_INFO_CURRENT_TYPE_RESOLUTION_V1");
        assertThat(result.masterClassification().classificationVersion()).isEqualTo(KisStockMasterTypeClassificationPolicy.CLASSIFICATION_VERSION);
        assertThat(result.masterClassification().sourceRevision()).isEqualTo(KisStockMasterTypeClassificationPolicy.SOURCE_REVISION);
        assertThat(result.basicInfoClassification().classificationVersion()).isEqualTo(KisStockBasicInfoTypeClassificationPolicy.CLASSIFICATION_VERSION);
        assertThat(result.basicInfoClassification().sourceReference()).isEqualTo(KisStockBasicInfoTypeClassificationPolicy.SOURCE_REFERENCE);
        assertThat(result.basicInfoClassification().sourceSha256()).isEqualTo(KisStockBasicInfoTypeClassificationPolicy.SOURCE_SHA256);
    }

    @Test
    void retainsMasterEtfTypeAndTheUninterpretedApiGroupWithoutApprovingEtfTrading() {
        var input = matching("111111", "KR7111111111", "STK", "EF", "");
        var result = policy().resolve(input);
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.MASTER_TYPE_ONLY);
        assertThat(result.referenceSecurityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.masterClassification().securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.basicInfoClassification().securityType()).isNull();
        assertThat(result.basicInfoClassification().reasonCode()).isEqualTo(KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
        assertThat(result.basicInfoClassification().parseResult()).isSameAs(input.apiInput());
    }

    @Test
    void preservesBothSourceReasonsWhenNeitherTypeIsInterpreted() {
        var result = policy().resolve(matching("005930", "KR7005930003", "STK", "ST", ""));
        assertThat(result.referenceSecurityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.TYPE_UNVERIFIED);
        assertThat(result.masterClassification().reasonCode()).isEqualTo(KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        assertThat(result.basicInfoClassification().reasonCode()).isEqualTo(KisStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED);
        assertThat(result.basicInfoClassification().parseResult().rawRecord().rawStockKind()).isEmpty();
    }

    @Test
    void doesNotPreferEitherSourceWhenInterpretedTypesConflict() {
        var result = policy().resolve(matching("111111", "KR7111111111", "STK", "ST", "101"));
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.TYPE_CONFLICT);
        assertThat(result.referenceSecurityType()).isNull();
        assertThat(result.masterClassification().securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.basicInfoClassification().securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
    }

    @Test
    void handlesAgreementWithASyntheticMasterWithoutExpandingRealClassifierSupport() {
        var input = commonMatch();
        var masterPolicy = mock(KisStockMasterTypeClassificationPolicy.class);
        when(masterPolicy.classify(input.comparedMarket(), input.comparedRecord()))
                .thenReturn(syntheticMaster(input, StockSecurityType.COMMON_STOCK));

        var result = new KisStockBasicInfoTypeResolutionPolicy(masterPolicy, new KisStockBasicInfoTypeClassificationPolicy()).resolve(input);
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.SOURCES_AGREE);
        assertThat(result.referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(new KisStockMasterTypeClassificationPolicy().classify(input.comparedMarket(), input.comparedRecord()).securityType()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"999999|KR7005930003|STK|REQUESTED_SYMBOL_NOT_FOUND",
            "005930|''|STK|API_STANDARD_CODE_BLANK", "005930|KR7111111111|STK|STANDARD_CODE_MISMATCH",
            "005930|KR7005930003|UNKNOWN|API_MARKET_UNVERIFIED", "005930|KR7005930003|KSQ|MARKET_MISMATCH"}, delimiter = '|', emptyValue = "")
    void neverInvokesApiClassificationForAnyFailedComparison(
            String symbol, String standardCode, String market, KisStockBasicInfoMatchReasonCode matchReason
    ) {
        var input = matching(symbol, standardCode, market, "ST", "101");
        var masterPolicy = mock(KisStockMasterTypeClassificationPolicy.class);
        var apiPolicy = mock(KisStockBasicInfoTypeClassificationPolicy.class);
        if (input.comparedRecord() != null) {
            when(masterPolicy.classify(input.comparedMarket(), input.comparedRecord())).thenReturn(syntheticMaster(input, null));
        }
        var result = new KisStockBasicInfoTypeResolutionPolicy(masterPolicy, apiPolicy).resolve(input);

        assertThat(input.reasonCode()).isEqualTo(matchReason);
        assertThat(result.referenceSecurityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED);
        assertThat(result.basicInfoClassification()).isNull();
        assertThat(result.matchingResult()).isSameAs(input);
        verifyNoInteractions(apiPolicy);
        if (input.comparedRecord() == null) {
            assertThat(result.masterClassification()).isNull();
            verifyNoInteractions(masterPolicy);
        } else {
            assertThat(result.masterClassification().rawRecord()).isSameAs(input.comparedRecord());
            verify(masterPolicy).classify(input.comparedMarket(), input.comparedRecord());
            verifyNoMoreInteractions(masterPolicy);
        }
    }

    @Test
    void doesNotFallbackToAKnownMasterTypeWhenComparisonFails() {
        var input = matching("111111", "KR7005930003", "STK", "ST", "101");
        var apiPolicy = mock(KisStockBasicInfoTypeClassificationPolicy.class);
        var result = new KisStockBasicInfoTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), apiPolicy).resolve(input);
        assertThat(result.masterClassification().securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED);
        assertThat(result.referenceSecurityType()).isNull();
        verifyNoInteractions(apiPolicy);
    }

    @Test
    void callsBothPoliciesOnlyForTheSingleComparedInput() {
        var input = commonMatch();
        var masterPolicy = mock(KisStockMasterTypeClassificationPolicy.class);
        var apiPolicy = mock(KisStockBasicInfoTypeClassificationPolicy.class);
        when(masterPolicy.classify(input.comparedMarket(), input.comparedRecord())).thenReturn(syntheticMaster(input, null));
        when(apiPolicy.classify(input.apiInput())).thenReturn(new KisStockBasicInfoTypeClassificationPolicy().classify(input.apiInput()));
        new KisStockBasicInfoTypeResolutionPolicy(masterPolicy, apiPolicy).resolve(input);
        verify(masterPolicy).classify(input.comparedMarket(), input.comparedRecord());
        verify(apiPolicy).classify(input.apiInput());
        verifyNoMoreInteractions(masterPolicy, apiPolicy);
    }

    @Test
    void preservesSpacManagementSuspensionDatesAndRawProductNumberWithoutCreatingEligibility() {
        var input = commonMatch();
        var result = policy().resolve(input);
        assertThat(result.referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.masterClassification().rawRecord().rawSpac()).isEqualTo("Y");
        assertThat(result.masterClassification().rawRecord().rawSuspension()).isEqualTo("Y");
        assertThat(result.masterClassification().rawRecord().rawLiquidation()).isEqualTo("Y");
        assertThat(result.masterClassification().rawRecord().rawManagement()).isEqualTo("Y");
        assertThat(result.masterClassification().rawRecord().rawInvestmentCaution()).isNull();
        var raw = result.basicInfoClassification().parseResult().rawRecord();
        assertThat(raw.productNumber()).isEqualTo("00000A005930");
        assertThat(raw.rawManagement()).isEqualTo("Y");
        assertThat(raw.rawKospiListingDate()).isEqualTo("UNKNOWN_KOSPI_DATE");
        assertThat(raw.rawDelistingDate()).isEmpty();
        assertThat(raw.rawNxtSuspension()).isEqualTo("N");
        assertThat(raw.rawCompetitiveTradingPermission()).isEqualTo("N");
        assertThat(result.matchingResult().masterBatch().collection()).isSameAs(input.masterBatch().collection());
        assertThat(result.basicInfoClassification().parseResult().inputSha256()).isEqualTo(input.apiInput().inputSha256());
    }

    @Test
    void repeatedCallsDoNotRetainThePreviousRequestsTypeOrReason() {
        var policy = policy();
        var input = commonMatch();
        var first = policy.resolve(input);
        policy.resolve(matching("111111", "KR7111111111", "STK", "EF", ""));
        policy.resolve(matching("999999", "KR7005930003", "STK", "ST", "101"));
        assertThat(policy.resolve(input)).isEqualTo(first);
    }

    @Test
    void rejectsNullDependenciesInputAndClassifierResults() {
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionPolicy(null, new KisStockBasicInfoTypeClassificationPolicy()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy().resolve(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionPolicy(mock(KisStockMasterTypeClassificationPolicy.class),
                new KisStockBasicInfoTypeClassificationPolicy()).resolve(commonMatch())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(),
                mock(KisStockBasicInfoTypeClassificationPolicy.class)).resolve(commonMatch())).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MARKET", "RECORD"})
    void rejectsMasterClassificationForAnotherMarketOrCompleteRow(String field) {
        var input = commonMatch();
        var masterPolicy = mock(KisStockMasterTypeClassificationPolicy.class);
        var other = matching("0004Y0", "KR70004Y0000", "KSQ", "ST", "101");
        var changed = new KisStockMasterTypeClassificationResult(field.equals("MARKET") ? other.comparedMarket() : input.comparedMarket(),
                field.equals("RECORD") ? other.comparedRecord() : input.comparedRecord(), null,
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED, "SYNTHETIC_MASTER_TYPE_V1", "0".repeat(40));
        when(masterPolicy.classify(input.comparedMarket(), input.comparedRecord())).thenReturn(changed);
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionPolicy(masterPolicy, new KisStockBasicInfoTypeClassificationPolicy()).resolve(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsApiClassificationEvenWhenOnlyTheParseEnvelopeChanges() {
        var input = commonMatch();
        var parsed = input.apiInput();
        var changed = new KisStockBasicInfoParseResult(
                parsed.inputSha256(), parsed.parserVersion(), parsed.responseCode(), parsed.messageCode(), "ALTERED MESSAGE", parsed.rawRecord());
        var real = new KisStockBasicInfoTypeClassificationPolicy().classify(parsed);
        var apiPolicy = mock(KisStockBasicInfoTypeClassificationPolicy.class);
        when(apiPolicy.classify(parsed)).thenReturn(new KisStockBasicInfoTypeClassificationResult(changed, real.securityType(),
                real.reasonCode(), real.classificationVersion(), real.sourceReference(), real.sourceSha256()));
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), apiPolicy).resolve(input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
