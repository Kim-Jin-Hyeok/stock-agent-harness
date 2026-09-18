package com.stock.market.price.provider.kis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisCurrentPriceResponseTest {

    @Test
    void deserializesSuccessfulKisResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisCurrentPriceResponse response = objectMapper.readValue(
                """
                        {
                          "rt_cd": "0",
                          "msg_cd": "MCA00000",
                          "msg1": "Request completed successfully.",
                          "output": {
                            "stck_prpr": "72000"
                          }
                        }
                        """,
                KisCurrentPriceResponse.class
        );

        assertThat(response.resultCode()).isEqualTo("0");
        assertThat(response.messageCode()).isEqualTo("MCA00000");
        assertThat(response.message()).isEqualTo("Request completed successfully.");
        assertThat(response.output().currentPrice()).isEqualTo("72000");
    }

    @Test
    void returnsTrueWhenResultCodeIsSuccess() {
        KisCurrentPriceResponse response = response("0");

        assertThat(response.isSuccessful()).isTrue();
    }

    @Test
    void returnsFalseWhenResultCodeIsFailure() {
        KisCurrentPriceResponse response = response("1");

        assertThat(response.isSuccessful()).isFalse();
    }

    @Test
    void returnsFalseWhenResultCodeIsMissing() {
        KisCurrentPriceResponse response = response(null);

        assertThat(response.isSuccessful()).isFalse();
    }

    private KisCurrentPriceResponse response(String resultCode) {
        return new KisCurrentPriceResponse(
                resultCode,
                "MCA00000",
                "response message",
                new KisCurrentPriceOutput("72000")
        );
    }
}
