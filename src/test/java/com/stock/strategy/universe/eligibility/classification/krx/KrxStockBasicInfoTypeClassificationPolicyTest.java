package com.stock.strategy.universe.eligibility.classification.krx;

import com.stock.market.stock.master.provider.krx.parsing.KrxStockBasicInfoParser;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.strategy.universe.eligibility.classification.krx.result.KrxStockBasicInfoTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.row;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxStockBasicInfoTypeClassificationPolicyTest {
    private static final String SHARE = "\uc8fc\uad8c";
    private static final String COMMON = "\ubcf4\ud1b5\uc8fc";
    private static final String OLD_PREFERRED = "\uad6c\ud615\uc6b0\uc120\uc8fc";
    private static final String SPECIAL = "\uc885\ub958\uc8fc\uad8c";
    private final KrxStockBasicInfoTypeClassificationPolicy policy = new KrxStockBasicInfoTypeClassificationPolicy();
    private final KrxStockBasicInfoParser parser = new KrxStockBasicInfoParser();

    @ParameterizedTest
    @CsvSource({
            "KOSPI,\ubcf4\ud1b5\uc8fc,COMMON_STOCK", "KOSDAQ,\ubcf4\ud1b5\uc8fc,COMMON_STOCK",
            "KOSPI,\uad6c\ud615\uc6b0\uc120\uc8fc,PREFERRED_STOCK", "KOSDAQ,\uad6c\ud615\uc6b0\uc120\uc8fc,PREFERRED_STOCK",
            "KOSPI,\uc2e0\ud615\uc6b0\uc120\uc8fc,PREFERRED_STOCK", "KOSDAQ,\uc2e0\ud615\uc6b0\uc120\uc8fc,PREFERRED_STOCK"
    })
    void interpretsOnlySupportedShareKindsWithFixedEvidenceMetadata(String market, String kind, StockSecurityType type) {
        var raw = record(market, SHARE, kind);

        var result = policy.classify(raw);

        assertThat(result.securityType()).isEqualTo(type);
        assertThat(result.reasonCode()).isEqualTo(KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.classificationVersion()).isEqualTo("KRX_STOCK_BASIC_INFO_CURRENT_TYPE_V1");
        assertThat(result.sourceReference()).isEqualTo("build/stock-eligibility-krx-source-validation-03/final-verification.json");
        assertThat(result.sourceSha256()).isEqualTo("df0658234036f57e88f297ef3cdf13eca804ea07ff0b944474ba3151a6448711");
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "kospi", "kosdaq", " KOSPI", "KOSDAQ ", "KONEX", "OTHER", "KR"})
    void keepsUnknownOrNonexactMarketsUnverified(String market) {
        assertReason(record(market, SHARE, COMMON), KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "UNKNOWN", "ETF", "ETN", "\uc8fc\uad8c ", " \uc8fc\uad8c"})
    void doesNotNormalizeOrInferUnobservedSecurityGroups(String group) {
        assertReason(record("KOSPI", group, COMMON),
                KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\ubd80\ub3d9\uc0b0\ud22c\uc790\ud68c\uc0ac", "\uc0ac\ud68c\uac04\uc811\uc790\ubcf8\ud22c\uc735\uc790\ud68c\uc0ac",
            "\uc678\uad6d\uc8fc\uad8c", "\uc8fc\uc2dd\uc608\ud0c1\uc99d\uad8c", "\ud22c\uc790\ud68c\uc0ac"
    })
    void doesNotPromoteObservedOtherGroupsEvenIfStockKindIsCommon(String group) {
        for (var market : new String[]{"KOSPI", "KOSDAQ"}) {
            assertReason(record(market, group, COMMON), KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
        }
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "UNKNOWN", "COMMON_STOCK", "\uc6b0\uc120\uc8fc", "\ubcf4\ud1b5\uc8fc ", " \ubcf4\ud1b5\uc8fc"})
    void keepsUnobservedKindsUnverifiedInsteadOfUsingANameOrDefaultType(String kind) {
        assertReason(record("KOSDAQ", SHARE, kind), KrxStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KOSPI", "KOSDAQ"})
    void keepsObservedSpecialShareKindUnsupported(String market) {
        assertReason(record(market, SHARE, SPECIAL), KrxStockBasicInfoTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    @Test
    void reportsFirstReasonWithoutDiscardingOtherUnverifiedFields() {
        assertReason(record("UNKNOWN", "UNKNOWN", "UNKNOWN"), KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED);
        assertReason(record("KOSPI", "UNKNOWN", "UNKNOWN"), KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
        assertReason(record("KOSDAQ", "\uc678\uad6d\uc8fc\uad8c", "UNKNOWN"),
                KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001A0", "005930", "03481K", " 005930 "})
    void preservesIdentifiersAndAllOtherRawFieldsWithoutNumericConversionOrTrimming(String symbol) {
        var fields = values();
        fields[0] = " KR70001A0001 ";
        fields[1] = symbol;
        fields[2] = " COMMON_STOCK PREFERRED_STOCK ETF ";
        fields[3] = "  ABBREVIATION  ";
        fields[4] = " ENGLISH NAME ";
        fields[5] = "NOTADATE";
        fields[6] = "KOSPI";
        fields[7] = SHARE;
        fields[8] = "";
        fields[9] = COMMON;
        fields[10] = "INVALID_NUMBER";
        fields[11] = "000000";
        var raw = rawRecord(17, fields);

        var result = policy.classify(raw);

        assertThat(result.securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().symbol()).isEqualTo(symbol);
        assertThat(result.rawRecord().rowNumber()).isEqualTo(17);
        assertThat(result.rawRecord().rawSection()).isEmpty();
        assertThat(result.rawRecord().rawListingDate()).isEqualTo("NOTADATE");
        assertThat(result.rawRecord().rawParValue()).isEqualTo("INVALID_NUMBER");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "SPAC(\uc18c\uc18d\ubd80\uc5c6\uc74c)", "\uad00\ub9ac\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)",
            "\ud22c\uc790\uc8fc\uc758\ud658\uae30\uc885\ubaa9(\uc18c\uc18d\ubd80\uc5c6\uc74c)", "UNOBSERVED_SECTION"
    })
    void preservesSectionRestrictionsWithoutConfusingShareTypeWithEligibility(String section) {
        var fields = values();
        fields[6] = "KOSDAQ";
        fields[7] = SHARE;
        fields[8] = section;
        fields[9] = COMMON;
        var raw = rawRecord(1, fields);

        var result = policy.classify(raw);

        assertThat(result.securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().rawSection()).isEqualTo(section);
    }

    @Test
    void doesNotInferATypeFromIdentifiersOrNamesWhenTypeFieldsAreMissing() {
        var fields = values();
        fields[2] = "COMMON_STOCK PREFERRED_STOCK";
        fields[6] = "KOSPI";
        fields[7] = "";
        fields[9] = COMMON;
        assertReason(rawRecord(1, fields), KrxStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
    }

    @Test
    void doesNotCertifyMissingIdentifiersWhenInterpretingShareKind() {
        var fields = values();
        fields[0] = "";
        fields[1] = " ";
        fields[6] = "KOSPI";
        fields[7] = SHARE;
        fields[9] = COMMON;
        var raw = rawRecord(1, fields);

        var result = policy.classify(raw);

        assertThat(result.securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().standardCode()).isEmpty();
        assertThat(result.rawRecord().symbol()).isEqualTo(" ");
    }

    @Test
    void classifiesParsedRowsWithoutRewritingDuplicateIdentifiersOrTheParseResult() {
        var first = row().put("MKT_TP_NM", "KOSPI").put("SECUGRP_NM", SHARE).put("KIND_STKCERT_TP_NM", COMMON);
        var second = first.deepCopy().put("MKT_TP_NM", "KOSDAQ").put("KIND_STKCERT_TP_NM", SPECIAL);
        byte[] bytes = content(first, second);
        var parsed = parser.parse(bytes);

        var results = parsed.records().stream().map(policy::classify).toList();

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(results.getLast().securityType()).isNull();
        assertThat(results.getFirst().rawRecord()).isSameAs(parsed.records().getFirst());
        assertThat(results.getLast().rawRecord()).isSameAs(parsed.records().getLast());
        assertThat(results.getFirst().rawRecord().symbol()).isEqualTo(results.getLast().rawRecord().symbol());
        assertThat(parser.parse(bytes)).isEqualTo(parsed);
    }

    @Test
    void doesNotRetainClassificationAcrossCallsOrInstances() {
        var common = record("KOSPI", SHARE, COMMON);
        var preferred = record("KOSDAQ", SHARE, OLD_PREFERRED);
        var first = policy.classify(common);

        assertThat(policy.classify(preferred).securityType()).isEqualTo(StockSecurityType.PREFERRED_STOCK);
        assertThat(policy.classify(record("KOSPI", SHARE, SPECIAL)).securityType()).isNull();
        assertThat(policy.classify(common)).isEqualTo(first);
        assertThat(new KrxStockBasicInfoTypeClassificationPolicy().classify(common)).isEqualTo(first);
    }

    @Test
    void rejectsNullRecord() {
        assertThatThrownBy(() -> policy.classify(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("rawRecord");
    }

    private void assertReason(KrxStockBasicInfoRawRecord raw, KrxStockBasicInfoTypeClassificationReasonCode reason) {
        var result = policy.classify(raw);
        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.classificationVersion()).isEqualTo(KrxStockBasicInfoTypeClassificationPolicy.CLASSIFICATION_VERSION);
        assertThat(result.sourceReference()).isEqualTo(KrxStockBasicInfoTypeClassificationPolicy.SOURCE_REFERENCE);
        assertThat(result.sourceSha256()).isEqualTo(KrxStockBasicInfoTypeClassificationPolicy.SOURCE_SHA256);
    }

    private static KrxStockBasicInfoRawRecord record(String market, String group, String kind) {
        var fields = values();
        fields[6] = market;
        fields[7] = group;
        fields[9] = kind;
        return rawRecord(1, fields);
    }
}
