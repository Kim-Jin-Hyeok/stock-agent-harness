package com.stock.market.stock.basicinfo.provider.kis.dto;

import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRawResponseTest {
    private static final Instant START = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant END = START.plusMillis(53);

    @Test
    void copiesContentAtConstructionAndAccessAndHasByteValueEquality() {
        byte[] bytes = "opaque original bytes".getBytes(StandardCharsets.UTF_8);
        byte[] before = bytes.clone();
        var response = new KisStockBasicInfoRawResponse("0004Y0", START, END, 200, bytes);
        var same = new KisStockBasicInfoRawResponse("0004Y0", START, END, 200, before.clone());
        Arrays.fill(bytes, (byte) 0);
        Arrays.fill(response.content(), (byte) 0);
        assertThat(response.content()).isEqualTo(before);
        assertThat(response).isEqualTo(response).isEqualTo(same).isNotEqualTo(null).isNotEqualTo("not a response");
        assertThat(response.hashCode()).isEqualTo(same.hashCode());
        assertThat(response).isNotEqualTo(new KisStockBasicInfoRawResponse("005930", START, END, 200, before));
        assertThat(response).isNotEqualTo(new KisStockBasicInfoRawResponse("0004Y0", START.plusMillis(1), END, 200, before));
        assertThat(response).isNotEqualTo(new KisStockBasicInfoRawResponse("0004Y0", START, END.plusMillis(1), 200, before));
        assertThat(response).isNotEqualTo(new KisStockBasicInfoRawResponse("0004Y0", START, END, 200, new byte[]{1}));
    }

    @Test
    void describesOnlyMetadataNeverBodyTextOrBase64InToString() {
        String marker = "SENSITIVE_RESPONSE_MARKER";
        var response = new KisStockBasicInfoRawResponse("005930", START, END, 200, marker.getBytes(StandardCharsets.UTF_8));
        assertThat(response.toString()).contains("requestedSymbol=005930", "httpStatus=200", "contentLength=" + marker.length())
                .doesNotContain(marker, "U0VOU0lUSVZFX1JFU1BPTlNFX01BUktFUg==");
    }

    @Test
    void acceptsEqualTimestampsAndTheExactContentLimitWithoutAssertingApiSuccess() {
        byte[] limit = new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES];
        var response = new KisStockBasicInfoRawResponse("0004Y0", START, START, 200, limit);
        assertThat(response.requestStartedAt()).isEqualTo(response.responseReceivedAt());
        assertThat(response.content()).hasSize(limit.length);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 ", "00593?"})
    void rejectsInvalidRequestedSymbols(String symbol) {
        assertThatThrownBy(() -> new KisStockBasicInfoRawResponse(symbol, START, END, 200, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestedSymbol must be exactly 6 uppercase alphanumeric characters.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 199, 201, 204, 301, 400, 500})
    void rejectsHttpStatusOtherThan200(int status) {
        assertThatThrownBy(() -> new KisStockBasicInfoRawResponse("005930", START, END, status, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("httpStatus must be 200.");
    }

    @Test
    void rejectsMissingOrReversedTimestamps() {
        assertThatThrownBy(() -> new KisStockBasicInfoRawResponse("005930", null, END, 200, new byte[]{1}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoRawResponse("005930", START, null, 200, new byte[]{1}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoRawResponse("005930", END, START, 200, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("responseReceivedAt must not precede requestStartedAt.");
    }

    @Test
    void rejectsNullEmptyOrOversizedContent() {
        assertThatThrownBy(() -> new KisStockBasicInfoRawResponse("005930", START, END, 200, null))
                .isInstanceOf(NullPointerException.class);
        for (byte[] bytes : new byte[][]{new byte[0], new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES + 1]}) {
            assertThatThrownBy(() -> new KisStockBasicInfoRawResponse("005930", START, END, 200, bytes))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("content must be nonempty and at most 1 MiB.");
        }
    }
}
