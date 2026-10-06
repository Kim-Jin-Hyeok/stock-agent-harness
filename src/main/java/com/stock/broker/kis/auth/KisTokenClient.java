package com.stock.broker.kis.auth;

import com.stock.broker.kis.auth.dto.KisTokenRequest;
import com.stock.broker.kis.auth.dto.KisTokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

        try {
            return restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, response) -> {
                        // Do not read an error body that may echo credentials.
                        throw httpFailure(response.getStatusCode().value());
                    })
                    .body(KisTokenResponse.class);
        } catch (RestClientResponseException failure) {
            throw httpFailure(failure.getStatusCode().value());
        } catch (ResourceAccessException failure) {
            throw new ResourceAccessException("KIS token request failed.");
        } catch (RestClientException failure) {
            throw new RestClientException("KIS token request failed.");
        } catch (RuntimeException failure) {
            // Transport and conversion exceptions may contain secrets in causes or suppressed errors.
            throw new IllegalStateException("KIS token request failed.");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }

    private static RestClientResponseException httpFailure(int status) {
        // Preserve downstream failure classification without carrying response data or causes.
        return new RestClientResponseException(
                "KIS token response HTTP status=" + status + ".",
                status,
                "",
                HttpHeaders.EMPTY,
                new byte[0],
                null
        );
    }
}
