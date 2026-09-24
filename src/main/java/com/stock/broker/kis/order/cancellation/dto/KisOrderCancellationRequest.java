package com.stock.broker.kis.order.cancellation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.broker.order.BrokerOrderReference;

import java.util.Objects;

public record KisOrderCancellationRequest(
        @JsonProperty("CANO") String accountNumber,
        @JsonProperty("ACNT_PRDT_CD") String accountProductCode,
        @JsonProperty("KRX_FWDG_ORD_ORGNO") String orderOrganizationNumber,
        @JsonProperty("ORGN_ODNO") String originalOrderNumber,
        @JsonProperty("ORD_DVSN") String orderDivision,
        @JsonProperty("RVSE_CNCL_DVSN_CD") String revisionCancellationDivision,
        @JsonProperty("ORD_QTY") String quantity,
        @JsonProperty("ORD_UNPR") String unitPrice,
        @JsonProperty("QTY_ALL_ORD_YN") String allRemainingQuantity,
        @JsonProperty("EXCG_ID_DVSN_CD") String exchangeId,
        @JsonProperty("CNDT_PRIC") String conditionPrice
) {
    private static final String CANCELLATION_DIVISION = "02";
    private static final String ALL_REMAINING_QUANTITY = "Y";

    public static KisOrderCancellationRequest allRemaining(
            String accountNumber,
            String accountProductCode,
            BrokerOrderReference originalReference,
            String orderDivision,
            long cancelableQuantity,
            long originalOrderPriceKrw,
            String exchangeId
    ) {
        Objects.requireNonNull(
                originalReference,
                "originalReference must not be null."
        );
        if (cancelableQuantity <= 0) {
            throw new IllegalArgumentException(
                    "cancelableQuantity must be positive."
            );
        }
        if (originalOrderPriceKrw < 0) {
            throw new IllegalArgumentException(
                    "originalOrderPriceKrw must not be negative."
            );
        }

        return new KisOrderCancellationRequest(
                requireText(accountNumber, "accountNumber"),
                requireText(accountProductCode, "accountProductCode"),
                requireText(
                        originalReference.organizationNumber(),
                        "originalReference.organizationNumber"
                ),
                originalReference.orderId(),
                requireText(orderDivision, "orderDivision"),
                CANCELLATION_DIVISION,
                Long.toString(cancelableQuantity),
                Long.toString(originalOrderPriceKrw),
                ALL_REMAINING_QUANTITY,
                requireText(exchangeId, "exchangeId"),
                ""
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
