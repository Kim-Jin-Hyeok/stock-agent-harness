package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.KisStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.support.KisStockBasicInfoTypeClassificationFixture.parsed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoTypeClassificationResultTest {
    private final KisStockBasicInfoParseResult input = parsed("STK", "300", "ST", "101");
    private final String version = KisStockBasicInfoTypeClassificationPolicy.CLASSIFICATION_VERSION;
    private final String reference = KisStockBasicInfoTypeClassificationPolicy.SOURCE_REFERENCE;
    private final String sha256 = KisStockBasicInfoTypeClassificationPolicy.SOURCE_SHA256;

    @ParameterizedTest
    @EnumSource(value = KisStockBasicInfoTypeClassificationReasonCode.class, names = "TYPE_INTERPRETED", mode = EnumSource.Mode.EXCLUDE)
    void preservesDeferredReasonAndEntireInputWithoutAType(KisStockBasicInfoTypeClassificationReasonCode reason) {
        var result = result(null, reason);

        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.parseResult()).isSameAs(input);
        assertThat(result.classificationVersion()).isEqualTo(version);
        assertThat(result.sourceReference()).isEqualTo(reference);
        assertThat(result.sourceSha256()).isEqualTo(sha256);
    }

    @ParameterizedTest
    @EnumSource(value = StockSecurityType.class, names = {"COMMON_STOCK", "PREFERRED_STOCK"})
    void acceptsInterpretedIndividualShareTypesAndRetainsParserEvidence(StockSecurityType type) {
        var result = result(type, KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);

        assertThat(result.securityType()).isEqualTo(type);
        assertThat(result.parseResult()).isSameAs(input);
        assertThat(result.parseResult().rawRecord()).isSameAs(input.rawRecord());
        assertThat(result.parseResult().inputSha256()).isEqualTo(input.inputSha256());
        assertThat(result.parseResult().parserVersion()).isEqualTo(input.parserVersion());
    }

    @Test
    void requiresATypeForInterpretedReason() {
        assertThatThrownBy(() -> result(null, KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TYPE_INTERPRETED");
    }

    @ParameterizedTest
    @EnumSource(value = KisStockBasicInfoTypeClassificationReasonCode.class, names = "TYPE_INTERPRETED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsAllDefaultTypesForDeferredReasons(KisStockBasicInfoTypeClassificationReasonCode reason) {
        for (var type : StockSecurityType.values()) {
            assertThatThrownBy(() -> result(type, reason)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = StockSecurityType.class, names = {"COMMON_STOCK", "PREFERRED_STOCK"}, mode = EnumSource.Mode.EXCLUDE)
    void rejectsTypesOutsideThisInterpretationContract(StockSecurityType type) {
        assertThatThrownBy(() -> result(type, KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("COMMON_STOCK or PREFERRED_STOCK");
    }

    @Test
    void rejectsNullInputOrReason() {
        assertThatThrownBy(() -> new KisStockBasicInfoTypeClassificationResult(null, null,
                KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, version, reference, sha256))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("parseResult");
        assertThatThrownBy(() -> result(null, null)).isInstanceOf(NullPointerException.class).hasMessageContaining("reasonCode");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void rejectsAbsentClassificationVersion(String value) {
        assertThatThrownBy(() -> new KisStockBasicInfoTypeClassificationResult(input, null,
                KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, value, reference, sha256))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classificationVersion");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void rejectsAbsentSourceReference(String value) {
        assertThatThrownBy(() -> new KisStockBasicInfoTypeClassificationResult(input, null,
                KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, version, value, sha256))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceReference");
    }

    @ParameterizedTest
    @MethodSource("malformedHashes")
    void rejectsMalformedOrMissingSpecificationHashes(String value) {
        assertThatThrownBy(() -> new KisStockBasicInfoTypeClassificationResult(input, null,
                KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED, version, reference, value))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceSha256");
    }

    @Test
    void validatesMetadataShapeWithoutReadingOrAuthenticatingTheSourceReference() {
        var result = new KisStockBasicInfoTypeClassificationResult(input, StockSecurityType.COMMON_STOCK,
                KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED, version,
                "SYNTHETIC_REFERENCE_NOT_A_FILE", "0".repeat(64));

        assertThat(result.sourceReference()).isEqualTo("SYNTHETIC_REFERENCE_NOT_A_FILE");
        assertThat(result.sourceSha256()).isEqualTo("0".repeat(64));
    }

    @Test
    void roundTripsTheCompletePolicyResultIncludingAllRawValuesAndHashes() throws Exception {
        var mapper = new ObjectMapper();
        var policy = new KisStockBasicInfoTypeClassificationPolicy();
        for (var parsed : new KisStockBasicInfoParseResult[]{input, parsed("KSQ", "300", "ST", "202"),
                parsed("STK", "300", "EF", ""), parsed("KSQ", "300", "ST", "")}) {
            var result = policy.classify(parsed);

            var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoTypeClassificationResult.class);

            assertThat(restored).isEqualTo(result);
            assertThat(restored.parseResult()).isEqualTo(parsed).isNotSameAs(parsed);
            assertThat(restored.parseResult().rawRecord()).isEqualTo(parsed.rawRecord());
            assertThat(restored.parseResult().inputSha256()).isEqualTo(parsed.inputSha256());
        }
    }

    private KisStockBasicInfoTypeClassificationResult result(StockSecurityType type,
                                                            KisStockBasicInfoTypeClassificationReasonCode reason) {
        return new KisStockBasicInfoTypeClassificationResult(input, type, reason, version, reference, sha256);
    }

    private static Stream<String> malformedHashes() {
        return Stream.of(null, "", " ", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64),
                "277ec0eb7a9b7f63b6807829286c80f36649dad2", " " + "a".repeat(64));
    }
}
