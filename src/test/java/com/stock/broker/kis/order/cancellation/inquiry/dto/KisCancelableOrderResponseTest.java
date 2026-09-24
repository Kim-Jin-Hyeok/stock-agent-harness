package com.stock.broker.kis.order.cancellation.inquiry.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisCancelableOrderResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCancelableOrderResponseWithoutConvertingNumbers() throws Exception {
        KisCancelableOrderResponse response = objectMapper.readValue(
                successfulResponseJson(),
                KisCancelableOrderResponse.class
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output())
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.orderOrganizationNumber())
                            .isEqualTo("06010");
                    assertThat(output.orderNumber())
                            .isEqualTo("0000123456");
                    assertThat(output.symbol()).isEqualTo("005930");
                    assertThat(output.orderQuantity()).isEqualTo("10");
                    assertThat(output.cumulativeFilledQuantity())
                            .isEqualTo("3");
                    assertThat(output.cancelableQuantity()).isEqualTo("7");
                    assertThat(output.orderPrice()).isEqualTo("70000");
                    assertThat(output.exchangeId()).isEqualTo("KRX");
                });
    }

    @Test
    void preservesFailedResponse() throws Exception {
        KisCancelableOrderResponse response = objectMapper.readValue(
                """
                        {
                          "rt_cd": "1",
                          "msg_cd": "EGW00123",
                          "msg1": "Request failed.",
                          "output": []
                        }
                        """,
                KisCancelableOrderResponse.class
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("EGW00123");
        assertThat(response.message()).isEqualTo("Request failed.");
        assertThat(response.output()).isEmpty();
    }

    private String successfulResponseJson() {
        return """
                {
                  "rt_cd": "0",
                  "msg_cd": "KIOK0560",
                  "msg1": "Request completed successfully.",
                  "output": [
                    {
                      "ord_gno_brno": "06010",
                      "odno": "0000123456",
                      "orgn_odno": "",
                      "pdno": "005930",
                      "ord_dvsn_cd": "00",
                      "ord_qty": "10",
                      "ord_unpr": "70000",
                      "tot_ccld_qty": "3",
                      "psbl_qty": "7",
                      "excg_id_dvsn_cd": "KRX"
                    }
                  ],
                  "ctx_area_fk100": "",
                  "ctx_area_nk100": ""
                }
                """;
    }
}
