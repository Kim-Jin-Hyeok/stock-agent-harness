package com.stock.broker.kis.order.cancellation.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisCancelableOrderOutput(
        @JsonProperty("ord_gno_brno") String orderOrganizationNumber,
        @JsonProperty("odno") String orderNumber,
        @JsonProperty("orgn_odno") String originalOrderNumber,
        @JsonProperty("pdno") String symbol,
        @JsonProperty("ord_dvsn_cd") String orderDivisionCode,
        @JsonProperty("ord_qty") String orderQuantity,
        @JsonProperty("ord_unpr") String orderPrice,
        @JsonProperty("tot_ccld_qty") String cumulativeFilledQuantity,
        @JsonProperty("psbl_qty") String cancelableQuantity,
        @JsonProperty("excg_id_dvsn_cd") String exchangeId
) {
}
