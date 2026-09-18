package com.stock.broker.kis.auth;

import com.stock.broker.kis.auth.dto.KisTokenRequest;
import com.stock.broker.kis.auth.dto.KisTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public class KisTokenClient {
    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String GRANT_TYPE = "client_credentials";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;

    public KisTokenClient(
            RestClient restClient,
            String appKey,
            String appSecret
    ) {
        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null."
        );
        this.appKey = requireText(appKey, "appKey");
        this.appSecret = requireText(appSecret, "appSecret");
    }

    public KisTokenResponse issueToken() {
        KisTokenRequest request = new KisTokenRequest(
                GRANT_TYPE,
                appKey,
                appSecret
        );

        return restClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KisTokenResponse.class);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
