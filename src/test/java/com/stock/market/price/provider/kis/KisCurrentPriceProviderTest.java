package com.stock.market.price.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.provider.error.CurrentPriceProviderException;
import com.stock.market.price.provider.error.CurrentPriceProviderFailureType;
import com.stock.market.price.provider.kis.dto.KisCurrentPriceOutput;
import com.stock.market.price.provider.kis.dto.KisCurrentPriceResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KisCurrentPriceProviderTest {
    private static final String SYMBOL = "005930";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-22T05:00:00Z");

    @Test
    void getsTokenAndMapsCurrentPriceResponseToSnapshot() {
        KisCurrentPriceClient currentPriceClient = mock(
                KisCurrentPriceClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(currentPriceClient.getCurrentPrice(SYMBOL, ACCESS_TOKEN))
                .thenReturn(successfulResponse());
        when(clock.instant()).thenReturn(OBSERVED_AT);
        KisCurrentPriceProvider provider = provider(
                currentPriceClient,
                tokenProvider,
                clock
        );

        CurrentPriceSnapshot snapshot = provider.getCurrentPrice(SYMBOL);

        assertThat(snapshot.symbol()).isEqualTo(SYMBOL);
        assertThat(snapshot.priceKrw()).isEqualTo(72_000L);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);

        InOrder inOrder = inOrder(tokenProvider, currentPriceClient, clock);
        inOrder.verify(tokenProvider).getAccessToken();
        inOrder.verify(currentPriceClient).getCurrentPrice(
                SYMBOL,
                ACCESS_TOKEN
        );
        inOrder.verify(clock).instant();
    }

    @Test
    void classifiesFailedKisResponseAsPermanentFailure() {
        KisCurrentPriceResponse response = new KisCurrentPriceResponse(
                "1",
                "EGW00123",
                "Request failed.",
                null
        );
        KisCurrentPriceProvider provider = provider(response);

        assertPermanentFailure(
                provider,
                "KIS current price response was not successful. "
                        + "messageCode=EGW00123"
        );
    }

    @Test
    void classifiesMissingOutputAsPermanentFailure() {
        KisCurrentPriceResponse response = new KisCurrentPriceResponse(
                "0",
                "MCA00000",
                "Request completed successfully.",
                null
        );
        KisCurrentPriceProvider provider = provider(response);

        assertPermanentFailure(
                provider,
                "KIS current price output must not be null."
        );
    }

    @Test
    void classifiesInvalidCurrentPriceAsPermanentFailure() {
        KisCurrentPriceResponse response = new KisCurrentPriceResponse(
                "0",
                "MCA00000",
                "Request completed successfully.",
                new KisCurrentPriceOutput("invalid")
        );
        KisCurrentPriceProvider provider = provider(response);

        assertPermanentFailure(
                provider,
                "KIS current price response is invalid."
        );
    }

    @Test
    void classifiesNetworkFailureAsTemporaryFailure() {
        KisCurrentPriceProvider provider = providerThrowing(
                new ResourceAccessException("connection failed")
        );

        assertThatThrownBy(() -> provider.getCurrentPrice(SYMBOL))
                .isInstanceOf(CurrentPriceProviderException.class)
                .hasMessage("KIS current price request failed temporarily.")
                .extracting("failureType")
                .isEqualTo(CurrentPriceProviderFailureType.TEMPORARY);
    }

    @Test
    void classifiesServerFailureAsTemporaryFailure() {
        KisCurrentPriceProvider provider = providerThrowing(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        );

        assertThatThrownBy(() -> provider.getCurrentPrice(SYMBOL))
                .isInstanceOf(CurrentPriceProviderException.class)
                .hasMessage("KIS current price request failed temporarily.")
                .extracting("failureType")
                .isEqualTo(CurrentPriceProviderFailureType.TEMPORARY);
    }

    @Test
    void classifiesClientFailureAsPermanentFailure() {
        KisCurrentPriceProvider provider = providerThrowing(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST)
        );

        assertPermanentFailure(
                provider,
                "KIS current price request failed permanently."
        );
    }

    private void assertPermanentFailure(
            KisCurrentPriceProvider provider,
            String message
    ) {
        assertThatThrownBy(() -> provider.getCurrentPrice(SYMBOL))
                .isInstanceOf(CurrentPriceProviderException.class)
                .hasMessage(message)
                .extracting("failureType")
                .isEqualTo(CurrentPriceProviderFailureType.PERMANENT);
    }

    private KisCurrentPriceProvider provider(
            KisCurrentPriceResponse response
    ) {
        KisCurrentPriceClient currentPriceClient = mock(
                KisCurrentPriceClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(currentPriceClient.getCurrentPrice(SYMBOL, ACCESS_TOKEN))
                .thenReturn(response);

        return provider(
                currentPriceClient,
                tokenProvider,
                Clock.systemUTC()
        );
    }

    private KisCurrentPriceProvider providerThrowing(RuntimeException failure) {
        KisCurrentPriceClient currentPriceClient = mock(
                KisCurrentPriceClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(currentPriceClient.getCurrentPrice(SYMBOL, ACCESS_TOKEN))
                .thenThrow(failure);

        return provider(
                currentPriceClient,
                tokenProvider,
                Clock.systemUTC()
        );
    }

    private KisCurrentPriceProvider provider(
            KisCurrentPriceClient currentPriceClient,
            KisTokenProvider tokenProvider,
            Clock clock
    ) {
        return new KisCurrentPriceProvider(
                currentPriceClient,
                tokenProvider,
                clock
        );
    }

    private KisCurrentPriceResponse successfulResponse() {
        return new KisCurrentPriceResponse(
                "0",
                "MCA00000",
                "Request completed successfully.",
                new KisCurrentPriceOutput("72000")
        );
    }
}
