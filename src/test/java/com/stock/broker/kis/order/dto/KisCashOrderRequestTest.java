package com.stock.broker.kis.order.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.broker.order.BrokerOrderSide;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisCashOrderRequestTest {

    @Test
    void createsKrxLimitBuyOrderWithStringValues() throws Exception {
        KisCashOrderRequest request = KisCashOrderRequest.limitOrder(
                "12345678",
                "01",
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L
        );

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).isEqualTo(
                "{\"CANO\":\"12345678\","
                        + "\"ACNT_PRDT_CD\":\"01\","
                        + "\"PDNO\":\"005930\","
                        + "\"SLL_TYPE\":\"\","
                        + "\"ORD_DVSN\":\"00\","
                        + "\"ORD_QTY\":\"10\","
                        + "\"ORD_UNPR\":\"70000\","
                        + "\"CNDT_PRIC\":\"\","
                        + "\"EXCG_ID_DVSN_CD\":\"KRX\"}"
        );
    }

    @Test
    void usesNormalSellTypeForSellOrder() {
        KisCashOrderRequest request = KisCashOrderRequest.limitOrder(
                "12345678",
                "01",
                BrokerOrderSide.SELL,
                "005930",
                10L,
                70_000L
        );

        assertThat(request.sellType()).isEqualTo("01");
    }
}
