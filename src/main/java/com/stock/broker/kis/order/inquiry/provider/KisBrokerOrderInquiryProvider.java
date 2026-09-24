package com.stock.broker.kis.order.inquiry.provider;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;
import com.stock.broker.kis.order.inquiry.KisOrderInquiryClient;
import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryOutput;
import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryResponse;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.inquiry.BrokerOrderExecutionSnapshot;
import com.stock.broker.order.inquiry.BrokerOrderInquiryRequest;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;
import com.stock.broker.order.inquiry.provider.BrokerOrderInquiryProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public class KisBrokerOrderInquiryProvider
        implements BrokerOrderInquiryProvider {
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final KisOrderInquiryClient orderInquiryClient;
    private final KisTokenProvider tokenProvider;
    private final KisProperties properties;
    private final Clock clock;

    public KisBrokerOrderInquiryProvider(
            KisOrderInquiryClient orderInquiryClient,
            KisTokenProvider tokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        this.orderInquiryClient = Objects.requireNonNull(
                orderInquiryClient,
                "orderInquiryClient must not be null."
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
    public BrokerOrderInquiryResult inquire(BrokerOrderInquiryRequest request) {
        Objects.requireNonNull(request, "request must not be null.");

        BrokerOrderReference reference = request.reference();
        requireText(
                reference.organizationNumber(),
                "reference.organizationNumber"
        );
        LocalDate orderDate = request.submittedAt()
                .atZone(KOREA_ZONE_ID)
                .toLocalDate();
        String accessToken = tokenProvider.getAccessToken();
        KisOrderInquiryResponse response = Objects.requireNonNull(
                orderInquiryClient.getOrderExecutions(
                        properties.accountNumber(),
                        properties.accountProductCode(),
                        orderDate,
                        orderDate,
                        reference,
                        request.symbol(),
                        accessToken
                ),
                "KIS order inquiry response must not be null."
        );

        if (!response.isSuccessful()) {
            throw new IllegalStateException(
                    "KIS order inquiry response was not successful. "
                            + "messageCode=" + response.messageCode()
            );
        }

        List<KisOrderInquiryOutput> outputs = Objects.requireNonNull(
                response.output1(),
                "KIS order inquiry output1 must not be null."
        );
        KisOrderInquiryOutput matchedOutput = outputs.stream()
                .filter(output -> matches(request, output))
                .findFirst()
                .orElse(null);

        if (matchedOutput == null) {
            return BrokerOrderInquiryResult.notFound(clock.instant());
        }
        return BrokerOrderInquiryResult.found(
                toSnapshot(reference, matchedOutput),
                clock.instant()
        );
    }

    private boolean matches(
            BrokerOrderInquiryRequest request,
            KisOrderInquiryOutput output
    ) {
        return output != null
                && request.reference().orderId().equals(output.orderNumber())
                && request.reference().organizationNumber().equals(
                output.orderOrganizationNumber()
        )
                && request.symbol().equals(output.symbol());
    }

    private BrokerOrderExecutionSnapshot toSnapshot(
            BrokerOrderReference reference,
            KisOrderInquiryOutput output
    ) {
        long requestedQuantity = parseNonNegativeLong(
                output.requestedQuantity(),
                "requestedQuantity"
        );
        if (requestedQuantity == 0) {
            throw new IllegalStateException(
                    "KIS order inquiry requestedQuantity must be positive."
            );
        }
        long cumulativeFilledQuantity = parseNonNegativeLong(
                output.cumulativeFilledQuantity(),
                "cumulativeFilledQuantity"
        );
        long cumulativeFilledAmountKrw = parseNonNegativeLong(
                output.cumulativeFilledAmount(),
                "cumulativeFilledAmount"
        );
        Long averageFilledPriceKrw = cumulativeFilledQuantity == 0
                ? null
                : parsePositiveLong(
                output.averageFilledPrice(),
                "averageFilledPrice"
        );
        BrokerOrderStatus status = resolveStatus(
                output.canceled(),
                requestedQuantity,
                cumulativeFilledQuantity
        );

        return new BrokerOrderExecutionSnapshot(
                reference,
                requestedQuantity,
                cumulativeFilledQuantity,
                cumulativeFilledAmountKrw,
                averageFilledPriceKrw,
                status,
                null
        );
    }

    private BrokerOrderStatus resolveStatus(
            String canceled,
            long requestedQuantity,
            long cumulativeFilledQuantity
    ) {
        if (cumulativeFilledQuantity > requestedQuantity) {
            throw new IllegalStateException(
                    "KIS order inquiry cumulativeFilledQuantity must not exceed "
                            + "requestedQuantity."
            );
        }

        if ("Y".equalsIgnoreCase(canceled)) {
            if (cumulativeFilledQuantity == requestedQuantity) {
                throw new IllegalStateException(
                        "KIS order inquiry canceled order must have an unfilled "
                                + "quantity."
                );
            }
            return BrokerOrderStatus.CANCELED;
        }
        if (!"N".equalsIgnoreCase(canceled)) {
            throw new IllegalStateException(
                    "KIS order inquiry canceled must be Y or N."
            );
        }
        if (cumulativeFilledQuantity == 0) {
            return BrokerOrderStatus.PENDING;
        }
        if (cumulativeFilledQuantity < requestedQuantity) {
            return BrokerOrderStatus.PARTIALLY_FILLED;
        }
        return BrokerOrderStatus.FILLED;
    }

    private long parsePositiveLong(String value, String fieldName) {
        long parsedValue = parseNonNegativeLong(value, fieldName);
        if (parsedValue == 0) {
            throw new IllegalStateException(
                    "KIS order inquiry " + fieldName + " must be positive."
            );
        }
        return parsedValue;
    }

    private long parseNonNegativeLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "KIS order inquiry " + fieldName + " must not be blank."
            );
        }

        final long parsedValue;
        try {
            parsedValue = new BigDecimal(value).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException(
                    "KIS order inquiry " + fieldName
                            + " must be an integer number.",
                    exception
            );
        }
        if (parsedValue < 0) {
            throw new IllegalStateException(
                    "KIS order inquiry " + fieldName
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
}
