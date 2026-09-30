package com.stock.market.index.history.provider.kis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KisMarketIndexDailyHistoryResponseTest {

    @Test
    void deserializesSuccessfulKisResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisMarketIndexDailyHistoryResponse response =
                objectMapper.readValue(
                        """
                                {
                                  "rt_cd": "0",
                                  "msg_cd": "MCA00000",
                                  "msg1": "Request completed successfully.",
                                  "output2": [
                                    {
                                      "stck_bsop_date": "20260930",
                                      "bstp_nmix_prpr": "3421.37"
                                    }
                                  ]
                                }
                                """,
                        KisMarketIndexDailyHistoryResponse.class
                );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.messageCode()).isEqualTo("MCA00000");
        assertThat(response.output()).hasSize(1);
        assertThat(response.output().getFirst().closeValue())
                .isEqualTo("3421.37");
    }

    @Test
    void returnsFalseForFailureResultCode() {
        KisMarketIndexDailyHistoryResponse response =
                new KisMarketIndexDailyHistoryResponse(
                        "1",
                        "OPSQ0002",
                        "Invalid request.",
                        List.of()
                );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("OPSQ0002");
    }
}
