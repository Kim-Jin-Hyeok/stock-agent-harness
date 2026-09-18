package com.stock.broker.kis.auth;

import com.stock.broker.kis.auth.dto.KisTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisTokenClientTest {
    private static final String BASE_URL = "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";

    @Test
    void sendsTokenRequestAndDeserializesResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisTokenClient client = new KisTokenClient(
                builder.build(),
                APP_KEY,
                APP_SECRET
        );

        server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        """
                                {
                                  "grant_type": "client_credentials",
                                  "appkey": "test-app-key",
                                  "appsecret": "test-app-secret"
                                }
                                """,
                        JsonCompareMode.STRICT
                ))
                .andRespond(withSuccess(
                        """
                                {
                                  "access_token": "test-access-token",
                                  "token_type": "Bearer",
                                  "expires_in": 86400,
                                  "access_token_token_expired": "2026-09-19 22:30:00"
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisTokenResponse response = client.issueToken();

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(86_400L);
        assertThat(response.expiresAt()).isEqualTo("2026-09-19 22:30:00");
        server.verify();
    }

    @Test
    void rejectsBlankAppKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisTokenClient(
                        RestClient.create(BASE_URL),
                        " ",
                        APP_SECRET
                ))
                .withMessage("appKey must not be blank.");
    }

    @Test
    void rejectsBlankAppSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisTokenClient(
                        RestClient.create(BASE_URL),
                        APP_KEY,
                        " "
                ))
                .withMessage("appSecret must not be blank.");
    }
}
