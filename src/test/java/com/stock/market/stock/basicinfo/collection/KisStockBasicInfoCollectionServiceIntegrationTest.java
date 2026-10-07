package com.stock.market.stock.basicinfo.collection;

import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DataJpaTest(showSql = false)
@Import({KisStockBasicInfoObservationStore.class, KisStockBasicInfoCollectionServiceIntegrationTest.FixedClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KisStockBasicInfoCollectionServiceIntegrationTest {
    private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
    private static final String APP_KEY = "synthetic-readonly-key";
    private static final String APP_SECRET = "synthetic-readonly-secret";
    private static final String ACCESS_TOKEN = "synthetic-readonly-token";
    @Autowired
    private KisStockBasicInfoObservationStore store;
    @Autowired
    private KisStockBasicInfoObservationRepository repository;
    @Autowired
    private Clock clock;
    private MockRestServiceServer server;
    private KisStockBasicInfoCollectionService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();
        var tokenProvider = new KisTokenProvider(new KisTokenClient(restClient, APP_KEY, APP_SECRET), clock, Duration.ofMinutes(1));
        var provider = new KisStockBasicInfoProvider(new KisStockBasicInfoClient(restClient, APP_KEY, APP_SECRET, clock), tokenProvider);
        service = new KisStockBasicInfoCollectionService(provider, store);
    }

    @AfterEach
    void verifyRequestsAndClearCommittedTestRows() {
        try {
            server.verify();
        } finally {
            repository.deleteAll();
        }
    }

    @ParameterizedTest(name = "content case {index}")
    @MethodSource("com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture#contents")
    void returnedIdRestoresCommittedExactBytesHashAndNormalizedRequestMetadata(byte[] bytes) {
        expectToken();
        expectLookup().andRespond(withSuccess(bytes, MediaType.APPLICATION_OCTET_STREAM));

        Long id = service.collect(SYMBOL);

        assertThat(id).isPositive();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(repository.count()).isEqualTo(1L);
        var restored = store.findById(id).orElseThrow();
        assertThat(restored.requestedSymbol()).isEqualTo(SYMBOL);
        assertThat(restored.httpStatus()).isEqualTo(200);
        assertThat(restored.requestStartedAt()).isEqualTo(START.truncatedTo(ChronoUnit.MICROS));
        assertThat(restored.responseReceivedAt()).isEqualTo(START.truncatedTo(ChronoUnit.MICROS));
        assertThat(restored.content()).isEqualTo(bytes);
        var row = repository.findById(id).orElseThrow();
        assertThat(row.getRecordedAt()).isEqualTo(START.truncatedTo(ChronoUnit.MICROS));
        assertThat(row.getContentSha256()).isEqualTo(sha256(bytes));
        assertThat(row.getContentLength()).isEqualTo(bytes.length);
    }

    @Test
    void collectsWithoutComparingOrNormalizingTheJsonProductNumber() {
        byte[] bytes = (" \n" + KisStockBasicInfoParsingFixture.root().toPrettyString() + "\r\n").getBytes(StandardCharsets.UTF_8);
        expectToken();
        expectLookup().andRespond(withSuccess(bytes, MediaType.APPLICATION_JSON));

        Long id = service.collect(SYMBOL);

        var restored = store.findById(id).orElseThrow();
        assertThat(restored.requestedSymbol()).isEqualTo(SYMBOL);
        assertThat(restored.content()).isEqualTo(bytes);
        var parser = new KisStockBasicInfoParser();
        var parsed = parser.parse(restored.content());
        assertThat(parsed).isEqualTo(parser.parse(bytes));
        assertThat(parsed.rawRecord().productNumber()).isEqualTo("00000A0004Y0").isNotEqualTo(SYMBOL);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void repeatedExplicitCollectionsQueryTwiceSaveSeparateRowsAndReuseOneToken() {
        byte[] bytes = KisStockBasicInfoObservationFixture.content();
        expectToken();
        expectLookup().andRespond(withSuccess(bytes, MediaType.APPLICATION_JSON));
        expectLookup().andRespond(withSuccess(bytes, MediaType.APPLICATION_JSON));

        Long firstId = service.collect(SYMBOL);
        Long secondId = service.collect(SYMBOL);

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(store.findById(firstId).orElseThrow().content()).isEqualTo(bytes);
        assertThat(store.findById(secondId).orElseThrow().content()).isEqualTo(bytes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 "})
    void invalidInputDoesNotMakeAnyHttpRequestOrCreateAnObservation(String symbol) {
        assertThatThrownBy(() -> service.collect(symbol))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.").hasNoCause();

        assertThat(repository.count()).isZero();
    }

    @Test
    void tokenFailureDoesNotQueryRetryOrCreateAnObservation() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP")).andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body(APP_KEY + APP_SECRET + ACCESS_TOKEN));

        assertThatThrownBy(() -> service.collect(SYMBOL))
                .isExactlyInstanceOf(RestClientResponseException.class)
                .hasMessage("KIS token response HTTP status=429.").hasNoCause()
                .satisfies(failure -> assertThat(((RestClientResponseException) failure).getResponseBodyAsByteArray()).isEmpty());

        assertThat(repository.count()).isZero();
    }

    @Test
    void nonSuccessHttpStatusDoesNotRetryOrCreateAnObservation() {
        expectToken();
        expectLookup().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(APP_KEY + APP_SECRET + ACCESS_TOKEN));

        assertThatThrownBy(() -> service.collect(SYMBOL))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info response HTTP status=503.").hasNoCause();

        assertThat(repository.count()).isZero();
    }

    @Test
    void emptyHttp200ContentDoesNotRetryOrCreateAnObservation() {
        expectToken();
        expectLookup().andRespond(withSuccess(new byte[0], MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.collect(SYMBOL))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info content must not be empty.").hasNoCause();

        assertThat(repository.count()).isZero();
    }

    private void expectToken() {
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP")).andExpect(method(POST))
                .andExpect(content().json("""
                        {"grant_type":"client_credentials","appkey":"synthetic-readonly-key",
                         "appsecret":"synthetic-readonly-secret"}
                        """))
                .andExpect(request -> assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("Token HTTP request must run outside a database transaction").isFalse())
                .andRespond(withSuccess("""
                        {"access_token":"synthetic-readonly-token","token_type":"Bearer",
                         "expires_in":3600,"access_token_token_expired":"2026-10-07 11:00:00"}
                        """, MediaType.APPLICATION_JSON));
    }

    private ResponseActions expectLookup() {
        return server.expect(requestTo(BASE_URL
                        + "/uapi/domestic-stock/v1/quotations/search-stock-info?PRDT_TYPE_CD=300&PDNO=" + SYMBOL))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY)).andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "CTPF1002R"))
                .andExpect(request -> assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("Basic info HTTP request must run outside a database transaction").isFalse());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        Clock observationClock() {
            return Clock.fixed(START, ZoneOffset.UTC);
        }
    }
}
