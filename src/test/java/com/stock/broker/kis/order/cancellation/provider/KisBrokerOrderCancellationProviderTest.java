package com.stock.broker.kis.order.cancellation.provider;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;
import com.stock.broker.kis.order.cancellation.KisOrderCancellationClient;
import com.stock.broker.kis.order.cancellation.dto.KisOrderCancellationOutput;
import com.stock.broker.kis.order.cancellation.dto.KisOrderCancellationResponse;
import com.stock.broker.kis.order.cancellation.inquiry.KisCancelableOrderInquiryClient;
import com.stock.broker.kis.order.cancellation.inquiry.dto.KisCancelableOrderOutput;
import com.stock.broker.kis.order.cancellation.inquiry.dto.KisCancelableOrderResponse;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.cancellation.BrokerOrderCancellationRequest;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmissionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisBrokerOrderCancellationProviderTest {
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final BrokerOrderReference ORIGINAL_REFERENCE =
            new BrokerOrderReference("0000123456", "06010");
    private static final Instant SUBMITTED_AT =
            Instant.parse("2026-09-24T01:00:00Z");

    @Test
    void cancelsMatchingOrderFromMergedInquiryOutput() {
        Dependencies dependencies = dependencies();
        KisCancelableOrderOutput matchedOutput = cancelableOutput(
                ORIGINAL_REFERENCE,
                "7",
                "70000.0000"
        );
        stubInquiry(
                dependencies,
                successfulInquiry(List.of(
                        cancelableOutput(
                                new BrokerOrderReference(
                                        "0000999999",
                                        "06010"
                                ),
                                "2",
                                "65000"
                        ),
                        matchedOutput
                ))
        );
        when(dependencies.cancellationClient().cancelAllRemaining(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORIGINAL_REFERENCE,
                "00",
                7L,
                70_000L,
                "KRX",
                ACCESS_TOKEN
        )).thenReturn(successfulCancellation());
        when(dependencies.clock().instant()).thenReturn(SUBMITTED_AT);

        BrokerOrderCancellationSubmission submission =
                dependencies.provider().cancelRemaining(request());

        assertThat(submission.status())
                .isEqualTo(BrokerOrderCancellationSubmissionStatus.ACCEPTED);
        assertThat(submission.cancellationReference()).isEqualTo(
                new BrokerOrderReference("0000123457", "06010")
        );
        assertThat(submission.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(submission.reason()).isNull();
        verify(dependencies.tokenProvider()).getAccessToken();
        verify(dependencies.inquiryClient()).getCancelableOrders(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        );
        verify(dependencies.cancellationClient()).cancelAllRemaining(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORIGINAL_REFERENCE,
                "00",
                7L,
                70_000L,
                "KRX",
                ACCESS_TOKEN
        );
    }

    @Test
    void mapsMissingCancelableOrderToRejectedSubmission() {
        Dependencies dependencies = dependencies();
        stubInquiry(
                dependencies,
                successfulInquiry(List.of(cancelableOutput(
                        new BrokerOrderReference("0000999999", "06010"),
                        "2",
                        "65000"
                )))
        );
        when(dependencies.clock().instant()).thenReturn(SUBMITTED_AT);

        BrokerOrderCancellationSubmission submission =
                dependencies.provider().cancelRemaining(request());

        assertThat(submission.status())
                .isEqualTo(BrokerOrderCancellationSubmissionStatus.REJECTED);
        assertThat(submission.cancellationReference()).isNull();
        assertThat(submission.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(submission.reason()).isEqualTo(
                "KIS cancelable order was not found. "
                        + "orderId=0000123456, organizationNumber=06010"
        );
        verifyNoInteractions(dependencies.cancellationClient());
    }

    @Test
    void rejectsInquiryBusinessFailure() {
        Dependencies dependencies = dependencies();
        stubInquiry(
                dependencies,
                new KisCancelableOrderResponse(
                        "1",
                        "EGW00123",
                        "Request failed.",
                        List.of(),
                        "",
                        ""
                )
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> dependencies.provider()
                        .cancelRemaining(request()))
                .withMessage(
                        "KIS cancelable order inquiry response was not "
                                + "successful. messageCode=EGW00123"
                );
        verifyNoInteractions(dependencies.cancellationClient());
        verifyNoInteractions(dependencies.clock());
    }

    @Test
    void mapsCancellationBusinessFailureToRejectedSubmission() {
        Dependencies dependencies = dependencies();
        stubMatchingInquiry(dependencies, "7", "70000");
        when(dependencies.cancellationClient().cancelAllRemaining(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORIGINAL_REFERENCE,
                "00",
                7L,
                70_000L,
                "KRX",
                ACCESS_TOKEN
        )).thenReturn(new KisOrderCancellationResponse(
                "1",
                "APBK0918",
                "Cancellation was rejected.",
                null
        ));
        when(dependencies.clock().instant()).thenReturn(SUBMITTED_AT);

        BrokerOrderCancellationSubmission submission =
                dependencies.provider().cancelRemaining(request());

        assertThat(submission.status())
                .isEqualTo(BrokerOrderCancellationSubmissionStatus.REJECTED);
        assertThat(submission.reason()).isEqualTo(
                "KIS order cancellation was rejected. "
                        + "messageCode=APBK0918, "
                        + "message=Cancellation was rejected."
        );
    }

    @Test
    void rejectsNonPositiveCancelableQuantity() {
        Dependencies dependencies = dependencies();
        stubMatchingInquiry(dependencies, "0", "70000");

        assertThatIllegalStateException()
                .isThrownBy(() -> dependencies.provider()
                        .cancelRemaining(request()))
                .withMessage(
                        "KIS cancelable order cancelableQuantity "
                                + "must be positive."
                );
        verifyNoInteractions(dependencies.cancellationClient());
        verifyNoInteractions(dependencies.clock());
    }

    @Test
    void rejectsReferenceWithoutOrganizationNumber() {
        Dependencies dependencies = dependencies();
        BrokerOrderCancellationRequest request =
                new BrokerOrderCancellationRequest(
                        new BrokerOrderReference("0000123456", null)
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> dependencies.provider()
                        .cancelRemaining(request))
                .withMessage(
                        "reference.organizationNumber must not be blank."
                );
        verifyNoInteractions(dependencies.tokenProvider());
    }

    @Test
    void rejectsSuccessfulCancellationWithoutOutput() {
        Dependencies dependencies = dependencies();
        stubMatchingInquiry(dependencies, "7", "70000");
        when(dependencies.cancellationClient().cancelAllRemaining(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORIGINAL_REFERENCE,
                "00",
                7L,
                70_000L,
                "KRX",
                ACCESS_TOKEN
        )).thenReturn(new KisOrderCancellationResponse(
                "0",
                "APBK0013",
                "Cancellation request completed.",
                null
        ));
        when(dependencies.clock().instant()).thenReturn(SUBMITTED_AT);

        assertThatNullPointerException()
                .isThrownBy(() -> dependencies.provider()
                        .cancelRemaining(request()))
                .withMessage(
                        "KIS order cancellation output must not be null."
                );
    }

    @Test
    void rejectsSuccessfulCancellationWithoutOrderNumber() {
        Dependencies dependencies = dependencies();
        stubMatchingInquiry(dependencies, "7", "70000");
        when(dependencies.cancellationClient().cancelAllRemaining(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORIGINAL_REFERENCE,
                "00",
                7L,
                70_000L,
                "KRX",
                ACCESS_TOKEN
        )).thenReturn(new KisOrderCancellationResponse(
                "0",
                "APBK0013",
                "Cancellation request completed.",
                new KisOrderCancellationOutput("06010", " ", "100001")
        ));
        when(dependencies.clock().instant()).thenReturn(SUBMITTED_AT);

        assertThatIllegalStateException()
                .isThrownBy(() -> dependencies.provider()
                        .cancelRemaining(request()))
                .withMessage(
                        "KIS order cancellation response orderNumber "
                                + "must not be blank."
                );
    }

    @Test
    void propagatesCancellationTechnicalFailure() {
        Dependencies dependencies = dependencies();
        stubMatchingInquiry(dependencies, "7", "70000");
        RestClientException failure = new RestClientException(
                "Request timed out."
        );
        when(dependencies.cancellationClient().cancelAllRemaining(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORIGINAL_REFERENCE,
                "00",
                7L,
                70_000L,
                "KRX",
                ACCESS_TOKEN
        )).thenThrow(failure);

        assertThatThrownBy(() -> dependencies.provider()
                .cancelRemaining(request()))
                .isSameAs(failure);
        verifyNoInteractions(dependencies.clock());
    }

    private void stubMatchingInquiry(
            Dependencies dependencies,
            String cancelableQuantity,
            String orderPrice
    ) {
        stubInquiry(
                dependencies,
                successfulInquiry(List.of(cancelableOutput(
                        ORIGINAL_REFERENCE,
                        cancelableQuantity,
                        orderPrice
                )))
        );
    }

    private void stubInquiry(
            Dependencies dependencies,
            KisCancelableOrderResponse response
    ) {
        when(dependencies.tokenProvider().getAccessToken())
                .thenReturn(ACCESS_TOKEN);
        when(dependencies.inquiryClient().getCancelableOrders(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        )).thenReturn(response);
    }

    private KisCancelableOrderResponse successfulInquiry(
            List<KisCancelableOrderOutput> outputs
    ) {
        return new KisCancelableOrderResponse(
                "0",
                "KIOK0560",
                "Request completed successfully.",
                outputs,
                "",
                ""
        );
    }

    private KisCancelableOrderOutput cancelableOutput(
            BrokerOrderReference reference,
            String cancelableQuantity,
            String orderPrice
    ) {
        return new KisCancelableOrderOutput(
                reference.organizationNumber(),
                reference.orderId(),
                "",
                "005930",
                "00",
                "10",
                orderPrice,
                "3",
                cancelableQuantity,
                "KRX"
        );
    }

    private KisOrderCancellationResponse successfulCancellation() {
        return new KisOrderCancellationResponse(
                "0",
                "APBK0013",
                "Cancellation request completed.",
                new KisOrderCancellationOutput(
                        "06010",
                        "0000123457",
                        "100001"
                )
        );
    }

    private BrokerOrderCancellationRequest request() {
        return new BrokerOrderCancellationRequest(ORIGINAL_REFERENCE);
    }

    private Dependencies dependencies() {
        KisCancelableOrderInquiryClient inquiryClient = mock(
                KisCancelableOrderInquiryClient.class
        );
        KisOrderCancellationClient cancellationClient = mock(
                KisOrderCancellationClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        KisBrokerOrderCancellationProvider provider =
                new KisBrokerOrderCancellationProvider(
                        inquiryClient,
                        cancellationClient,
                        tokenProvider,
                        properties(),
                        clock
                );
        return new Dependencies(
                inquiryClient,
                cancellationClient,
                tokenProvider,
                clock,
                provider
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
                10
        );
    }

    private record Dependencies(
            KisCancelableOrderInquiryClient inquiryClient,
            KisOrderCancellationClient cancellationClient,
            KisTokenProvider tokenProvider,
            Clock clock,
            KisBrokerOrderCancellationProvider provider
    ) {
    }
}
