package com.stock.market.price.history.provider.kis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KisDailyPriceHistoryResponseTest {

    @Test
    void deserializesSuccessfulKisResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisDailyPriceHistoryResponse response = objectMapper.readValue(
                """
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "msg1": "Request completed successfully.",
                          "output1": {
                            "stck_prpr": "72000"
                          },
                          "output2": [
                            {
                              "stck_bsop_date": "20260923",
                              "stck_oprc": "70000",
                              "stck_hgpr": "73000",
                              "stck_lwpr": "69000",
                              "stck_clpr": "72000",
                              "acml_vol": "1234567",
                              "acml_tr_pbmn": "87654321"
                            }
                          ]
                        }
                        """,
                KisDailyPriceHistoryResponse.class
        );

        assertThat(response.resultCode()).isEqualTo("0");
        assertThat(response.messageCode()).isEqualTo("MCA00000");
        assertThat(response.message()).isEqualTo(
                "Request completed successfully."
        );
        assertThat(response.output()).hasSize(1);
        assertThat(response.output().getFirst().tradingDate())
                .isEqualTo("20260923");
    }

    @Test
    void returnsTrueWhenResultCodeIsSuccess() {
        assertThat(response("0").isSuccessful()).isTrue();
    }

    @Test
    void returnsFalseWhenResultCodeIsFailure() {
        assertThat(response("1").isSuccessful()).isFalse();
    }

    @Test
    void returnsFalseWhenResultCodeIsMissing() {
        assertThat(response(null).isSuccessful()).isFalse();
    }

    private KisDailyPriceHistoryResponse response(String resultCode) {
        return new KisDailyPriceHistoryResponse(
                resultCode,
                "MCA00000",
                "response message",
                List.of()
        );
    }
}
