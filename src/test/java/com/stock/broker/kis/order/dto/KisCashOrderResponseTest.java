package com.stock.broker.kis.order.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisCashOrderResponseTest {

    @Test
    void deserializesOrderIdentifiersWithoutCombiningThem() throws Exception {
        KisCashOrderResponse response = new ObjectMapper().readValue(
                """
                        {
                          "rt_cd": "0",
                          "msg_cd": "APBK0013",
                          "msg1": "Order request completed.",
                          "output": {
                            "KRX_FWDG_ORD_ORGNO": "06010",
                            "ODNO": "0000123456",
                            "ORD_TMD": "091530"
                          }
                        }
                        """,
                KisCashOrderResponse.class
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output().orderOrganizationNumber()).isEqualTo("06010");
        assertThat(response.output().orderNumber()).isEqualTo("0000123456");
        assertThat(response.output().orderTime()).isEqualTo("091530");
    }

    @Test
    void preservesFailedResponseWithoutOutput() throws Exception {
        KisCashOrderResponse response = new ObjectMapper().readValue(
                """
                        {
                          "rt_cd": "1",
                          "msg_cd": "APBK0918",
                          "msg1": "Order was rejected.",
                          "output": null
                        }
                        """,
                KisCashOrderResponse.class
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("APBK0918");
        assertThat(response.message()).isEqualTo("Order was rejected.");
        assertThat(response.output()).isNull();
    }
}
