package com.stock.broker.kis.order.provider;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryMarket;
import com.stock.broker.kis.order.KisCashOrderClient;
import com.stock.broker.kis.order.dto.KisCashOrderOutput;
import com.stock.broker.kis.order.dto.KisCashOrderResponse;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderSubmission;
import com.stock.broker.order.BrokerOrderSubmissionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisBrokerOrderProviderTest {
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-23T00:00:00Z");

    @Test
    void getsTokenAndMapsSuccessfulResponseToAcceptedSubmission() {
        KisCashOrderClient cashOrderClient = mock(KisCashOrderClient.class);
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        BrokerOrderRequest request = request();
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(cashOrderClient.submitLimitOrder(
                request.side(),
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                request.symbol(),
                request.quantity(),
                request.limitPriceKrw(),
                ACCESS_TOKEN
        )).thenReturn(successfulResponse());
        when(clock.instant()).thenReturn(SUBMITTED_AT);
        KisBrokerOrderProvider provider = provider(
                cashOrderClient,
                tokenProvider,
                clock
        );

        BrokerOrderSubmission submission = provider.submit(request);

        assertThat(submission.status())
                .isEqualTo(BrokerOrderSubmissionStatus.ACCEPTED);
        assertThat(submission.reference()).isEqualTo(
                new BrokerOrderReference("0000123456", "06010")
        );
        assertThat(submission.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(submission.reason()).isNull();

        InOrder inOrder = inOrder(tokenProvider, cashOrderClient, clock);
        inOrder.verify(tokenProvider).getAccessToken();
        inOrder.verify(cashOrderClient).submitLimitOrder(
                request.side(),
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                request.symbol(),
                request.quantity(),
                request.limitPriceKrw(),
                ACCESS_TOKEN
        );
        inOrder.verify(clock).instant();
    }

    @Test
    void mapsKisBusinessFailureToRejectedSubmission() {
        KisCashOrderClient cashOrderClient = mock(KisCashOrderClient.class);
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        BrokerOrderRequest request = request();
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(cashOrderClient.submitLimitOrder(
                request.side(),
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                request.symbol(),
                request.quantity(),
                request.limitPriceKrw(),
                ACCESS_TOKEN
        )).thenReturn(new KisCashOrderResponse(
                "1",
                "APBK0918",
                "Order was rejected.",
                null
        ));
        when(clock.instant()).thenReturn(SUBMITTED_AT);
        KisBrokerOrderProvider provider = provider(
                cashOrderClient,
                tokenProvider,
                clock
        );

        BrokerOrderSubmission submission = provider.submit(request);

        assertThat(submission.status())
                .isEqualTo(BrokerOrderSubmissionStatus.REJECTED);
        assertThat(submission.reference()).isNull();
        assertThat(submission.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(submission.reason()).isEqualTo(
                "KIS order was rejected. messageCode=APBK0918, "
                        + "message=Order was rejected."
        );
    }

    @Test
    void rejectsSuccessfulResponseWithoutOutput() {
        KisCashOrderClient cashOrderClient = mock(KisCashOrderClient.class);
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        stubResponse(
                cashOrderClient,
                tokenProvider,
                new KisCashOrderResponse("0", "APBK0013", "Success.", null)
        );
        when(clock.instant()).thenReturn(SUBMITTED_AT);
        KisBrokerOrderProvider provider = provider(
                cashOrderClient,
                tokenProvider,
                clock
        );

        assertThatNullPointerException()
                .isThrownBy(() -> provider.submit(request()))
                .withMessage("KIS cash order output must not be null.");
    }

    @Test
    void rejectsSuccessfulResponseWithoutKisOrderIdentifiers() {
        KisCashOrderClient cashOrderClient = mock(KisCashOrderClient.class);
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        stubResponse(
                cashOrderClient,
                tokenProvider,
                new KisCashOrderResponse(
                        "0",
                        "APBK0013",
                        "Success.",
                        new KisCashOrderOutput(" ", " ", "091530")
                )
        );
        when(clock.instant()).thenReturn(SUBMITTED_AT);
        KisBrokerOrderProvider provider = provider(
                cashOrderClient,
                tokenProvider,
                clock
        );

        assertThatThrownBy(() -> provider.submit(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "KIS cash order response orderNumber must not be blank."
                );
    }

    @Test
    void rejectsSuccessfulResponseWithoutOrderOrganizationNumber() {
        KisCashOrderClient cashOrderClient = mock(KisCashOrderClient.class);
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        stubResponse(
                cashOrderClient,
                tokenProvider,
                new KisCashOrderResponse(
                        "0",
                        "APBK0013",
                        "Success.",
                        new KisCashOrderOutput(" ", "0000123456", "091530")
                )
        );
        when(clock.instant()).thenReturn(SUBMITTED_AT);
        KisBrokerOrderProvider provider = provider(
                cashOrderClient,
                tokenProvider,
                clock
        );

        assertThatThrownBy(() -> provider.submit(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "KIS cash order response orderOrganizationNumber "
                                + "must not be blank."
                );
    }

    @Test
    void propagatesTechnicalFailureWithoutMappingItToRejection() {
        KisCashOrderClient cashOrderClient = mock(KisCashOrderClient.class);
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        BrokerOrderRequest request = request();
        RestClientException failure = new RestClientException("Request timed out.");
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(cashOrderClient.submitLimitOrder(
                request.side(),
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                request.symbol(),
                request.quantity(),
                request.limitPriceKrw(),
                ACCESS_TOKEN
        )).thenThrow(failure);
        KisBrokerOrderProvider provider = provider(
                cashOrderClient,
                tokenProvider,
                clock
        );

        assertThatThrownBy(() -> provider.submit(request))
                .isSameAs(failure);
        verifyNoInteractions(clock);
    }

    private void stubResponse(
            KisCashOrderClient cashOrderClient,
            KisTokenProvider tokenProvider,
            KisCashOrderResponse response
    ) {
        BrokerOrderRequest request = request();
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(cashOrderClient.submitLimitOrder(
                request.side(),
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                request.symbol(),
                request.quantity(),
                request.limitPriceKrw(),
                ACCESS_TOKEN
        )).thenReturn(response);
    }

    private KisBrokerOrderProvider provider(
            KisCashOrderClient cashOrderClient,
            KisTokenProvider tokenProvider,
            Clock clock
    ) {
        return new KisBrokerOrderProvider(
                cashOrderClient,
                tokenProvider,
                properties(),
                clock
        );
    }

    private BrokerOrderRequest request() {
        return new BrokerOrderRequest(
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L
        );
    }

    private KisCashOrderResponse successfulResponse() {
        return new KisCashOrderResponse(
                "0",
                "APBK0013",
                "Order request completed.",
                new KisCashOrderOutput(
                        "06010",
                        "0000123456",
                        "091530"
                )
        );
    }

    private KisProperties properties() {
        return new KisProperties(
                true,
                URI.create("https://openapivts.koreainvestment.com:29443"),
                "test-app-key",
                "test-app-secret",
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                Duration.ofMinutes(1),
                10,
                10,
                10,
                10,
                Duration.ofSeconds(1),
                KisDailyPriceHistoryMarket.INTEGRATED
        );
    }
}
