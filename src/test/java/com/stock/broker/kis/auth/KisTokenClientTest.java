package com.stock.broker.kis.auth;

import com.stock.broker.kis.auth.dto.KisTokenResponse;
import com.stock.market.price.provider.error.CurrentPriceProviderException;
import com.stock.market.price.provider.error.CurrentPriceProviderFailureType;
import com.stock.market.price.provider.kis.KisCurrentPriceClient;
import com.stock.market.price.provider.kis.KisCurrentPriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisTokenClientTest {
    private static final String BASE_URL = "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String SENSITIVE_CONTENT = APP_KEY + " " + APP_SECRET + " " + ACCESS_TOKEN;

    private MockRestServiceServer server;
    private KisTokenClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisTokenClient(
                builder.build(),
                APP_KEY,
                APP_SECRET
        );
    }

    @Test
    void sendsTokenRequestAndDeserializesResponse() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        """
                                {
                                  "grant_type": "client_credentials",
                                  "appkey": "test-app-key",
                                  "appsecret": "test-app-secret"
                                }
                                """,
                        JsonCompareMode.STRICT
                ))
                .andRespond(withSuccess(
                        """
                                {
                                  "access_token": "test-access-token",
                                  "token_type": "Bearer",
                                  "expires_in": 86400,
                                  "access_token_token_expired": "2026-09-19 22:30:00"
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(86_400L);
        assertThat(response.expiresAt()).isEqualTo("2026-09-19 22:30:00");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void exposesOnlyHttpStatusWithoutReadingErrorBodyOrRetrying(int status) {
        var stream = new ByteArrayInputStream(SENSITIVE_CONTENT.getBytes(StandardCharsets.UTF_8)) {
            boolean closed;

            @Override
            public int read() {
                throw new AssertionError("Error body must not be read.");
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                throw new AssertionError("Error body must not be read.");
            }

            @Override
            public void close() throws IOException {
                closed = true;
                throw new IOException(SENSITIVE_CONTENT);
            }
        };
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andExpect(method(POST))
                .andRespond(request -> new MockClientHttpResponse(stream, status));

        assertSafeFailure("KIS token response HTTP status=" + status + ".", RestClientResponseException.class);

        assertThat(stream.closed).isTrue();
        server.verify();
    }

    @Test
    void stripsResponseDataAndNestedErrorsFromHttpExceptionsRaisedByTheHttpClient() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN);
        headers.set("appkey", APP_KEY);
        headers.set("appsecret", APP_SECRET);
        RestClientResponseException failure = new RestClientResponseException(
                SENSITIVE_CONTENT, 403, SENSITIVE_CONTENT, headers,
                SENSITIVE_CONTENT.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
        );
        failure.initCause(new IllegalStateException(SENSITIVE_CONTENT));
        failure.addSuppressed(new IOException(SENSITIVE_CONTENT));
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> {
                    throw failure;
                });

        assertSafeFailure("KIS token response HTTP status=403.", RestClientResponseException.class);
        server.verify();
    }

    @Test
    void sanitizesTransportFailureIncludingItsCauseAndSuppressedErrorsWithoutRetrying() {
        IOException failure = new IOException(SENSITIVE_CONTENT, new IllegalStateException(SENSITIVE_CONTENT));
        failure.addSuppressed(new IllegalArgumentException(SENSITIVE_CONTENT));
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> {
                    throw failure;
                });

        assertSafeFailure("KIS token request failed.", ResourceAccessException.class);
        server.verify();
    }

    @Test
    void sanitizesRuntimeFailureWithoutRetrying() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> {
                    throw new IllegalStateException(SENSITIVE_CONTENT);
                });

        assertSafeFailure("KIS token request failed.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"access_token\":\"test-access-token\",\"expires_in\":\"test-app-key test-app-secret\"}",
            "{\"access_token\":\"test-access-token\",\"test-app-key\":\"test-app-secret\""
    })
    void sanitizesResponseConversionFailuresWithoutLeakingJsonOrRetrying(String body) {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertSafeFailure("KIS token request failed.", RestClientException.class);
        server.verify();
    }

    @Test
    void sanitizesUnsupportedContentTypeWithoutLeakingBody() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(withSuccess(SENSITIVE_CONTENT, MediaType.TEXT_PLAIN));

        assertSafeFailure("KIS token request failed.", RestClientException.class);
        server.verify();
    }

    @Test
    void sanitizesResponseReadFailure() {
        var stream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException(SENSITIVE_CONTENT);
            }
        };
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> {
                    var response = new MockClientHttpResponse(stream, 200);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });

        assertSafeFailure("KIS token request failed.", RestClientException.class);
        server.verify();
    }

    @Test
    void sanitizesResponseCloseFailureAfterHttpError() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> new MockClientHttpResponse(
                        SENSITIVE_CONTENT.getBytes(StandardCharsets.UTF_8), 401
                ) {
                    @Override
                    public void close() {
                        super.close();
                        IllegalStateException failure = new IllegalStateException(SENSITIVE_CONTENT);
                        failure.addSuppressed(new IOException(SENSITIVE_CONTENT));
                        throw failure;
                    }
                });

        assertSafeFailure("KIS token request failed.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void preservesCurrentPriceProviderHttpFailureClassificationWithoutLeakingOrRetrying(int status) {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> new MockClientHttpResponse(
                        SENSITIVE_CONTENT.getBytes(StandardCharsets.UTF_8), status
                ));
        KisCurrentPriceClient priceClient = mock(KisCurrentPriceClient.class);
        KisTokenProvider tokenProvider = new KisTokenProvider(client, Clock.systemUTC(), Duration.ofMinutes(1));
        KisCurrentPriceProvider priceProvider = new KisCurrentPriceProvider(priceClient, tokenProvider, Clock.systemUTC());

        assertThatThrownBy(() -> priceProvider.getCurrentPrice("005930"))
                .isExactlyInstanceOf(CurrentPriceProviderException.class)
                .satisfies(failure -> {
                    CurrentPriceProviderFailureType expected = status == 429 || status >= 500
                            ? CurrentPriceProviderFailureType.TEMPORARY : CurrentPriceProviderFailureType.PERMANENT;
                    assertThat(((CurrentPriceProviderException) failure).failureType()).isEqualTo(expected);
                    assertThat(failure.getCause()).isExactlyInstanceOf(RestClientResponseException.class).hasNoCause();
                    assertNoSensitiveOutput(failure);
                });
        verifyNoInteractions(priceClient);
        server.verify();
    }

    @Test
    void preservesTemporaryNetworkFailureThroughTokenAndCurrentPriceProvidersWithoutRetrying() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andRespond(request -> {
                    throw new IOException(SENSITIVE_CONTENT);
                });
        KisCurrentPriceClient priceClient = mock(KisCurrentPriceClient.class);
        KisTokenProvider tokenProvider = new KisTokenProvider(client, Clock.systemUTC(), Duration.ofMinutes(1));
        KisCurrentPriceProvider priceProvider = new KisCurrentPriceProvider(priceClient, tokenProvider, Clock.systemUTC());

        assertThatThrownBy(() -> priceProvider.getCurrentPrice("005930"))
                .isExactlyInstanceOf(CurrentPriceProviderException.class)
                .satisfies(failure -> {
                    assertThat(((CurrentPriceProviderException) failure).failureType())
                            .isEqualTo(CurrentPriceProviderFailureType.TEMPORARY);
                    assertThat(failure.getCause()).isExactlyInstanceOf(ResourceAccessException.class).hasNoCause();
                    assertNoSensitiveOutput(failure);
                });
        verifyNoInteractions(priceClient);
        server.verify();
    }

    @Test
    void rejectsBlankAppKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisTokenClient(
                        RestClient.create(BASE_URL),
                        " ",
                        APP_SECRET
                ))
                .withMessage("appKey must not be blank.");
    }

    @Test
    void rejectsBlankAppSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisTokenClient(
                        RestClient.create(BASE_URL),
                        APP_KEY,
                        " "
                ))
                .withMessage("appSecret must not be blank.");
    }

    private void assertSafeFailure(String message) {
        assertSafeFailure(message, IllegalStateException.class);
    }

    private void assertSafeFailure(String message, Class<? extends RuntimeException> failureType) {
        assertThatThrownBy(client::issueToken)
                .isExactlyInstanceOf(failureType)
                .hasMessage(message)
                .hasNoCause()
                .satisfies(failure -> {
                    assertNoSensitiveOutput(failure);
                    if (failure instanceof RestClientResponseException responseFailure) {
                        assertThat(responseFailure.getStatusText()).isEmpty();
                        assertThat(responseFailure.getResponseHeaders()).isEmpty();
                        assertThat(responseFailure.getResponseBodyAsByteArray()).isEmpty();
                        assertThat(responseFailure.getResponseBodyAsString()).isEmpty();
                    }
                });
    }

    private void assertNoSensitiveOutput(Throwable failure) {
        assertThat(failure.getSuppressed()).isEmpty();
        if (failure.getCause() != null) {
            assertThat(failure.getCause().getSuppressed()).isEmpty();
        }
        StringWriter stackTrace = new StringWriter();
        failure.printStackTrace(new PrintWriter(stackTrace));
        assertThat(stackTrace.toString()).doesNotContain(APP_KEY, APP_SECRET, ACCESS_TOKEN);
    }
}
