package com.stock.market.stock.basicinfo.provider.kis;

import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.output;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.root;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

class KisStockBasicInfoClientTest {
    private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
    private static final String PATH = "/uapi/domestic-stock/v1/quotations/search-stock-info";
    private static final String APP_KEY = "synthetic-readonly-app-key";
    private static final String APP_SECRET = "synthetic-readonly-app-secret";
    private static final String ACCESS_TOKEN = "synthetic-readonly-access-token";
    private static final Instant START = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant END = START.plusMillis(53);

    private Clock clock;
    private MockRestServiceServer server;
    private KisStockBasicInfoClient client;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(START, END);
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisStockBasicInfoClient(builder.build(), APP_KEY, APP_SECRET, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"005930", "0004Y0"})
    void sendsExactlyOneGetAndPreservesOriginalBytesMetadataAndParserResult(String symbol) throws Exception {
        byte[] bytes = (" \n" + root().toPrettyString() + "\n ").getBytes(StandardCharsets.UTF_8);
        var stream = new TrackingStream(bytes);
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=" + symbol))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT_ENCODING, "identity"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "CTPF1002R"))
                .andExpect(header("custtype", "P"))
                .andRespond(request -> {
                    verify(clock, times(1)).instant();
                    return new MockClientHttpResponse(stream, 200);
                });

        var response = client.getStockBasicInfo(symbol, ACCESS_TOKEN);
        assertThat(response.requestedSymbol()).isEqualTo(symbol);
        assertThat(response.requestStartedAt()).isEqualTo(START);
        assertThat(response.responseReceivedAt()).isEqualTo(END);
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.content()).isEqualTo(bytes);
        assertThat(stream.readCount).isEqualTo(bytes.length);
        assertThat(stream.closed).isTrue();
        verify(clock, times(2)).instant();
        var parser = new KisStockBasicInfoParser();
        assertThat(parser.parse(response.content())).isEqualTo(parser.parse(bytes));
        assertThat(parser.parse(response.content()).inputSha256()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Arrays.fill(response.content(), (byte) 0);
        assertThat(response.content()).isEqualTo(bytes);
        assertThat(response.toString()).doesNotContain(APP_KEY, APP_SECRET, ACCESS_TOKEN, "rt_cd", "msg1");
        server.verify();
    }

    @Test
    void returnsBusinessFailureBytesWithoutTreatingHttp200AsApiSuccess() {
        byte[] bytes = root().put("rt_cd", "1").put("msg1", "Synthetic business failure.")
                .toString().getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(bytes, 200));
        var response = client.getStockBasicInfo("005930", ACCESS_TOKEN);
        assertThat(response.content()).isEqualTo(bytes);
        assertThatThrownBy(() -> new KisStockBasicInfoParser().parse(response.content()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rt_cd must be the success string 0");
        server.verify();
    }

    @Test
    void doesNotParseOrRepairMalformedJsonAtTheTransportBoundary() {
        byte[] bytes = "not-json".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(bytes, 200));
        assertThat(client.getStockBasicInfo("005930", ACCESS_TOKEN).content()).isEqualTo(bytes);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204, 301, 302, 400, 401, 403, 429, 500, 503})
    void rejectsOtherHttpStatusesWithoutReadingLeakingOrRetryingTheBody(int status) {
        var stream = new TrackingStream((APP_KEY + APP_SECRET + ACCESS_TOKEN).getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(stream, status));
        assertThatThrownBy(() -> client.getStockBasicInfo("005930", ACCESS_TOKEN))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info response HTTP status=" + status + ".")
                .hasNoCause().satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
        assertThat(stream.readCount).isZero();
        assertThat(stream.closed).isTrue();
        server.verify();
    }

    @Test
    void sanitizesTransportExceptionsAndMakesNoTokenOrRetryRequest() {
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> {
                    throw new IOException("authorization=" + ACCESS_TOKEN + " appkey=" + APP_KEY + " appsecret=" + APP_SECRET);
                });
        assertSafeFailure("KIS stock basic info request failed.");
        verify(clock, times(1)).instant();
        server.verify();
    }

    @Test
    void sanitizesBodyReadExceptionsAndClosesTheResponse() {
        var stream = new InputStream() {
            boolean closed;

            @Override
            public int read() throws IOException {
                throw new IOException(APP_SECRET + ACCESS_TOKEN);
            }

            @Override
            public void close() {
                closed = true;
            }
        };
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(stream, 200));
        assertSafeFailure("KIS stock basic info request failed.");
        assertThat(stream.closed).isTrue();
        server.verify();
    }

    @Test
    void removesSensitiveSuppressedCloseExceptionsFromARejectedBody() {
        var stream = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                throw new IOException(APP_SECRET + ACCESS_TOKEN);
            }
        };
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(stream, 200));
        assertSafeFailure("KIS stock basic info content must not be empty.");
        server.verify();
    }

    @Test
    void rejectsEmptyContentAndAcceptsExactlyOneMiBWithoutInterpretingIt() {
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(new byte[0], 200));
        assertSafeFailure("KIS stock basic info content must not be empty.");
        server.verify();
        server.reset();
        when(clock.instant()).thenReturn(START, END);
        byte[] bytes = new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES];
        Arrays.fill(bytes, (byte) 'X');
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> new MockClientHttpResponse(bytes, 200));
        assertThat(client.getStockBasicInfo("005930", ACCESS_TOKEN).content()).isEqualTo(bytes);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "1"})
    void stopsAtLimitPlusOneEvenWhenLengthIsAbsentOrMisleading(String length) {
        var stream = new TrackingStream(new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES + 500]);
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> {
                    var response = new MockClientHttpResponse(stream, 200);
                    if (!length.equals("unknown")) {
                        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, length);
                    }
                    return response;
                });
        assertSafeFailure("KIS stock basic info content exceeds 1 MiB.");
        assertThat(stream.readCount).isEqualTo(KisStockBasicInfoParser.MAX_CONTENT_BYTES + 1);
        assertThat(stream.available()).isEqualTo(499);
        assertThat(stream.closed).isTrue();
        server.verify();
    }

    @Test
    void rejectsDeclaredOversizeBeforeReadingAnyBodyBytes() {
        var stream = new TrackingStream(content(output()));
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> {
                    var response = new MockClientHttpResponse(stream, 200);
                    response.getHeaders().setContentLength(KisStockBasicInfoParser.MAX_CONTENT_BYTES + 1L);
                    return response;
                });
        assertSafeFailure("KIS stock basic info content exceeds 1 MiB.");
        assertThat(stream.readCount).isZero();
        assertThat(stream.closed).isTrue();
        server.verify();
    }

    @Test
    void sanitizesMalformedResponseHeadersInsteadOfExposingTheirValues() {
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> {
                    var response = new MockClientHttpResponse(content(output()), 200);
                    response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, APP_SECRET + ACCESS_TOKEN);
                    return response;
                });
        assertSafeFailure("KIS stock basic info request failed.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "br", "", " identity "})
    void rejectsUnexpectedEncodingWithoutReadingTheBody(String encoding) {
        var stream = new TrackingStream(content(output()));
        server.expect(requestTo(BASE_URL + PATH + "?PRDT_TYPE_CD=300&PDNO=005930"))
                .andRespond(request -> {
                    var response = new MockClientHttpResponse(stream, 200);
                    response.getHeaders().set(HttpHeaders.CONTENT_ENCODING, encoding);
                    return response;
                });
        assertSafeFailure("KIS stock basic info content encoding must be identity.");
        assertThat(stream.readCount).isZero();
        assertThat(stream.closed).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://openapivts.koreainvestment.com:29443", "http://openapi.koreainvestment.com:9443",
            "https://example.com:9443", "https://openapi.koreainvestment.com", "https://openapi.koreainvestment.com:9443/extra",
            "https://user:password@openapi.koreainvestment.com:9443", "https://openapi.koreainvestment.com:9443?extra=1",
            "https://openapi.koreainvestment.com:9443#extra"})
    void rejectsNonisolatedEndpointsBeforeAnyRequest(String baseUrl) {
        var builder = RestClient.builder().baseUrl(baseUrl);
        var isolatedServer = MockRestServiceServer.bindTo(builder).build();
        var isolatedClient = new KisStockBasicInfoClient(builder.build(), APP_KEY, APP_SECRET, clock);
        assertThatThrownBy(() -> isolatedClient.getStockBasicInfo("005930", ACCESS_TOKEN))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info requires the isolated live read-only endpoint.").hasNoCause();
        isolatedServer.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 ", "00593?", "00593/", "\r\n"})
    void rejectsInvalidSymbolsWithoutNormalizingOrCalling(String symbol) {
        assertThatThrownBy(() -> client.getStockBasicInfo(symbol, ACCESS_TOKEN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
        verifyNoInteractions(clock);
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " token", "token ", "to ken", "token\r\nInjected: value", "\u00e9"})
    void rejectsUnsafeTokensOrKeysBeforeCallingWithoutPrintingTheirValues(String value) {
        assertThatThrownBy(() -> client.getStockBasicInfo("005930", value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accessToken must be nonblank visible ASCII without whitespace.");
        assertThatThrownBy(() -> new KisStockBasicInfoClient(RestClient.create(), value, APP_SECRET, clock))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("appKey must be nonblank visible ASCII without whitespace.");
        assertThatThrownBy(() -> new KisStockBasicInfoClient(RestClient.create(), APP_KEY, value, clock))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("appSecret must be nonblank visible ASCII without whitespace.");
        verifyNoInteractions(clock);
        server.verify();
    }

    @Test
    void rejectsNullClientOrClock() {
        assertThatThrownBy(() -> new KisStockBasicInfoClient(null, APP_KEY, APP_SECRET, clock)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoClient(RestClient.create(), APP_KEY, APP_SECRET, null)).isInstanceOf(NullPointerException.class);
    }

    private void assertSafeFailure(String message) {
        assertThatThrownBy(() -> client.getStockBasicInfo("005930", ACCESS_TOKEN))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessage(message).hasNoCause()
                .satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
    }

    private static class TrackingStream extends InputStream {
        private final ByteArrayInputStream delegate;
        int readCount;
        boolean closed;

        private TrackingStream(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            int value = delegate.read();
            if (value != -1) {
                readCount++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                readCount += count;
            }
            return count;
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
