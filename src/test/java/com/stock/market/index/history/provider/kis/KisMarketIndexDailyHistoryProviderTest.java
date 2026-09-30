package com.stock.market.index.history.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.provider.error.MarketIndexDailyHistoryProviderException;
import com.stock.market.index.history.provider.error.MarketIndexDailyHistoryProviderFailureType;
import com.stock.market.index.history.provider.kis.dto.KisMarketIndexDailyHistoryResponse;
import com.stock.market.index.history.provider.kis.dto.KisMarketIndexDailyObservationOutput;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisMarketIndexDailyHistoryProviderTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 2);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final MarketIndexDailyHistoryRequest REQUEST =
            new MarketIndexDailyHistoryRequest(
                    BENCHMARK_ID,
                    FROM_DATE,
                    TO_DATE
            );

    @Test
    void getsTokenAndMapsPageToAscendingHistory() {
        KisMarketIndexDailyHistoryClient client = mock(
                KisMarketIndexDailyHistoryClient.class
        );
        KisTokenProvider tokenProvider = tokenProvider();
        when(client.getDailyHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenReturn(successfulResponse(List.of(
                        output(TO_DATE),
                        output(FROM_DATE)
                )));
        KisMarketIndexDailyHistoryProvider provider = provider(
                client,
                tokenProvider,
                3
        );

        MarketIndexDailyHistory history = provider.getDailyHistory(REQUEST);

        assertThat(history.benchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(history.observations())
                .extracting(observation -> observation.observationDate())
                .containsExactly(FROM_DATE, TO_DATE);
        verify(tokenProvider).getAccessToken();
        verify(client).getDailyHistoryPage(REQUEST, ACCESS_TOKEN);
    }

    @Test
    void requestsOlderPagesUntilFromDateIsReached() {
        KisMarketIndexDailyHistoryClient client = mock(
                KisMarketIndexDailyHistoryClient.class
        );
        KisTokenProvider tokenProvider = tokenProvider();
        KisMarketIndexDailyHistoryRequestWaiter requestWaiter = mock(
                KisMarketIndexDailyHistoryRequestWaiter.class
        );
        LocalDate firstPageOldestDate = TO_DATE.minusDays(1);
        MarketIndexDailyHistoryRequest secondRequest =
                new MarketIndexDailyHistoryRequest(
                        BENCHMARK_ID,
                        FROM_DATE,
                        firstPageOldestDate.minusDays(1)
                );
        when(client.getDailyHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenReturn(successfulResponse(List.of(
                        output(TO_DATE),
                        output(firstPageOldestDate)
                )));
        when(client.getDailyHistoryPage(secondRequest, ACCESS_TOKEN))
                .thenReturn(successfulResponse(List.of(
                        output(FROM_DATE)
                )));
        KisMarketIndexDailyHistoryProvider provider = provider(
                client,
                tokenProvider,
                requestWaiter,
                3
        );

        MarketIndexDailyHistory history = provider.getDailyHistory(REQUEST);

        assertThat(history.observations())
                .extracting(observation -> observation.observationDate())
                .containsExactly(
                        FROM_DATE,
                        firstPageOldestDate,
                        TO_DATE
                );
        verify(client).getDailyHistoryPage(REQUEST, ACCESS_TOKEN);
        verify(client).getDailyHistoryPage(secondRequest, ACCESS_TOKEN);
        verify(requestWaiter, times(2)).waitBeforeRequest();
    }

    @Test
    void returnsEmptyHistoryWhenFirstPageIsEmpty() {
        KisMarketIndexDailyHistoryProvider provider = provider(
                successfulResponse(List.of())
        );

        MarketIndexDailyHistory history = provider.getDailyHistory(REQUEST);

        assertThat(history.benchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(history.observations()).isEmpty();
    }

    @Test
    void rejectsNonPositiveMaxPages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider(
                        mock(KisMarketIndexDailyHistoryClient.class),
                        mock(KisTokenProvider.class),
                        mock(KisMarketIndexDailyHistoryRequestWaiter.class),
                        0
                ))
                .withMessage("maxPages must be positive.");
    }

    @Test
    void classifiesFailedKisResponseAsPermanentFailure() {
        KisMarketIndexDailyHistoryResponse response =
                new KisMarketIndexDailyHistoryResponse(
                        "1",
                        "OPSQ0003",
                        "Request failed.",
                        null
                );

        assertFailure(
                provider(response),
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history response was not "
                        + "successful. messageCode=OPSQ0003, "
                        + "message=Request failed."
        );
    }

    @Test
    void classifiesKisRateLimitResponseAsTemporaryFailure() {
        KisMarketIndexDailyHistoryResponse response =
                new KisMarketIndexDailyHistoryResponse(
                        "1",
                        "EGW00201",
                        "Rate limit exceeded.",
                        null
                );

        assertFailure(
                provider(response),
                MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                "KIS market index daily history response was not "
                        + "successful. messageCode=EGW00201, "
                        + "message=Rate limit exceeded."
        );
    }

    @Test
    void classifiesNullResponseAsPermanentFailure() {
        assertFailure(
                provider(null),
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history response must not be null."
        );
    }

    @Test
    void classifiesMissingOutputAsPermanentFailure() {
        KisMarketIndexDailyHistoryResponse response =
                new KisMarketIndexDailyHistoryResponse(
                        "0",
                        "MCA00000",
                        "Request completed successfully.",
                        null
                );

        assertFailure(
                provider(response),
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history output must not be null."
        );
    }

    @Test
    void classifiesInvalidObservationAsPermanentFailure() {
        KisMarketIndexDailyHistoryResponse response = successfulResponse(
                List.of(new KisMarketIndexDailyObservationOutput(
                        "invalid",
                        "3421.37"
                ))
        );

        assertFailure(
                provider(response),
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history response is invalid."
        );
    }

    @Test
    void classifiesObservationOutsideRequestedRangeAsPermanentFailure() {
        KisMarketIndexDailyHistoryResponse response = successfulResponse(
                List.of(output(FROM_DATE.minusDays(1)))
        );

        assertFailure(
                provider(response),
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history response is invalid."
        );
    }

    @Test
    void classifiesDuplicateObservationDateAsPermanentFailure() {
        KisMarketIndexDailyHistoryResponse response = successfulResponse(
                List.of(output(FROM_DATE), output(FROM_DATE))
        );

        assertFailure(
                provider(response),
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history response is invalid."
        );
    }

    @Test
    void classifiesNetworkFailureAsTemporaryFailure() {
        KisMarketIndexDailyHistoryProvider provider = providerThrowing(
                new ResourceAccessException("connection failed")
        );

        assertFailure(
                provider,
                MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                "KIS market index daily history request failed "
                        + "temporarily."
        );
    }

    @Test
    void classifiesTooManyRequestsAsTemporaryFailure() {
        KisMarketIndexDailyHistoryProvider provider = providerThrowing(
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)
        );

        assertFailure(
                provider,
                MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                "KIS market index daily history request failed "
                        + "temporarily."
        );
    }

    @Test
    void classifiesServerFailureAsTemporaryFailure() {
        KisMarketIndexDailyHistoryProvider provider = providerThrowing(
                new HttpServerErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
        );

        assertFailure(
                provider,
                MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                "KIS market index daily history request failed "
                        + "temporarily."
        );
    }

    @Test
    void classifiesClientFailureAsPermanentFailure() {
        KisMarketIndexDailyHistoryProvider provider = providerThrowing(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST)
        );

        assertFailure(
                provider,
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history request failed "
                        + "permanently."
        );
    }

    @Test
    void stopsWhenMaxPagesIsExceeded() {
        KisMarketIndexDailyHistoryProvider provider = provider(
                successfulResponse(List.of(output(TO_DATE))),
                1
        );

        assertFailure(
                provider,
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                "KIS market index daily history exceeded maxPages=1."
        );
    }

    private void assertFailure(
            KisMarketIndexDailyHistoryProvider provider,
            MarketIndexDailyHistoryProviderFailureType failureType,
            String message
    ) {
        assertThatThrownBy(() -> provider.getDailyHistory(REQUEST))
                .isInstanceOf(
                        MarketIndexDailyHistoryProviderException.class
                )
                .hasMessage(message)
                .extracting("failureType")
                .isEqualTo(failureType);
    }

    private KisMarketIndexDailyHistoryProvider provider(
            KisMarketIndexDailyHistoryResponse response
    ) {
        return provider(response, 3);
    }

    private KisMarketIndexDailyHistoryProvider provider(
            KisMarketIndexDailyHistoryResponse response,
            int maxPages
    ) {
        KisMarketIndexDailyHistoryClient client = mock(
                KisMarketIndexDailyHistoryClient.class
        );
        when(client.getDailyHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenReturn(response);
        return provider(client, tokenProvider(), maxPages);
    }

    private KisMarketIndexDailyHistoryProvider providerThrowing(
            RuntimeException failure
    ) {
        KisMarketIndexDailyHistoryClient client = mock(
                KisMarketIndexDailyHistoryClient.class
        );
        when(client.getDailyHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenThrow(failure);
        return provider(client, tokenProvider(), 3);
    }

    private KisMarketIndexDailyHistoryProvider provider(
            KisMarketIndexDailyHistoryClient client,
            KisTokenProvider tokenProvider,
            int maxPages
    ) {
        return provider(
                client,
                tokenProvider,
                mock(KisMarketIndexDailyHistoryRequestWaiter.class),
                maxPages
        );
    }

    private KisMarketIndexDailyHistoryProvider provider(
            KisMarketIndexDailyHistoryClient client,
            KisTokenProvider tokenProvider,
            KisMarketIndexDailyHistoryRequestWaiter requestWaiter,
            int maxPages
    ) {
        return new KisMarketIndexDailyHistoryProvider(
                client,
                tokenProvider,
                requestWaiter,
                maxPages
        );
    }

    private KisTokenProvider tokenProvider() {
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        return tokenProvider;
    }

    private KisMarketIndexDailyHistoryResponse successfulResponse(
            List<KisMarketIndexDailyObservationOutput> output
    ) {
        return new KisMarketIndexDailyHistoryResponse(
                "0",
                "MCA00000",
                "Request completed successfully.",
                output
        );
    }

    private KisMarketIndexDailyObservationOutput output(
            LocalDate observationDate
    ) {
        return new KisMarketIndexDailyObservationOutput(
                observationDate.toString().replace("-", ""),
                "3421.37"
        );
    }
}
