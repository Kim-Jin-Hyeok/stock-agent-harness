package com.stock.broker.kis.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisTokenResponseTest {

    @Test
    void redactsTokenAndPotentiallyReflectedStringsInObjectOutput() {
        KisTokenResponse response = new KisTokenResponse(
                "test-access-token", "test-app-key", 86_400L, "test-app-secret"
        );

        assertThat(response.toString())
                .isEqualTo("KisTokenResponse[accessToken=<redacted>, tokenType=<redacted>, "
                        + "expiresInSeconds=86400, expiresAt=<redacted>]")
                .doesNotContain("test-access-token", "test-app-key", "test-app-secret");
        assertThat(String.valueOf(response)).isEqualTo(response.toString());
    }

    @Test
    void preservesOriginalValuesAndJsonPropertyNamesWhenSerialized() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KisTokenResponse response = new KisTokenResponse(
                "test-access-token", "Bearer", 86_400L, "2026-09-19 22:30:00"
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(objectMapper.readTree(json)).isEqualTo(objectMapper.readTree("""
                {
                  "access_token": "test-access-token",
                  "token_type": "Bearer",
                  "expires_in": 86400,
                  "access_token_token_expired": "2026-09-19 22:30:00"
                }
                """));
        assertThat(objectMapper.readValue(json, KisTokenResponse.class)).isEqualTo(response);
    }

    @Test
    void redactsNullValuesWithoutChangingThem() {
        KisTokenResponse response = new KisTokenResponse(null, null, 0L, null);

        assertThat(response.toString()).isEqualTo(
                "KisTokenResponse[accessToken=<redacted>, tokenType=<redacted>, expiresInSeconds=0, expiresAt=<redacted>]"
        );
        assertThat(response.accessToken()).isNull();
        assertThat(response.expiresAt()).isNull();
    }

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
