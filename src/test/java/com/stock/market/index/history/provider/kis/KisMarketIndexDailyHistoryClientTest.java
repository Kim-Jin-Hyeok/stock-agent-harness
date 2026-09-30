package com.stock.market.index.history.provider.kis;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.provider.kis.dto.KisMarketIndexDailyHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class KisMarketIndexDailyHistoryClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final Instant REQUEST_STARTED_AT =
            Instant.parse("2026-09-30T11:15:00Z");
    private static final Instant REQUEST_FINISHED_AT =
            Instant.parse("2026-09-30T11:15:01.250Z");

    private MockRestServiceServer server;
    private KisMarketIndexDailyHistoryClient client;
    private Clock clock;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(
                REQUEST_STARTED_AT,
                REQUEST_FINISHED_AT
        );
        client = new KisMarketIndexDailyHistoryClient(
                builder.build(),
                APP_KEY,
                APP_SECRET,
                clock
        );
    }

    @Test
    void sendsKospiDailyHistoryRequestAndDeserializesResponse(
            CapturedOutput output
    ) {
        server.expect(requestTo(
                        BASE_URL
                                + "/uapi/domestic-stock/v1/quotations/"
                                + "inquire-daily-indexchartprice"
                                + "?FID_COND_MRKT_DIV_CODE=U"
                                + "&FID_INPUT_ISCD=0001"
                                + "&FID_INPUT_DATE_1=20260901"
                                + "&FID_INPUT_DATE_2=20260930"
                                + "&FID_PERIOD_DIV_CODE=D"
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
                .andExpect(header("tr_id", "FHKUP03500100"))
                .andRespond(withSuccess(
                        """
                                {
                                  "rt_cd": "0",
                                  "msg_cd": "MCA00000",
                                  "msg1": "Request completed successfully.",
                                  "output2": [
                                    {
                                      "stck_bsop_date": "20260930",
                                      "bstp_nmix_prpr": "3421.37"
                                    }
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisMarketIndexDailyHistoryResponse response =
                client.getDailyHistoryPage(request(), ACCESS_TOKEN);

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output()).hasSize(1);
        assertThat(response.output().getFirst().toObservation().closeValue())
                .isEqualByComparingTo("3421.37");
        assertThat(output.getAll())
                .contains(
                        "KIS market index daily history request completed. "
                                + "status=SUCCESS, benchmarkId=KOSPI, "
                                + "fromDate=2026-09-01, "
                                + "toDate=2026-09-30, durationMs=1250, "
                                + "messageCode=MCA00000, outputCount=1"
                )
                .doesNotContain(APP_KEY)
                .doesNotContain(APP_SECRET)
                .doesNotContain(ACCESS_TOKEN);
        server.verify();
    }

    @Test
    void preservesKisFailureResponseMessageCode(CapturedOutput output) {
        server.expect(method(GET)).andRespond(withSuccess(
                """
                        {
                          "rt_cd": "1",
                          "msg_cd": "OPSQ0002",
                          "msg1": "Invalid request.",
                          "output2": []
                        }
                        """,
                MediaType.APPLICATION_JSON
        ));

        KisMarketIndexDailyHistoryResponse response =
                client.getDailyHistoryPage(request(), ACCESS_TOKEN);

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("OPSQ0002");
        assertThat(output.getAll()).contains(
                "status=FAILED, benchmarkId=KOSPI"
        );
        server.verify();
    }

    @Test
    void logsTransportFailureWithoutCredentials(CapturedOutput output) {
        server.expect(method(GET)).andRespond(withServerError());

        assertThatThrownBy(() ->
                client.getDailyHistoryPage(request(), ACCESS_TOKEN)
        ).isInstanceOf(RestClientResponseException.class);

        assertThat(output.getAll())
                .contains(
                        "KIS market index daily history request failed. "
                                + "benchmarkId=KOSPI, "
                                + "fromDate=2026-09-01, "
                                + "toDate=2026-09-30, durationMs=1250"
                )
                .doesNotContain(APP_KEY)
                .doesNotContain(APP_SECRET)
                .doesNotContain(ACCESS_TOKEN);
        server.verify();
    }

    @Test
    void rejectsNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> client.getDailyHistoryPage(
                        null,
                        ACCESS_TOKEN
                ))
                .withMessage("request must not be null.");
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getDailyHistoryPage(
                        request(),
                        " "
                ))
                .withMessage("accessToken must not be blank.");
    }

    private MarketIndexDailyHistoryRequest request() {
        return new MarketIndexDailyHistoryRequest(
                "KOSPI",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
    }
}
