package com.stock.broker.kis.order.provider;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;
import com.stock.broker.kis.order.KisCashOrderClient;
import com.stock.broker.kis.order.dto.KisCashOrderOutput;
import com.stock.broker.kis.order.dto.KisCashOrderResponse;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderSubmission;
import com.stock.broker.order.BrokerOrderSubmissionStatus;
import com.stock.broker.order.provider.BrokerOrderProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class KisBrokerOrderProvider implements BrokerOrderProvider {
    private final KisCashOrderClient cashOrderClient;
    private final KisTokenProvider tokenProvider;
    private final KisProperties properties;
    private final Clock clock;

    public KisBrokerOrderProvider(
            KisCashOrderClient cashOrderClient,
            KisTokenProvider tokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        this.cashOrderClient = Objects.requireNonNull(
                cashOrderClient,
                "cashOrderClient must not be null."
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
    public BrokerOrderSubmission submit(BrokerOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null.");

        String accessToken = tokenProvider.getAccessToken();
        KisCashOrderResponse response = Objects.requireNonNull(
                cashOrderClient.submitLimitOrder(
                        request.side(),
                        properties.accountNumber(),
                        properties.accountProductCode(),
                        request.symbol(),
                        request.quantity(),
                        request.limitPriceKrw(),
                        accessToken
                ),
                "KIS cash order response must not be null."
        );
        Instant submittedAt = clock.instant();

        if (!response.isSuccessful()) {
            return new BrokerOrderSubmission(
                    BrokerOrderSubmissionStatus.REJECTED,
                    null,
                    submittedAt,
                    "KIS order was rejected. messageCode="
                            + response.messageCode()
                            + ", message="
                            + response.message()
            );
        }

        KisCashOrderOutput output = Objects.requireNonNull(
                response.output(),
                "KIS cash order output must not be null."
        );
        BrokerOrderReference reference = new BrokerOrderReference(
                requireResponseText(output.orderNumber(), "orderNumber"),
                requireResponseText(
                        output.orderOrganizationNumber(),
                        "orderOrganizationNumber"
                )
        );

        return new BrokerOrderSubmission(
                BrokerOrderSubmissionStatus.ACCEPTED,
                reference,
                submittedAt,
                null
        );
    }

    private String requireResponseText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "KIS cash order response " + name + " must not be blank."
            );
        }
        return value;
    }
}
