package com.stock.broker.kis.order.cancellation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisOrderCancellationResponseTest {

    @Test
    void deserializesCancellationOrderIdentifiers() throws Exception {
        KisOrderCancellationResponse response = new ObjectMapper().readValue(
                """
                        {
                          "rt_cd": "0",
                          "msg_cd": "APBK0013",
                          "msg1": "Cancellation request completed.",
                          "output": {
                            "KRX_FWDG_ORD_ORGNO": "06010",
                            "ODNO": "0000123457",
                            "ORD_TMD": "091531"
                          }
                        }
                        """,
                KisOrderCancellationResponse.class
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output().orderOrganizationNumber())
                .isEqualTo("06010");
        assertThat(response.output().orderNumber())
                .isEqualTo("0000123457");
        assertThat(response.output().orderTime()).isEqualTo("091531");
    }

    @Test
    void preservesFailedResponseWithoutOutput() throws Exception {
        KisOrderCancellationResponse response = new ObjectMapper().readValue(
                """
                        {
                          "rt_cd": "1",
                          "msg_cd": "APBK0918",
                          "msg1": "Cancellation was rejected.",
                          "output": null
                        }
                        """,
                KisOrderCancellationResponse.class
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("APBK0918");
        assertThat(response.message())
                .isEqualTo("Cancellation was rejected.");
        assertThat(response.output()).isNull();
    }
}
