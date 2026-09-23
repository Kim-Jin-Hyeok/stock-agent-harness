package com.stock.broker.kis.order.inquiry.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisOrderInquiryResponseTest {

    @Test
    void deserializesOrderExecutionFieldsAsRawStrings() throws Exception {
        KisOrderInquiryResponse response = new ObjectMapper().readValue(
                """
                        {
                          "rt_cd": "0",
                          "msg_cd": "KIOK0560",
                          "msg1": "Request completed successfully.",
                          "output1": [
                            {
                              "ord_dt": "20260923",
                              "ord_gno_brno": "06010",
                              "odno": "0000123456",
                              "orgn_odno": "",
                              "sll_buy_dvsn_cd": "02",
                              "pdno": "005930",
                              "ord_qty": "10",
                              "ord_unpr": "70000",
                              "tot_ccld_qty": "3",
                              "avg_prvs": "69900.0000",
                              "tot_ccld_amt": "209700",
                              "cncl_yn": "N",
                              "rmn_qty": "7"
                            }
                          ],
                          "ctx_area_fk100": "next-fk",
                          "ctx_area_nk100": "next-nk"
                        }
                        """,
                KisOrderInquiryResponse.class
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output1())
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.orderNumber())
                            .isEqualTo("0000123456");
                    assertThat(output.orderOrganizationNumber())
                            .isEqualTo("06010");
                    assertThat(output.cumulativeFilledQuantity())
                            .isEqualTo("3");
                    assertThat(output.averageFilledPrice())
                            .isEqualTo("69900.0000");
                    assertThat(output.canceled()).isEqualTo("N");
                });
        assertThat(response.contextAreaFk100()).isEqualTo("next-fk");
        assertThat(response.contextAreaNk100()).isEqualTo("next-nk");
    }

    @Test
    void preservesFailedResponse() throws Exception {
        KisOrderInquiryResponse response = new ObjectMapper().readValue(
                """
                        {
                          "rt_cd": "1",
                          "msg_cd": "EGW00123",
                          "msg1": "Request failed.",
                          "output1": []
                        }
                        """,
                KisOrderInquiryResponse.class
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("EGW00123");
        assertThat(response.message()).isEqualTo("Request failed.");
        assertThat(response.output1()).isEmpty();
    }
}
