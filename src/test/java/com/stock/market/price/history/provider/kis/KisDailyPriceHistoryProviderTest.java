package com.stock.market.price.history.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderException;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderFailureType;
import com.stock.market.price.history.provider.kis.dto.KisDailyPriceBarOutput;
import com.stock.market.price.history.provider.kis.dto.KisDailyPriceHistoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisDailyPriceHistoryProviderTest {
    private static final String SYMBOL = "005930";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final DailyPriceHistoryRequest REQUEST =
            new DailyPriceHistoryRequest(SYMBOL, FROM_DATE, TO_DATE);

    @Test
    void getsTokenAndMapsPageToAscendingHistory() {
        KisDailyPriceHistoryClient client = mock(
                KisDailyPriceHistoryClient.class
        );
        KisTokenProvider tokenProvider = tokenProvider();
        when(client.getDailyPriceHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenReturn(successfulResponse(List.of(
                        output(TO_DATE),
                        output(TO_DATE.minusDays(1))
                )));
        KisDailyPriceHistoryProvider provider = provider(
                client,
                tokenProvider,
                3
        );

        DailyPriceHistory history = provider.getDailyPriceHistory(REQUEST);

        assertThat(history.symbol()).isEqualTo(SYMBOL);
        assertThat(history.bars())
                .extracting(bar -> bar.tradingDate())
                .containsExactly(TO_DATE.minusDays(1), TO_DATE);
        verify(tokenProvider).getAccessToken();
        verify(client).getDailyPriceHistoryPage(REQUEST, ACCESS_TOKEN);
    }

    @Test
    void requestsOlderRangeWhenPageIsFull() {
        KisDailyPriceHistoryClient client = mock(
                KisDailyPriceHistoryClient.class
        );
        KisTokenProvider tokenProvider = tokenProvider();
        KisDailyPriceHistoryRequestWaiter requestWaiter = mock(
                KisDailyPriceHistoryRequestWaiter.class
        );
        List<KisDailyPriceBarOutput> firstPage = IntStream.range(0, 100)
                .mapToObj(offset -> output(TO_DATE.minusDays(offset)))
                .toList();
        LocalDate oldestDate = TO_DATE.minusDays(99);
        DailyPriceHistoryRequest secondRequest =
                new DailyPriceHistoryRequest(
                        SYMBOL,
                        FROM_DATE,
                        oldestDate.minusDays(1)
                );
        when(client.getDailyPriceHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenReturn(successfulResponse(firstPage));
        when(client.getDailyPriceHistoryPage(
                secondRequest,
                ACCESS_TOKEN
        )).thenReturn(successfulResponse(List.of(
                output(oldestDate.minusDays(1))
        )));
        KisDailyPriceHistoryProvider provider = provider(
                client,
                tokenProvider,
                requestWaiter,
                3
        );

        DailyPriceHistory history = provider.getDailyPriceHistory(REQUEST);

        assertThat(history.bars()).hasSize(101);
        assertThat(history.bars().getFirst().tradingDate())
                .isEqualTo(oldestDate.minusDays(1));
        assertThat(history.bars().getLast().tradingDate())
                .isEqualTo(TO_DATE);
        verify(tokenProvider, times(1)).getAccessToken();
        verify(client).getDailyPriceHistoryPage(REQUEST, ACCESS_TOKEN);
        verify(client).getDailyPriceHistoryPage(
                secondRequest,
                ACCESS_TOKEN
        );
        verify(requestWaiter, times(2)).waitBeforeRequest();
    }

    @Test
    void returnsEmptyHistoryWhenPageIsEmpty() {
        KisDailyPriceHistoryProvider provider = provider(
                successfulResponse(List.of())
        );

        DailyPriceHistory history = provider.getDailyPriceHistory(REQUEST);

        assertThat(history.symbol()).isEqualTo(SYMBOL);
        assertThat(history.bars()).isEmpty();
    }

    @Test
    void rejectsNonPositiveMaxPages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider(
                        mock(KisDailyPriceHistoryClient.class),
                        mock(KisTokenProvider.class),
                        mock(KisDailyPriceHistoryRequestWaiter.class),
                        0
                ))
                .withMessage("maxPages must be positive.");
    }

    @Test
    void classifiesFailedKisResponseAsPermanentFailure() {
        KisDailyPriceHistoryResponse response =
                new KisDailyPriceHistoryResponse(
                        "1",
                        "OPSQ0003",
                        "Request failed.",
                        null
                );

        assertFailure(
                provider(response),
                DailyPriceHistoryProviderFailureType.PERMANENT,
                "KIS daily price history response was not successful. "
                        + "messageCode=OPSQ0003, message=Request failed."
        );
    }

    @Test
    void classifiesKisRateLimitResponseAsTemporaryFailure() {
        KisDailyPriceHistoryResponse response =
                new KisDailyPriceHistoryResponse(
                        "1",
                        "EGW00201",
                        "Rate limit exceeded.",
                        null
                );

        assertFailure(
                provider(response),
                DailyPriceHistoryProviderFailureType.TEMPORARY,
                "KIS daily price history response was not successful. "
                        + "messageCode=EGW00201, message=Rate limit exceeded."
        );
    }

    @Test
    void classifiesMissingOutputAsPermanentFailure() {
        KisDailyPriceHistoryResponse response =
                new KisDailyPriceHistoryResponse(
                        "0",
                        "MCA00000",
                        "Request completed successfully.",
                        null
                );

        assertFailure(
                provider(response),
                DailyPriceHistoryProviderFailureType.PERMANENT,
                "KIS daily price history output must not be null."
        );
    }

    @Test
    void classifiesInvalidBarAsPermanentFailure() {
        KisDailyPriceHistoryResponse response = successfulResponse(List.of(
                new KisDailyPriceBarOutput(
                        "invalid",
                        "70000",
                        "73000",
                        "69000",
                        "72000",
                        "1000000"
                )
        ));

        assertFailure(
                provider(response),
                DailyPriceHistoryProviderFailureType.PERMANENT,
                "KIS daily price history response is invalid."
        );
    }

    @Test
    void classifiesNetworkFailureAsTemporaryFailure() {
        KisDailyPriceHistoryProvider provider = providerThrowing(
                new ResourceAccessException("connection failed")
        );

        assertFailure(
                provider,
                DailyPriceHistoryProviderFailureType.TEMPORARY,
                "KIS daily price history request failed temporarily."
        );
    }

    @Test
    void classifiesTooManyRequestsAsTemporaryFailure() {
        KisDailyPriceHistoryProvider provider = providerThrowing(
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)
        );

        assertFailure(
                provider,
                DailyPriceHistoryProviderFailureType.TEMPORARY,
                "KIS daily price history request failed temporarily."
        );
    }

    @Test
    void classifiesServerFailureAsTemporaryFailure() {
        KisDailyPriceHistoryProvider provider = providerThrowing(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        );

        assertFailure(
                provider,
                DailyPriceHistoryProviderFailureType.TEMPORARY,
                "KIS daily price history request failed temporarily."
        );
    }

    @Test
    void classifiesClientFailureAsPermanentFailure() {
        KisDailyPriceHistoryProvider provider = providerThrowing(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST)
        );

        assertFailure(
                provider,
                DailyPriceHistoryProviderFailureType.PERMANENT,
                "KIS daily price history request failed permanently."
        );
    }

    @Test
    void stopsWhenMaxPagesIsExceeded() {
        List<KisDailyPriceBarOutput> fullPage = IntStream.range(0, 100)
                .mapToObj(offset -> output(TO_DATE.minusDays(offset)))
                .toList();
        KisDailyPriceHistoryProvider provider = provider(
                successfulResponse(fullPage),
                1
        );

        assertFailure(
                provider,
                DailyPriceHistoryProviderFailureType.PERMANENT,
                "KIS daily price history exceeded maxPages=1."
        );
    }

    private void assertFailure(
            KisDailyPriceHistoryProvider provider,
            DailyPriceHistoryProviderFailureType failureType,
            String message
    ) {
        assertThatThrownBy(() -> provider.getDailyPriceHistory(REQUEST))
                .isInstanceOf(DailyPriceHistoryProviderException.class)
                .hasMessage(message)
                .extracting("failureType")
                .isEqualTo(failureType);
    }

    private KisDailyPriceHistoryProvider provider(
            KisDailyPriceHistoryResponse response
    ) {
        return provider(response, 3);
    }

    private KisDailyPriceHistoryProvider provider(
            KisDailyPriceHistoryResponse response,
            int maxPages
    ) {
        KisDailyPriceHistoryClient client = mock(
                KisDailyPriceHistoryClient.class
        );
        when(client.getDailyPriceHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenReturn(response);

        return provider(client, tokenProvider(), maxPages);
    }

    private KisDailyPriceHistoryProvider providerThrowing(
            RuntimeException failure
    ) {
        KisDailyPriceHistoryClient client = mock(
                KisDailyPriceHistoryClient.class
        );
        when(client.getDailyPriceHistoryPage(REQUEST, ACCESS_TOKEN))
                .thenThrow(failure);

        return provider(client, tokenProvider(), 3);
    }

    private KisDailyPriceHistoryProvider provider(
            KisDailyPriceHistoryClient client,
            KisTokenProvider tokenProvider,
            int maxPages
    ) {
        return provider(
                client,
                tokenProvider,
                mock(KisDailyPriceHistoryRequestWaiter.class),
                maxPages
        );
    }

    private KisDailyPriceHistoryProvider provider(
            KisDailyPriceHistoryClient client,
            KisTokenProvider tokenProvider,
            KisDailyPriceHistoryRequestWaiter requestWaiter,
            int maxPages
    ) {
        return new KisDailyPriceHistoryProvider(
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

    private KisDailyPriceHistoryResponse successfulResponse(
            List<KisDailyPriceBarOutput> output
    ) {
        return new KisDailyPriceHistoryResponse(
                "0",
                "MCA00000",
                "Request completed successfully.",
                output
        );
    }

    private KisDailyPriceBarOutput output(LocalDate tradingDate) {
        return new KisDailyPriceBarOutput(
                tradingDate.toString().replace("-", ""),
                "70000",
                "73000",
                "69000",
                "72000",
                "1000000"
        );
    }
}
