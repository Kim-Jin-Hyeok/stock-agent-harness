package com.stock.broker.kis.order.cancellation.inquiry;

import com.stock.broker.kis.order.cancellation.inquiry.dto.KisCancelableOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisCancelableOrderInquiryClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final int MAX_PAGES = 10;

    private MockRestServiceServer server;
    private KisCancelableOrderInquiryClient client;

    @BeforeEach
    void setUp() {
        createClient(MAX_PAGES);
    }

    @Test
    void sendsCancelableOrderInquiryAndDeserializesResponse() {
        server.expect(requestTo(cancelableOrderUrl("", "")))
                .andExpect(method(GET))
                .andExpect(header(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(header("authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "TTTC0084R"))
                .andExpect(queryParam("CANO", ACCOUNT_NUMBER))
                .andExpect(queryParam(
                        "ACNT_PRDT_CD",
                        ACCOUNT_PRODUCT_CODE
                ))
                .andExpect(queryParam("INQR_DVSN_1", "1"))
                .andExpect(queryParam("INQR_DVSN_2", "0"))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "7",
                                "",
                                ""
                        ),
                        MediaType.APPLICATION_JSON
                ));

        KisCancelableOrderResponse response = getCancelableOrders();

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output())
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.orderNumber())
                            .isEqualTo("0000123456");
                    assertThat(output.orderOrganizationNumber())
                            .isEqualTo("06010");
                    assertThat(output.cancelableQuantity()).isEqualTo("7");
                });
        server.verify();
    }

    @Test
    void requestsNextPageAndMergesOutputs() {
        server.expect(requestTo(cancelableOrderUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "7",
                                "next-fk",
                                "next-nk"
                        ),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));
        server.expect(requestTo(cancelableOrderUrl("next-fk", "next-nk")))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123457",
                                "2",
                                "",
                                ""
                        ),
                        MediaType.APPLICATION_JSON
                ));

        KisCancelableOrderResponse response = getCancelableOrders();

        assertThat(response.output())
                .extracting(output -> output.orderNumber())
                .containsExactly("0000123456", "0000123457");
        server.verify();
    }

    @Test
    void returnsFailureWithoutPartialOutputsWhenNextPageFails() {
        server.expect(requestTo(cancelableOrderUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "7",
                                "next-fk",
                                "next-nk"
                        ),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));
        server.expect(requestTo(cancelableOrderUrl("next-fk", "next-nk")))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(
                        """
                                {
                                  "rt_cd": "1",
                                  "msg_cd": "EGW00123",
                                  "msg1": "Request failed.",
                                  "output": []
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisCancelableOrderResponse response = getCancelableOrders();

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("EGW00123");
        assertThat(response.output()).isEmpty();
        server.verify();
    }

    @Test
    void rejectsPaginationBeyondMaxPages() {
        createClient(1);
        server.expect(requestTo(cancelableOrderUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "7",
                                "next-fk",
                                "next-nk"
                        ),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));

        assertThatIllegalStateException()
                .isThrownBy(this::getCancelableOrders)
                .withMessage(
                        "KIS cancelable order pagination exceeded maxPages=1."
                );
        server.verify();
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getCancelableOrders(
                        ACCOUNT_NUMBER,
                        ACCOUNT_PRODUCT_CODE,
                        " "
                ))
                .withMessage("accessToken must not be blank.");
    }

    private void createClient(int maxPages) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisCancelableOrderInquiryClient(
                builder.build(),
                APP_KEY,
                APP_SECRET,
                maxPages
        );
    }

    private KisCancelableOrderResponse getCancelableOrders() {
        return client.getCancelableOrders(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        );
    }

    private String cancelableOrderUrl(
            String contextAreaFk100,
            String contextAreaNk100
    ) {
        return BASE_URL
                + "/uapi/domestic-stock/v1/trading/inquire-psbl-rvsecncl"
                + "?CANO=12345678"
                + "&ACNT_PRDT_CD=01"
                + "&INQR_DVSN_1=1"
                + "&INQR_DVSN_2=0"
                + "&CTX_AREA_FK100=" + contextAreaFk100
                + "&CTX_AREA_NK100=" + contextAreaNk100;
    }

    private String successfulResponse(
            String orderNumber,
            String cancelableQuantity,
            String contextAreaFk100,
            String contextAreaNk100
    ) {
        return """
                {
                  "rt_cd": "0",
                  "msg_cd": "KIOK0560",
                  "msg1": "Request completed successfully.",
                  "output": [
                    {
                      "ord_gno_brno": "06010",
                      "odno": "%s",
                      "orgn_odno": "",
                      "pdno": "005930",
                      "ord_dvsn_cd": "00",
                      "ord_qty": "10",
                      "ord_unpr": "70000",
                      "tot_ccld_qty": "3",
                      "psbl_qty": "%s",
                      "excg_id_dvsn_cd": "KRX"
                    }
                  ],
                  "ctx_area_fk100": "%s",
                  "ctx_area_nk100": "%s"
                }
                """.formatted(
                orderNumber,
                cancelableQuantity,
                contextAreaFk100,
                contextAreaNk100
        );
    }
}
