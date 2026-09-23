package com.stock.broker.kis.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisCashOrderOutput(
        @JsonProperty("KRX_FWDG_ORD_ORGNO") String orderOrganizationNumber,
        @JsonProperty("ODNO") String orderNumber,
        @JsonProperty("ORD_TMD") String orderTime
) {
}
