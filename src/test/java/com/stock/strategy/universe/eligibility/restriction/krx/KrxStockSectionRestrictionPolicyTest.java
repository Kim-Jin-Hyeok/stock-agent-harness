package com.stock.strategy.universe.eligibility.restriction.krx;

import com.stock.market.stock.master.matching.kiskrx.KisKrxStockIdentityMatchingPolicy;
import com.stock.market.stock.master.matching.kiskrx.result.KisKrxStockIdentityMatchReasonCode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.classification.kiskrx.result.KisKrxStockTypeResolutionResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.restriction.krx.result.KrxStockSectionRestrictionReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class KrxStockSectionRestrictionPolicyTest {
    private final KrxStockSectionRestrictionPolicy restrictionPolicy = new KrxStockSectionRestrictionPolicy();

    @ParameterizedTest
    @CsvSource({
            "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c),SPAC_OBSERVED",
            "\uad00\ub9ac\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c),MANAGEMENT_DESIGNATION_OBSERVED",
            "\ud22c\uc790\uc8fc\uc758\ud658\uae30\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c),INVESTMENT_CAUTION_OBSERVED"
    })
    void observesExactTargetSectionsWithoutChangingCommonStockType(String section, KrxStockSectionRestrictionReasonCode reason) {
        var input = resolution(KOSDAQ, section);

        var result = restrictionPolicy.evaluate(input);

        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.typeResolution().identityMatch().krxRecord().rawSection()).isEqualTo(section);
        assertThat(result.restrictionVersion()).isEqualTo("KRX_STOCK_SECTION_CURRENT_RESTRICTION_V1");
        assertThat(result.sourceReference()).isEqualTo(KrxStockSectionRestrictionPolicy.SOURCE_REFERENCE);
        assertThat(result.sourceSha256()).isEqualTo(KrxStockSectionRestrictionPolicy.SOURCE_SHA256);
        assertThat(restrictionPolicy.evaluate(input)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uc6b0\ub7c9\uae30\uc5c5\ubd80", "\uc911\uacac\uae30\uc5c5\ubd80",
            "\uae30\uc220\uc131\uc7a5\uae30\uc5c5\ubd80", "\ubca4\ucc98\uae30\uc5c5\ubd80"})
    void reportsOnlyAbsenceOfTheTargetMarkersForReviewedKosdaqSections(String section) {
        var result = restrictionPolicy.evaluate(resolution(KOSDAQ, section));
        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.NO_TARGET_RESTRICTION_OBSERVED);
        assertThat(result.typeResolution().identityMatch().requestMarket()).isEqualTo(KOSDAQ);
        assertThat(result.typeResolution().identityMatch().krxRecord().rawSection()).isEqualTo(section);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "UNKNOWN_SECTION", "\uc678\uad6d\uae30\uc5c5(\uc18c\uc18d\ubd80\uc5c6\uc74c)",
            "SPAC", "spac(\uc18c\uc18d\ubd80\uc5c6\uc74c)", " SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)",
            "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c) ", "\uad00\ub9ac\uc885\ubaa9", "\uc6b0\ub7c9\uae30\uc5c5\ubd80 ", "\ubca4\ucc98\uae30\uc5c5\ubd80\n"})
    void defersBlankUnreviewedOrModifiedSectionsWithoutNormalization(String section) {
        var result = restrictionPolicy.evaluate(resolution(KOSDAQ, section));
        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED);
        assertThat(result.typeResolution().identityMatch().krxRecord().rawSection()).isEqualTo(section);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)", "\uad00\ub9ac\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)",
            "\ud22c\uc790\uc8fc\uc758\ud658\uae30\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)", "\uc6b0\ub7c9\uae30\uc5c5\ubd80",
            "\uc911\uacac\uae30\uc5c5\ubd80", "\uae30\uc220\uc131\uc7a5\uae30\uc5c5\ubd80", "\ubca4\ucc98\uae30\uc5c5\ubd80"})
    void neverExpandsTheKosdaqSectionInterpretationToKospi(String section) {
        var result = restrictionPolicy.evaluate(resolution(KOSPI, section));
        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.SECTION_UNVERIFIED);
        assertThat(result.typeResolution().identityMatch().krxRecord().rawSection()).isEqualTo(section);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "ISU_CD|''|KRX_IDENTIFIER_BLANK", "ISU_CD|OTHER_STANDARD|SYMBOL_ONLY_MATCH",
            "ISU_SRT_CD|OTHER_SYMBOL|SYMBOL_MISMATCH", "MKT_TP_NM|KOSPI|REQUEST_MARKET_MISMATCH"
    }, delimiter = '|', emptyValue = "")
    void refusesSectionEvidenceFromFailedIdentityMatches(String field, String value, KisKrxStockIdentityMatchReasonCode reason) {
        var inputs = inputs();
        inputs.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")
                .put("SECT_TP_NM", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)").put(field, value)));
        var matching = new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), inputs);
        var input = policy().resolve(matching).rowResults().getLast();

        var result = restrictionPolicy.evaluate(input);

        assertThat(matching.rowResults().getFirst().reasonCode()).isEqualTo(reason);
        assertThat(matching.rowResults().getFirst().krxRecord().rawSection()).isEqualTo("SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)");
        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED);
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().identityMatch()).isNull();
        assertThat(result.typeResolution().krxClassification()).isNull();
    }

    @Test
    void keepsUnmatchedKisRowsIncludingKnownEtfsUnverified() {
        var batch = policy().resolve(matching(baseBatch()));

        var results = batch.rowResults().stream().map(restrictionPolicy::evaluate).toList();

        assertThat(results).hasSize(3).allSatisfy(result -> {
            assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED);
            assertThat(result.typeResolution().identityMatch()).isNull();
        });
        assertThat(results).extracting(result -> result.typeResolution().kisClassification().rawRecord().symbol())
                .containsExactly("005930", "111111", "0001A0");
        assertThat(results.get(1).typeResolution().referenceSecurityType()).isEqualTo(StockSecurityType.ETF);
    }

    @Test
    void neverUsesDuplicatedKrxRowsEvenWithRecognizedSectionMarkers() {
        var row = stock(KOSDAQ, "0001A0", "KR70001A0001").put("SECT_TP_NM", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)");
        var inputs = inputs();
        inputs.put(KOSDAQ, parsed(row, row.deepCopy()));
        var matching = new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), inputs);

        var result = restrictionPolicy.evaluate(policy().resolve(matching).rowResults().getLast());

        assertThat(matching.rowResults()).hasSize(2).allSatisfy(match ->
                assertThat(match.reasonCode()).isEqualTo(KisKrxStockIdentityMatchReasonCode.KRX_IDENTIFIER_DUPLICATED));
        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.IDENTITY_MATCH_UNVERIFIED);
    }

    @Test
    void doesNotRequireAConfirmedSecurityTypeToPreserveSectionEvidence() {
        var inputs = inputs();
        inputs.put(KOSDAQ, parsed(stock(KOSDAQ, "0001A0", "KR70001A0001")
                .put("SECT_TP_NM", "\uad00\ub9ac\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)")
                .put("KIND_STKCERT_TP_NM", "UNKNOWN_KIND").put("LIST_DD", "NOT_A_DATE")));
        var input = policy().resolve(new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), inputs)).rowResults().getLast();

        var result = restrictionPolicy.evaluate(input);

        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.MANAGEMENT_DESIGNATION_OBSERVED);
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().referenceSecurityType()).isNull();
        assertThat(result.typeResolution().identityMatch().krxRecord().rawListingDate()).isEqualTo("NOT_A_DATE");
        assertThat(result.typeResolution().kisClassification().securityType()).isNull();
    }

    @Test
    void ignoresStockNamesRatherThanInferringAdditionalRestrictionRules() {
        var input = resolution(KOSDAQ, "\uc6b0\ub7c9\uae30\uc5c5\ubd80");
        var source = input.kisClassification().rawRecord();
        var inputs = inputs();
        inputs.put(KOSDAQ, parsed(stock(KOSDAQ, source.symbol(), source.standardCode())
                .put("ISU_NM", "SPAC MANAGEMENT SUSPENDED")
                .put("SECT_TP_NM", "\uc6b0\ub7c9\uae30\uc5c5\ubd80")));
        var named = policy().resolve(new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), inputs)).rowResults().getLast();

        var result = restrictionPolicy.evaluate(named);

        assertThat(result.reasonCode()).isEqualTo(KrxStockSectionRestrictionReasonCode.NO_TARGET_RESTRICTION_OBSERVED);
        assertThat(result.typeResolution().identityMatch().krxRecord().name()).isEqualTo("SPAC MANAGEMENT SUSPENDED");
        assertThat(result.typeResolution()).isSameAs(named);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> restrictionPolicy.evaluate(null)).isInstanceOf(NullPointerException.class);
    }

    private static KisKrxStockTypeResolutionResult resolution(KisStockMasterMarket market, String section) {
        var inputs = inputs();
        inputs.put(market, parsed(stock(market, market == KOSPI ? "005930" : "0001A0",
                market == KOSPI ? "KR7005930003" : "KR70001A0001").put("SECT_TP_NM", section)));
        var result = policy().resolve(new KisKrxStockIdentityMatchingPolicy().match(baseBatch(), inputs));
        return market == KOSPI ? result.rowResults().getFirst() : result.rowResults().getLast();
    }
}
