package com.stock.market.stock.basicinfo.observation.persistence;

import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.END;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.content;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.response;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoObservationEntityTest {
    @ParameterizedTest(name = "content case {index}")
    @MethodSource("com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture#contents")
    void preservesRawBytesAndDerivesMetadataWithoutParsing(byte[] bytes) {
        var original = response(bytes);
        var entity = KisStockBasicInfoObservationEntity.from(original, RECORDED_AT);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getRequestedSymbol()).isEqualTo(SYMBOL);
        assertThat(entity.getHttpStatus()).isEqualTo(200);
        assertThat(entity.getRequestStartedAt()).isEqualTo(START.truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.getResponseReceivedAt()).isEqualTo(END.truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.getRecordedAt()).isEqualTo(RECORDED_AT.truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.getContentLength()).isEqualTo(bytes.length);
        assertThat(entity.getContentSha256()).isEqualTo(sha256(bytes));
        assertThat(entity.getRawContent()).isEqualTo(bytes);
        assertThat(entity.toRawResponse()).isEqualTo(normalized(original));
        assertThat(original.requestStartedAt()).isEqualTo(START);
        assertThat(original.responseReceivedAt()).isEqualTo(END);
        assertThat(original.content()).isEqualTo(bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-07T01:00:00.999999999Z", "2026-10-07T01:00:00.000000001Z",
            "2026-10-07T01:00:00.123456Z", "2026-10-07T01:00:00Z"})
    void truncatesRatherThanRoundsAllTimesAndAllowsEqualNormalizedTimes(String timestamp) {
        Instant start = Instant.parse(timestamp);
        Instant end = start.plusNanos(1);
        var original = new KisStockBasicInfoRawResponse(SYMBOL, start, end, 200, content());

        var entity = KisStockBasicInfoObservationEntity.from(original, end.plusSeconds(1));

        assertThat(entity.getRequestStartedAt()).isEqualTo(start.truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.getResponseReceivedAt()).isEqualTo(end.truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.getRecordedAt()).isEqualTo(end.plusSeconds(1).truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.toRawResponse()).isEqualTo(normalized(original));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, KisStockBasicInfoParser.MAX_CONTENT_BYTES})
    void supportsMinimumAndMaximumRawContentWithoutStringConversion(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0xff);
        var entity = KisStockBasicInfoObservationEntity.from(response(bytes), RECORDED_AT);

        assertThat(entity.getContentLength()).isEqualTo(length);
        assertThat(entity.toRawResponse().content()).isEqualTo(bytes);
    }

    @Test
    void defensivelyCopiesPayloadOnEveryExposedBoundary() {
        byte[] bytes = content();
        byte[] expected = bytes.clone();
        var original = response(bytes);
        var entity = KisStockBasicInfoObservationEntity.from(original, RECORDED_AT);

        Arrays.fill(bytes, (byte) 0);
        Arrays.fill(original.content(), (byte) 0);
        Arrays.fill(entity.getRawContent(), (byte) 0);
        Arrays.fill(entity.toRawResponse().content(), (byte) 0);

        assertThat(original.content()).isEqualTo(expected);
        assertThat(entity.getRawContent()).isEqualTo(expected);
        assertThat(entity.getContentSha256()).isEqualTo(sha256(expected));
        assertThat(entity.toRawResponse().content()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "corruption case {index}: {0}")
    @MethodSource("contentCorruptions")
    void rejectsContentIntegrityFailureWithoutRepairingOrExposingStoredValues(String field, Object value) {
        var entity = KisStockBasicInfoObservationEntity.from(response(), RECORDED_AT);
        ReflectionTestUtils.setField(entity, "id", 17L);
        ReflectionTestUtils.setField(entity, field, value);

        assertThatThrownBy(entity::toRawResponse).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Stored KIS stock basic info content integrity check failed. id=17").hasNoCause()
                .satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
        assertThat(ReflectionTestUtils.getField(entity, field)).isEqualTo(value);
    }

    @ParameterizedTest(name = "metadata case {index}: {0}")
    @MethodSource("invalidMetadata")
    void rejectsInvalidMetadataWithoutRepairingOrExposingStoredValues(String field, Object value) {
        var entity = KisStockBasicInfoObservationEntity.from(response(), RECORDED_AT);
        ReflectionTestUtils.setField(entity, "id", 17L);
        ReflectionTestUtils.setField(entity, field, value);

        assertThatThrownBy(entity::toRawResponse).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Stored KIS stock basic info metadata is invalid. id=17").hasNoCause()
                .satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
        assertThat(ReflectionTestUtils.getField(entity, field)).isEqualTo(value);
    }

    @Test
    void doesNotTreatRecordedTimeAsTheSourceEffectiveTimeOrRequireClockOrdering() {
        Instant recordedAt = START.minusSeconds(1);
        var entity = KisStockBasicInfoObservationEntity.from(response(), recordedAt);

        assertThat(entity.getRecordedAt()).isEqualTo(recordedAt.truncatedTo(ChronoUnit.MICROS));
        assertThat(entity.toRawResponse()).isEqualTo(normalized(response()));
    }

    @Test
    void rejectsNullFactoryInputs() {
        assertThatThrownBy(() -> KisStockBasicInfoObservationEntity.from(null, RECORDED_AT))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("response must not be null.");
        assertThatThrownBy(() -> KisStockBasicInfoObservationEntity.from(response(), null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("recordedAt must not be null.");
    }

    private static Stream<Arguments> contentCorruptions() {
        byte[] changed = content();
        changed[0] ^= 1;
        return Stream.of(
                Arguments.of("rawContent", null), Arguments.of("rawContent", new byte[0]),
                Arguments.of("rawContent", new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES + 1]),
                Arguments.of("rawContent", changed), Arguments.of("rawContent", new byte[]{1}),
                Arguments.of("contentLength", 0), Arguments.of("contentLength", -1),
                Arguments.of("contentLength", content().length + 1), Arguments.of("contentSha256", null),
                Arguments.of("contentSha256", "invalid-hash"), Arguments.of("contentSha256", "0".repeat(64)),
                Arguments.of("contentSha256", sha256(content()).toUpperCase(Locale.ROOT))
        );
    }

    private static Stream<Arguments> invalidMetadata() {
        return Stream.of(
                Arguments.of("requestedSymbol", null), Arguments.of("requestedSymbol", ""),
                Arguments.of("requestedSymbol", "0004y0"), Arguments.of("requestedSymbol", "synthetic-sensitive-value"),
                Arguments.of("httpStatus", 201), Arguments.of("requestStartedAt", null),
                Arguments.of("responseReceivedAt", null), Arguments.of("recordedAt", null),
                Arguments.of("requestStartedAt", START), Arguments.of("responseReceivedAt", END),
                Arguments.of("recordedAt", RECORDED_AT),
                Arguments.of("responseReceivedAt", START.minusSeconds(1).truncatedTo(ChronoUnit.MICROS))
        );
    }
}
