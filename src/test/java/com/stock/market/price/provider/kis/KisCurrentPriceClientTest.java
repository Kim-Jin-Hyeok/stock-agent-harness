package com.stock.market.price.provider.kis;

import com.stock.market.price.provider.kis.dto.KisCurrentPriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisCurrentPriceClientTest {
    private static final String BASE_URL = "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCESS_TOKEN = "test-access-token";

    private MockRestServiceServer server;
    private KisCurrentPriceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisCurrentPriceClient(
                builder.build(),
                APP_KEY,
                APP_SECRET
        );
    }

    @Test
    void sendsCurrentPriceRequestAndDeserializesResponse() {
        server.expect(requestTo(
                        BASE_URL
                                + "/uapi/domestic-stock/v1/quotations/inquire-price"
                                + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"
                ))
                .andExpect(method(GET))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "FHKST01010100"))
                .andRespond(withSuccess(
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
                        MediaType.APPLICATION_JSON
                ));

        KisCurrentPriceResponse response = client.getCurrentPrice(
                "005930",
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output().currentPrice()).isEqualTo("72000");
        server.verify();
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getCurrentPrice(" ", ACCESS_TOKEN))
                .withMessage("symbol must not be blank.");
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getCurrentPrice("005930", " "))
                .withMessage("accessToken must not be blank.");
    }
}
