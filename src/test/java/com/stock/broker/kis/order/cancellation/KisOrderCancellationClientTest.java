package com.stock.broker.kis.order.cancellation;

import com.stock.broker.kis.order.cancellation.dto.KisOrderCancellationResponse;
import com.stock.broker.order.BrokerOrderReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisOrderCancellationClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCESS_TOKEN = "test-access-token";

    private MockRestServiceServer server;
    private KisOrderCancellationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisOrderCancellationClient(
                builder.build(),
                APP_KEY,
                APP_SECRET
        );
    }

    @Test
    void sendsPaperAllRemainingCancellationAndDeserializesResponse() {
        server.expect(requestTo(BASE_URL
                        + "/uapi/domestic-stock/v1/trading/order-rvsecncl"))
                .andExpect(method(POST))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json;charset=utf-8"
                ))
                .andExpect(header(
                        "authorization",
                        "Bearer " + ACCESS_TOKEN
                ))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "VTTC0013U"))
                .andExpect(header("custtype", "P"))
                .andExpect(content().json("""
                        {
                          "CANO": "12345678",
                          "ACNT_PRDT_CD": "01",
                          "KRX_FWDG_ORD_ORGNO": "06010",
                          "ORGN_ODNO": "0000123456",
                          "ORD_DVSN": "00",
                          "RVSE_CNCL_DVSN_CD": "02",
                          "ORD_QTY": "7",
                          "ORD_UNPR": "70000",
                          "QTY_ALL_ORD_YN": "Y",
                          "EXCG_ID_DVSN_CD": "KRX",
                          "CNDT_PRIC": ""
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "APBK0013",
                          "msg1": "Cancellation request completed.",
                          "output": {
                            "KRX_FWDG_ORD_ORGNO": "06010",
                            "ODNO": "0000123457",
                            "ORD_TMD": "091531"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        KisOrderCancellationResponse response = cancelAllRemaining(
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output().orderOrganizationNumber())
                .isEqualTo("06010");
        assertThat(response.output().orderNumber())
                .isEqualTo("0000123457");
        server.verify();
    }

    @Test
    void returnsKisBusinessFailureWithoutHidingResponseDetails() {
        server.expect(requestTo(BASE_URL
                        + "/uapi/domestic-stock/v1/trading/order-rvsecncl"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "1",
                          "msg_cd": "APBK0918",
                          "msg1": "Cancellation was rejected.",
                          "output": null
                        }
                        """, MediaType.APPLICATION_JSON));

        KisOrderCancellationResponse response = cancelAllRemaining(
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("APBK0918");
        assertThat(response.message())
                .isEqualTo("Cancellation was rejected.");
        assertThat(response.output()).isNull();
        server.verify();
    }

    @Test
    void rejectsBlankAccessTokenBeforeSendingRequest() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cancelAllRemaining(" "))
                .withMessage("accessToken must not be blank.");
    }

    private KisOrderCancellationResponse cancelAllRemaining(
            String accessToken
    ) {
        return client.cancelAllRemaining(
                "12345678",
                "01",
                new BrokerOrderReference("0000123456", "06010"),
                "00",
                7L,
                70_000L,
                "KRX",
                accessToken
        );
    }
}
