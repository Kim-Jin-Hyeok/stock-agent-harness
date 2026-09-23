package com.stock.broker.kis.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.broker.order.BrokerOrderSide;

import java.util.Objects;

public record KisCashOrderRequest(
        @JsonProperty("CANO") String accountNumber,
        @JsonProperty("ACNT_PRDT_CD") String accountProductCode,
        @JsonProperty("PDNO") String symbol,
        @JsonProperty("SLL_TYPE") String sellType,
        @JsonProperty("ORD_DVSN") String orderDivision,
        @JsonProperty("ORD_QTY") String quantity,
        @JsonProperty("ORD_UNPR") String unitPrice,
        @JsonProperty("CNDT_PRIC") String conditionPrice,
        @JsonProperty("EXCG_ID_DVSN_CD") String exchangeId
) {
    private static final String LIMIT_ORDER_DIVISION = "00";
    private static final String NORMAL_SELL_TYPE = "01";
    private static final String KRX_EXCHANGE_ID = "KRX";

    public static KisCashOrderRequest limitOrder(
            String accountNumber,
            String accountProductCode,
            BrokerOrderSide side,
            String symbol,
            long quantity,
            long limitPriceKrw
    ) {
        Objects.requireNonNull(side, "side must not be null.");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        if (limitPriceKrw <= 0) {
            throw new IllegalArgumentException("limitPriceKrw must be positive.");
        }

        return new KisCashOrderRequest(
                requireText(accountNumber, "accountNumber"),
                requireText(accountProductCode, "accountProductCode"),
                requireText(symbol, "symbol"),
                side == BrokerOrderSide.SELL ? NORMAL_SELL_TYPE : "",
                LIMIT_ORDER_DIVISION,
                Long.toString(quantity),
                Long.toString(limitPriceKrw),
                "",
                KRX_EXCHANGE_ID
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
