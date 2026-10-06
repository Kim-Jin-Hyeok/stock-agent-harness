package com.stock.strategy.universe.eligibility.classification.krx.result;

import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.strategy.universe.eligibility.classification.krx.KrxStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.rawRecord;
import static com.stock.market.stock.master.provider.krx.parsing.support.KrxStockBasicInfoParsingFixture.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxStockBasicInfoTypeClassificationResultTest {
    private final KrxStockBasicInfoRawRecord raw = supportedRecord();
    private final String version = KrxStockBasicInfoTypeClassificationPolicy.CLASSIFICATION_VERSION;
    private final String reference = KrxStockBasicInfoTypeClassificationPolicy.SOURCE_REFERENCE;
    private final String sha256 = KrxStockBasicInfoTypeClassificationPolicy.SOURCE_SHA256;

    @ParameterizedTest
    @EnumSource(value = KrxStockBasicInfoTypeClassificationReasonCode.class, names = "TYPE_INTERPRETED", mode = EnumSource.Mode.EXCLUDE)
    void preservesDeferredResultAndItsEvidenceWithoutAType(KrxStockBasicInfoTypeClassificationReasonCode reason) {
        var result = result(null, reason);

        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.classificationVersion()).isEqualTo(version);
        assertThat(result.sourceReference()).isEqualTo(reference);
        assertThat(result.sourceSha256()).isEqualTo(sha256);
    }

    @ParameterizedTest
    @EnumSource(value = StockSecurityType.class, names = {"COMMON_STOCK", "PREFERRED_STOCK"})
    void acceptsInterpretedTypeWithoutReconstructingTheRawRecord(StockSecurityType type) {
        var result = result(type, KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        assertThat(result.securityType()).isEqualTo(type);
        assertThat(result.rawRecord()).isSameAs(raw);
    }

    @Test
    void requiresATypeForInterpretedReason() {
        assertThatThrownBy(() -> result(null, KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TYPE_INTERPRETED");
    }

    @ParameterizedTest
    @EnumSource(value = KrxStockBasicInfoTypeClassificationReasonCode.class, names = "TYPE_INTERPRETED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsAnyDefaultTypeForADeferredReason(KrxStockBasicInfoTypeClassificationReasonCode reason) {
        for (var type : StockSecurityType.values()) {
            assertThatThrownBy(() -> result(type, reason)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsMissingRecordOrReason() {
        assertThatThrownBy(() -> new KrxStockBasicInfoTypeClassificationResult(null, null,
                KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, version, reference, sha256))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("rawRecord");
        assertThatThrownBy(() -> result(null, null)).isInstanceOf(NullPointerException.class).hasMessageContaining("reasonCode");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingClassificationVersion(String value) {
        assertThatThrownBy(() -> new KrxStockBasicInfoTypeClassificationResult(raw, null,
                KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, value, reference, sha256))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classificationVersion");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingSourceReference(String value) {
        assertThatThrownBy(() -> new KrxStockBasicInfoTypeClassificationResult(raw, null,
                KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, version, value, sha256))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceReference");
    }

    @ParameterizedTest
    @MethodSource("malformedHashes")
    void rejectsMissingMalformedOrGitRevisionShapedSourceHash(String value) {
        assertThatThrownBy(() -> new KrxStockBasicInfoTypeClassificationResult(raw, null,
                KrxStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, version, reference, value))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceSha256");
    }

    @Test
    void validatesEvidenceShapeWithoutReadingOrAuthenticatingItsReference() {
        var result = new KrxStockBasicInfoTypeClassificationResult(raw, StockSecurityType.COMMON_STOCK,
                KrxStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED, version,
                "SYNTHETIC_REFERENCE_NOT_A_FILE", "0".repeat(64));

        assertThat(result.sourceReference()).isEqualTo("SYNTHETIC_REFERENCE_NOT_A_FILE");
        assertThat(result.sourceSha256()).isEqualTo("0".repeat(64));
    }

    private KrxStockBasicInfoTypeClassificationResult result(StockSecurityType type,
                                                           KrxStockBasicInfoTypeClassificationReasonCode reason) {
        return new KrxStockBasicInfoTypeClassificationResult(raw, type, reason, version, reference, sha256);
    }

    private static Stream<String> malformedHashes() {
        return Stream.of(null, "", " ", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64),
                "277ec0eb7a9b7f63b6807829286c80f36649dad2", " " + "a".repeat(64));
    }

    private static KrxStockBasicInfoRawRecord supportedRecord() {
        var fields = values();
        fields[6] = "KOSPI";
        fields[7] = "\uc8fc\uad8c";
        fields[9] = "\ubcf4\ud1b5\uc8fc";
        return rawRecord(1, fields);
    }
}
