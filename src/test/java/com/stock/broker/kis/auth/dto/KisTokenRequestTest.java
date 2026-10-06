package com.stock.broker.kis.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisTokenRequestTest {
    @Test
    void redactsAllStringValuesInObjectOutput() {
        KisTokenRequest request = new KisTokenRequest("synthetic-grant", "test-app-key", "test-app-secret");

        assertThat(request.toString())
                .isEqualTo("KisTokenRequest[grantType=<redacted>, appKey=<redacted>, appSecret=<redacted>]")
                .doesNotContain("synthetic-grant", "test-app-key", "test-app-secret");
        assertThat(String.valueOf(request)).isEqualTo(request.toString());
    }

    @Test
    void preservesOriginalValuesAndJsonPropertyNames() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KisTokenRequest request = new KisTokenRequest("client_credentials", "test-app-key", "test-app-secret");

        String json = objectMapper.writeValueAsString(request);

        assertThat(objectMapper.readTree(json)).isEqualTo(objectMapper.readTree("""
                {"grant_type":"client_credentials","appkey":"test-app-key","appsecret":"test-app-secret"}
                """));
        assertThat(objectMapper.readValue(json, KisTokenRequest.class)).isEqualTo(request);
        assertThat(request.grantType()).isEqualTo("client_credentials");
        assertThat(request.appKey()).isEqualTo("test-app-key");
        assertThat(request.appSecret()).isEqualTo("test-app-secret");
    }

    @Test
    void redactsNullValuesWithoutChangingThem() {
        KisTokenRequest request = new KisTokenRequest(null, null, null);

        assertThat(request.toString()).isEqualTo(
                "KisTokenRequest[grantType=<redacted>, appKey=<redacted>, appSecret=<redacted>]"
        );
        assertThat(request.appKey()).isNull();
        assertThat(request.appSecret()).isNull();
    }
}
