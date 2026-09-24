package com.stock.broker.kis.order.inquiry.provider;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryMarket;
import com.stock.broker.kis.order.inquiry.KisOrderInquiryClient;
import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryOutput;
import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryResponse;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.inquiry.BrokerOrderInquiryRequest;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;
import com.stock.broker.order.inquiry.BrokerOrderInquiryStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisBrokerOrderInquiryProviderTest {
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String SYMBOL = "005930";
    private static final BrokerOrderReference REFERENCE =
            new BrokerOrderReference("0000123456", "06010");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-22T15:30:00Z");
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-23T00:31:00Z");
    private static final LocalDate KOREA_ORDER_DATE =
            LocalDate.of(2026, 9, 23);

    @Test
    void getsTokenAndMapsPartialExecutionToFoundResult() {
        KisOrderInquiryClient inquiryClient = mock(
                KisOrderInquiryClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        stubResponse(
                inquiryClient,
                tokenProvider,
                response(output("3", "69900.0000", "N"))
        );
        when(clock.instant()).thenReturn(OBSERVED_AT);
        KisBrokerOrderInquiryProvider provider = provider(
                inquiryClient,
                tokenProvider,
                clock
        );

        BrokerOrderInquiryResult result = provider.inquire(request());

        assertThat(result.status()).isEqualTo(BrokerOrderInquiryStatus.FOUND);
        assertThat(result.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(result.snapshot().reference()).isEqualTo(REFERENCE);
        assertThat(result.snapshot().requestedQuantity()).isEqualTo(10L);
        assertThat(result.snapshot().cumulativeFilledQuantity()).isEqualTo(3L);
        assertThat(result.snapshot().cumulativeFilledAmountKrw())
                .isEqualTo(209_700L);
        assertThat(result.snapshot().averageFilledPriceKrw())
                .isEqualTo(69_900L);
        assertThat(result.snapshot().status())
                .isEqualTo(BrokerOrderStatus.PARTIALLY_FILLED);
        verify(tokenProvider).getAccessToken();
        verify(inquiryClient).getOrderExecutions(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                KOREA_ORDER_DATE,
                KOREA_ORDER_DATE,
                REFERENCE,
                SYMBOL,
                ACCESS_TOKEN
        );
    }

    @Test
    void mapsMissingMatchingOrderToNotFoundResult() {
        KisOrderInquiryClient inquiryClient = mock(
                KisOrderInquiryClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        KisOrderInquiryOutput otherOrder = new KisOrderInquiryOutput(
                "20260923",
                "06010",
                "0000999999",
                "",
                "02",
                SYMBOL,
                "10",
                "70000",
                "0",
                "0.0000",
                "0",
                "N",
                "10"
        );
        stubResponse(
                inquiryClient,
                tokenProvider,
                response(otherOrder)
        );
        when(clock.instant()).thenReturn(OBSERVED_AT);
        KisBrokerOrderInquiryProvider provider = provider(
                inquiryClient,
                tokenProvider,
                clock
        );

        BrokerOrderInquiryResult result = provider.inquire(request());

        assertThat(result.status())
                .isEqualTo(BrokerOrderInquiryStatus.NOT_FOUND);
        assertThat(result.snapshot()).isNull();
        assertThat(result.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void mapsUnfilledOrderToPending() {
        assertThat(inquire(output("0", "0.0000", "N"))
                .snapshot().status()).isEqualTo(BrokerOrderStatus.PENDING);
    }

    @Test
    void mapsFullyFilledOrderToFilled() {
        assertThat(inquire(output("10", "69900.0000", "N"))
                .snapshot().status()).isEqualTo(BrokerOrderStatus.FILLED);
    }

    @Test
    void mapsCanceledOrderToCanceled() {
        assertThat(inquire(output("3", "69900.0000", "Y"))
                .snapshot().status()).isEqualTo(BrokerOrderStatus.CANCELED);
    }

    @Test
    void rejectsKisBusinessFailureInsteadOfMappingItToNotFound() {
        KisOrderInquiryClient inquiryClient = mock(
                KisOrderInquiryClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        stubResponse(
                inquiryClient,
                tokenProvider,
                new KisOrderInquiryResponse(
                        "1",
                        "EGW00123",
                        "Request failed.",
                        List.of(),
                        "",
                        ""
                )
        );
        KisBrokerOrderInquiryProvider provider = provider(
                inquiryClient,
                tokenProvider,
                clock
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.inquire(request()))
                .withMessage(
                        "KIS order inquiry response was not successful. "
                                + "messageCode=EGW00123"
                );
    }

    @Test
    void rejectsInvalidQuantity() {
        KisBrokerOrderInquiryProvider provider = stubbedProvider(
                output("not-a-number", "69900.0000", "N")
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.inquire(request()))
                .withMessage(
                        "KIS order inquiry cumulativeFilledQuantity "
                                + "must be an integer number."
                );
    }

    @Test
    void rejectsInvalidCumulativeFilledAmount() {
        KisBrokerOrderInquiryProvider provider = stubbedProvider(
                output("3", "69900.0000", "not-a-number", "N")
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.inquire(request()))
                .withMessage(
                        "KIS order inquiry cumulativeFilledAmount "
                                + "must be an integer number."
                );
    }

    @Test
    void rejectsReferenceWithoutOrganizationNumber() {
        KisOrderInquiryClient inquiryClient = mock(
                KisOrderInquiryClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        KisBrokerOrderInquiryProvider provider = provider(
                inquiryClient,
                tokenProvider,
                clock
        );
        BrokerOrderInquiryRequest request = new BrokerOrderInquiryRequest(
                new BrokerOrderReference("0000123456", null),
                SYMBOL,
                SUBMITTED_AT
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.inquire(request))
                .withMessage(
                        "reference.organizationNumber must not be blank."
                );
    }

    private BrokerOrderInquiryResult inquire(KisOrderInquiryOutput output) {
        return stubbedProvider(output).inquire(request());
    }

    private KisBrokerOrderInquiryProvider stubbedProvider(
            KisOrderInquiryOutput output
    ) {
        KisOrderInquiryClient inquiryClient = mock(
                KisOrderInquiryClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        stubResponse(inquiryClient, tokenProvider, response(output));
        when(clock.instant()).thenReturn(OBSERVED_AT);
        return provider(inquiryClient, tokenProvider, clock);
    }

    private void stubResponse(
            KisOrderInquiryClient inquiryClient,
            KisTokenProvider tokenProvider,
            KisOrderInquiryResponse response
    ) {
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(inquiryClient.getOrderExecutions(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                KOREA_ORDER_DATE,
                KOREA_ORDER_DATE,
                REFERENCE,
                SYMBOL,
                ACCESS_TOKEN
        )).thenReturn(response);
    }

    private KisBrokerOrderInquiryProvider provider(
            KisOrderInquiryClient inquiryClient,
            KisTokenProvider tokenProvider,
            Clock clock
    ) {
        return new KisBrokerOrderInquiryProvider(
                inquiryClient,
                tokenProvider,
                properties(),
                clock
        );
    }

    private BrokerOrderInquiryRequest request() {
        return new BrokerOrderInquiryRequest(
                REFERENCE,
                SYMBOL,
                SUBMITTED_AT
        );
    }

    private KisOrderInquiryResponse response(KisOrderInquiryOutput output) {
        return new KisOrderInquiryResponse(
                "0",
                "KIOK0560",
                "Request completed successfully.",
                List.of(output),
                "",
                ""
        );
    }

    private KisOrderInquiryOutput output(
            String cumulativeFilledQuantity,
            String averageFilledPrice,
            String canceled
    ) {
        String cumulativeFilledAmount = switch (cumulativeFilledQuantity) {
            case "0" -> "0";
            case "10" -> "699000";
            default -> "209700";
        };
        return output(
                cumulativeFilledQuantity,
                averageFilledPrice,
                cumulativeFilledAmount,
                canceled
        );
    }

    private KisOrderInquiryOutput output(
            String cumulativeFilledQuantity,
            String averageFilledPrice,
            String cumulativeFilledAmount,
            String canceled
    ) {
        return new KisOrderInquiryOutput(
                "20260923",
                REFERENCE.organizationNumber(),
                REFERENCE.orderId(),
                "",
                "02",
                SYMBOL,
                "10",
                "70000",
                cumulativeFilledQuantity,
                averageFilledPrice,
                cumulativeFilledAmount,
                canceled,
                "7"
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
                KisDailyPriceHistoryMarket.INTEGRATED
        );
    }
}
