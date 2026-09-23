package com.stock.broker.kis.order;

import com.stock.broker.kis.order.dto.KisCashOrderResponse;
import com.stock.broker.order.BrokerOrderSide;
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

class KisCashOrderClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCESS_TOKEN = "test-access-token";

    private MockRestServiceServer server;
    private KisCashOrderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisCashOrderClient(
                builder.build(),
                APP_KEY,
                APP_SECRET
        );
    }

    @Test
    void sendsPaperBuyLimitOrderAndDeserializesResponse() {
        expectOrderRequest("VTTC0012U", "");

        KisCashOrderResponse response = client.submitLimitOrder(
                BrokerOrderSide.BUY,
                "12345678",
                "01",
                "005930",
                10L,
                70_000L,
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output().orderOrganizationNumber()).isEqualTo("06010");
        assertThat(response.output().orderNumber()).isEqualTo("0000123456");
        server.verify();
    }

    @Test
    void sendsPaperSellLimitOrderWithNormalSellType() {
        expectOrderRequest("VTTC0011U", "01");

        KisCashOrderResponse response = client.submitLimitOrder(
                BrokerOrderSide.SELL,
                "12345678",
                "01",
                "005930",
                10L,
                70_000L,
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isTrue();
        server.verify();
    }

    @Test
    void returnsKisBusinessFailureWithoutHidingResponseDetails() {
        server.expect(requestTo(BASE_URL
                        + "/uapi/domestic-stock/v1/trading/order-cash"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "1",
                          "msg_cd": "APBK0918",
                          "msg1": "Order was rejected.",
                          "output": null
                        }
                        """, MediaType.APPLICATION_JSON));

        KisCashOrderResponse response = client.submitLimitOrder(
                BrokerOrderSide.BUY,
                "12345678",
                "01",
                "005930",
                10L,
                70_000L,
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("APBK0918");
        assertThat(response.message()).isEqualTo("Order was rejected.");
        assertThat(response.output()).isNull();
        server.verify();
    }

    @Test
    void rejectsBlankAccessTokenBeforeSendingRequest() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.submitLimitOrder(
                        BrokerOrderSide.BUY,
                        "12345678",
                        "01",
                        "005930",
                        10L,
                        70_000L,
                        " "
                ))
                .withMessage("accessToken must not be blank.");
    }

    private void expectOrderRequest(String transactionId, String sellType) {
        server.expect(requestTo(BASE_URL
                        + "/uapi/domestic-stock/v1/trading/order-cash"))
                .andExpect(method(POST))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json;charset=utf-8"
                ))
                .andExpect(header("authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", transactionId))
                .andExpect(header("custtype", "P"))
                .andExpect(content().json("""
                        {
                          "CANO": "12345678",
                          "ACNT_PRDT_CD": "01",
                          "PDNO": "005930",
                          "SLL_TYPE": "%s",
                          "ORD_DVSN": "00",
                          "ORD_QTY": "10",
                          "ORD_UNPR": "70000",
                          "CNDT_PRIC": "",
                          "EXCG_ID_DVSN_CD": "KRX"
                        }
                        """.formatted(sellType)))
                .andRespond(withSuccess("""
                        {
                          "rt_cd": "0",
                          "msg_cd": "APBK0013",
                          "msg1": "Order request completed.",
                          "output": {
                            "KRX_FWDG_ORD_ORGNO": "06010",
                            "ODNO": "0000123456",
                            "ORD_TMD": "091530"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
    }
}
