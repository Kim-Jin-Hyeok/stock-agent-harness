package com.stock.market.price.history.provider.kis;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.provider.kis.dto.KisDailyPriceHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisDailyPriceHistoryClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCESS_TOKEN = "test-access-token";

    private MockRestServiceServer server;
    private KisDailyPriceHistoryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisDailyPriceHistoryClient(
                builder.build(),
                APP_KEY,
                APP_SECRET,
                KisDailyPriceHistoryMarket.INTEGRATED
        );
    }

    @Test
    void sendsDailyPriceHistoryRequestAndDeserializesResponse() {
        server.expect(requestTo(
                        BASE_URL
                                + "/uapi/domestic-stock/v1/quotations/"
                                + "inquire-daily-itemchartprice"
                                + "?FID_COND_MRKT_DIV_CODE=UN"
                                + "&FID_INPUT_ISCD=005930"
                                + "&FID_INPUT_DATE_1=20260901"
                                + "&FID_INPUT_DATE_2=20260923"
                                + "&FID_PERIOD_DIV_CODE=D"
                                + "&FID_ORG_ADJ_PRC=0"
                ))
                .andExpect(method(GET))
                .andExpect(header(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(header(
                        "authorization",
                        "Bearer " + ACCESS_TOKEN
                ))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "FHKST03010100"))
                .andRespond(withSuccess(
                        """
                                {
                                  "rt_cd": "0",
                                  "msg_cd": "MCA00000",
                                  "msg1": "Request completed successfully.",
                                  "output2": [
                                    {
                                      "stck_bsop_date": "20260923",
                                      "stck_oprc": "70000",
                                      "stck_hgpr": "73000",
                                      "stck_lwpr": "69000",
                                      "stck_clpr": "72000",
                                      "acml_vol": "1234567"
                                    }
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisDailyPriceHistoryResponse response =
                client.getDailyPriceHistoryPage(request(), ACCESS_TOKEN);

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output()).hasSize(1);
        assertThat(response.output().getFirst().tradingDate())
                .isEqualTo("20260923");
        server.verify();
    }

    @Test
    void rejectsNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> client.getDailyPriceHistoryPage(
                        null,
                        ACCESS_TOKEN
                ))
                .withMessage("request must not be null.");
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getDailyPriceHistoryPage(
                        request(),
                        " "
                ))
                .withMessage("accessToken must not be blank.");
    }

    private DailyPriceHistoryRequest request() {
        return new DailyPriceHistoryRequest(
                "005930",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 23)
        );
    }
}
