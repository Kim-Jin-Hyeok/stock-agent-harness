package com.stock.broker.kis.order.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisOrderInquiryOutput(
        @JsonProperty("ord_dt") String orderDate,
        @JsonProperty("ord_gno_brno") String orderOrganizationNumber,
        @JsonProperty("odno") String orderNumber,
        @JsonProperty("orgn_odno") String originalOrderNumber,
        @JsonProperty("sll_buy_dvsn_cd") String sideCode,
        @JsonProperty("pdno") String symbol,
        @JsonProperty("ord_qty") String requestedQuantity,
        @JsonProperty("ord_unpr") String orderPrice,
        @JsonProperty("tot_ccld_qty") String cumulativeFilledQuantity,
        @JsonProperty("avg_prvs") String averageFilledPrice,
        @JsonProperty("tot_ccld_amt") String cumulativeFilledAmount,
        @JsonProperty("cncl_yn") String canceled,
        @JsonProperty("rmn_qty") String remainingQuantity
) {
}
