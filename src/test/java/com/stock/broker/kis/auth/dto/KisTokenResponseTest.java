package com.stock.broker.kis.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisTokenResponseTest {

    @Test
    void deserializesKisTokenResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisTokenResponse response = objectMapper.readValue(
                """
                        {
                          "access_token": "test-access-token",
                          "token_type": "Bearer",
                          "expires_in": 86400,
                          "access_token_token_expired": "2026-09-19 22:30:00"
                        }
                        """,
                KisTokenResponse.class
        );

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(86_400L);
        assertThat(response.expiresAt()).isEqualTo("2026-09-19 22:30:00");
    }
}
