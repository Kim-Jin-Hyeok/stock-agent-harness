package com.stock.broker.kis.order.cancellation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisOrderCancellationOutput(
        @JsonProperty("KRX_FWDG_ORD_ORGNO") String orderOrganizationNumber,
        @JsonProperty("ODNO") String orderNumber,
        @JsonProperty("ORD_TMD") String orderTime
) {
}
