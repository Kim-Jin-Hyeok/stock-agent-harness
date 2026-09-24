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
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class KisBrokerOrderCancellationProvider
        implements BrokerOrderCancellationProvider {
    private final KisCancelableOrderInquiryClient cancelableOrderInquiryClient;
    private final KisOrderCancellationClient orderCancellationClient;
    private final KisTokenProvider tokenProvider;
    private final KisProperties properties;
    private final Clock clock;

    public KisBrokerOrderCancellationProvider(
            KisCancelableOrderInquiryClient cancelableOrderInquiryClient,
            KisOrderCancellationClient orderCancellationClient,
            KisTokenProvider tokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        this.cancelableOrderInquiryClient = Objects.requireNonNull(
                cancelableOrderInquiryClient,
                "cancelableOrderInquiryClient must not be null."
        );
        this.orderCancellationClient = Objects.requireNonNull(
                orderCancellationClient,
                "orderCancellationClient must not be null."
        );
        this.tokenProvider = Objects.requireNonNull(
                tokenProvider,
                "tokenProvider must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Override
    public BrokerOrderCancellationSubmission cancelRemaining(
            BrokerOrderCancellationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        BrokerOrderReference reference = request.reference();
        requireText(
                reference.organizationNumber(),
                "reference.organizationNumber"
        );
        String accessToken = tokenProvider.getAccessToken();
        KisCancelableOrderResponse inquiryResponse = Objects.requireNonNull(
                cancelableOrderInquiryClient.getCancelableOrders(
                        properties.accountNumber(),
                        properties.accountProductCode(),
                        accessToken
                ),
                "KIS cancelable order inquiry response must not be null."
        );

        if (!inquiryResponse.isSuccessful()) {
            throw new IllegalStateException(
                    "KIS cancelable order inquiry response was not successful. "
                            + "messageCode=" + inquiryResponse.messageCode()
            );
        }

        List<KisCancelableOrderOutput> outputs = Objects.requireNonNull(
                inquiryResponse.output(),
                "KIS cancelable order inquiry output must not be null."
        );
        KisCancelableOrderOutput matchedOutput = outputs.stream()
                .filter(output -> matches(reference, output))
                .findFirst()
                .orElse(null);

        if (matchedOutput == null) {
            return rejected(
                    clock.instant(),
                    "KIS cancelable order was not found. orderId="
                            + reference.orderId()
                            + ", organizationNumber="
                            + reference.organizationNumber()
            );
        }

        long cancelableQuantity = parsePositiveLong(
                matchedOutput.cancelableQuantity(),
                "cancelableQuantity"
        );
        long orderPriceKrw = parseNonNegativeLong(
                matchedOutput.orderPrice(),
                "orderPrice"
        );
        KisOrderCancellationResponse cancellationResponse =
                Objects.requireNonNull(
                        orderCancellationClient.cancelAllRemaining(
                                properties.accountNumber(),
                                properties.accountProductCode(),
                                reference,
                                requireResponseText(
                                        matchedOutput.orderDivisionCode(),
                                        "KIS cancelable order",
                                        "orderDivisionCode"
                                ),
                                cancelableQuantity,
                                orderPriceKrw,
                                requireResponseText(
                                        matchedOutput.exchangeId(),
                                        "KIS cancelable order",
                                        "exchangeId"
                                ),
                                accessToken
                        ),
                        "KIS order cancellation response must not be null."
                );
        Instant submittedAt = clock.instant();

        if (!cancellationResponse.isSuccessful()) {
            return rejected(
                    submittedAt,
                    "KIS order cancellation was rejected. messageCode="
                            + cancellationResponse.messageCode()
                            + ", message="
                            + cancellationResponse.message()
            );
        }

        KisOrderCancellationOutput output = Objects.requireNonNull(
                cancellationResponse.output(),
                "KIS order cancellation output must not be null."
        );
        BrokerOrderReference cancellationReference = new BrokerOrderReference(
                requireResponseText(
                        output.orderNumber(),
                        "KIS order cancellation response",
                        "orderNumber"
                ),
                requireResponseText(
                        output.orderOrganizationNumber(),
                        "KIS order cancellation response",
                        "orderOrganizationNumber"
                )
        );

        return new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                cancellationReference,
                submittedAt,
                null
        );
    }

    private boolean matches(
            BrokerOrderReference reference,
            KisCancelableOrderOutput output
    ) {
        return output != null
                && reference.orderId().equals(output.orderNumber())
                && reference.organizationNumber().equals(
                output.orderOrganizationNumber()
        );
    }

    private BrokerOrderCancellationSubmission rejected(
            Instant submittedAt,
            String reason
    ) {
        return new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.REJECTED,
                null,
                submittedAt,
                reason
        );
    }

    private long parsePositiveLong(String value, String fieldName) {
        long parsedValue = parseNonNegativeLong(value, fieldName);
        if (parsedValue == 0) {
            throw new IllegalStateException(
                    "KIS cancelable order " + fieldName + " must be positive."
            );
        }
        return parsedValue;
    }

    private long parseNonNegativeLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "KIS cancelable order " + fieldName
                            + " must not be blank."
            );
        }

        final long parsedValue;
        try {
            parsedValue = new BigDecimal(value).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException(
                    "KIS cancelable order " + fieldName
                            + " must be an integer number.",
                    exception
            );
        }
        if (parsedValue < 0) {
            throw new IllegalStateException(
                    "KIS cancelable order " + fieldName
                            + " must not be negative."
            );
        }
        return parsedValue;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }

    private String requireResponseText(
            String value,
            String source,
            String name
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    source + " " + name + " must not be blank."
            );
        }
        return value;
    }
}
