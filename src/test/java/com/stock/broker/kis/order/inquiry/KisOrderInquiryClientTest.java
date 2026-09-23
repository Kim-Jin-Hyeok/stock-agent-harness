package com.stock.broker.kis.order.inquiry;

import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryResponse;
import com.stock.broker.order.BrokerOrderReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisOrderInquiryClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String SYMBOL = "005930";
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 23);
    private static final BrokerOrderReference REFERENCE =
            new BrokerOrderReference("0000123456", "06010");
    private static final int MAX_PAGES = 10;

    private MockRestServiceServer server;
    private KisOrderInquiryClient client;

    @BeforeEach
    void setUp() {
        createClient(MAX_PAGES);
    }

    @Test
    void sendsOrderInquiryRequestAndDeserializesResponse() {
        server.expect(requestTo(orderInquiryUrl("", "")))
                .andExpect(method(GET))
                .andExpect(header(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(header("authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "VTTC0081R"))
                .andExpect(queryParam("INQR_STRT_DT", "20260923"))
                .andExpect(queryParam("INQR_END_DT", "20260923"))
                .andExpect(queryParam("ORD_GNO_BRNO", "06010"))
                .andExpect(queryParam("ODNO", "0000123456"))
                .andExpect(queryParam("EXCG_ID_DVSN_CD", "KRX"))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "3",
                                "7",
                                "",
                                ""
                        ),
                        MediaType.APPLICATION_JSON
                ));

        KisOrderInquiryResponse response = getOrderExecutions();

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output1())
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.orderNumber())
                            .isEqualTo("0000123456");
                    assertThat(output.orderOrganizationNumber())
                            .isEqualTo("06010");
                    assertThat(output.symbol()).isEqualTo(SYMBOL);
                    assertThat(output.requestedQuantity()).isEqualTo("10");
                    assertThat(output.cumulativeFilledQuantity())
                            .isEqualTo("3");
                    assertThat(output.remainingQuantity()).isEqualTo("7");
                });
        server.verify();
    }

    @Test
    void requestsNextPageAndMergesOutputs() {
        server.expect(requestTo(orderInquiryUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "3",
                                "7",
                                "next-fk",
                                "next-nk"
                        ),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));
        server.expect(requestTo(orderInquiryUrl("next-fk", "next-nk")))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123457",
                                "10",
                                "0",
                                "",
                                ""
                        ),
                        MediaType.APPLICATION_JSON
                ));

        KisOrderInquiryResponse response = getOrderExecutions();

        assertThat(response.output1())
                .extracting(output -> output.orderNumber())
                .containsExactly("0000123456", "0000123457");
        server.verify();
    }

    @Test
    void returnsFailureWithoutPartialOutputsWhenNextPageFails() {
        server.expect(requestTo(orderInquiryUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "3",
                                "7",
                                "next-fk",
                                "next-nk"
                        ),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));
        server.expect(requestTo(orderInquiryUrl("next-fk", "next-nk")))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(
                        """
                                {
                                  "rt_cd": "1",
                                  "msg_cd": "EGW00123",
                                  "msg1": "Request failed.",
                                  "output1": []
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisOrderInquiryResponse response = getOrderExecutions();

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.messageCode()).isEqualTo("EGW00123");
        assertThat(response.output1()).isEmpty();
        server.verify();
    }

    @Test
    void rejectsPaginationBeyondMaxPages() {
        createClient(1);
        server.expect(requestTo(orderInquiryUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse(
                                "0000123456",
                                "3",
                                "7",
                                "next-fk",
                                "next-nk"
                        ),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));

        assertThatIllegalStateException()
                .isThrownBy(this::getOrderExecutions)
                .withMessage(
                        "KIS order inquiry pagination exceeded maxPages=1."
                );
        server.verify();
    }

    @Test
    void rejectsStartDateAfterEndDate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getOrderExecutions(
                        ACCOUNT_NUMBER,
                        ACCOUNT_PRODUCT_CODE,
                        ORDER_DATE.plusDays(1),
                        ORDER_DATE,
                        REFERENCE,
                        SYMBOL,
                        ACCESS_TOKEN
                ))
                .withMessage("startDate must not be after endDate.");
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getOrderExecutions(
                        ACCOUNT_NUMBER,
                        ACCOUNT_PRODUCT_CODE,
                        ORDER_DATE,
                        ORDER_DATE,
                        REFERENCE,
                        SYMBOL,
                        " "
                ))
                .withMessage("accessToken must not be blank.");
    }

    private void createClient(int maxPages) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisOrderInquiryClient(
                builder.build(),
                APP_KEY,
                APP_SECRET,
                maxPages
        );
    }

    private KisOrderInquiryResponse getOrderExecutions() {
        return client.getOrderExecutions(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ORDER_DATE,
                ORDER_DATE,
                REFERENCE,
                SYMBOL,
                ACCESS_TOKEN
        );
    }

    private String orderInquiryUrl(
            String contextAreaFk100,
            String contextAreaNk100
    ) {
        return BASE_URL
                + "/uapi/domestic-stock/v1/trading/inquire-daily-ccld"
                + "?CANO=12345678"
                + "&ACNT_PRDT_CD=01"
                + "&INQR_STRT_DT=20260923"
                + "&INQR_END_DT=20260923"
                + "&SLL_BUY_DVSN_CD=00"
                + "&PDNO=005930"
                + "&CCLD_DVSN=00"
                + "&INQR_DVSN=00"
                + "&INQR_DVSN_3=00"
                + "&ORD_GNO_BRNO=06010"
                + "&ODNO=0000123456"
                + "&INQR_DVSN_1="
                + "&CTX_AREA_FK100=" + contextAreaFk100
                + "&CTX_AREA_NK100=" + contextAreaNk100
                + "&EXCG_ID_DVSN_CD=KRX";
    }

    private String successfulResponse(
            String orderNumber,
            String cumulativeFilledQuantity,
            String remainingQuantity,
            String contextAreaFk100,
            String contextAreaNk100
    ) {
        return """
                {
                  "rt_cd": "0",
                  "msg_cd": "KIOK0560",
                  "msg1": "Request completed successfully.",
                  "output1": [
                    {
                      "ord_dt": "20260923",
                      "ord_gno_brno": "06010",
                      "odno": "%s",
                      "orgn_odno": "",
                      "sll_buy_dvsn_cd": "02",
                      "pdno": "005930",
                      "ord_qty": "10",
                      "ord_unpr": "70000",
                      "tot_ccld_qty": "%s",
                      "avg_prvs": "69900.0000",
                      "tot_ccld_amt": "209700",
                      "cncl_yn": "N",
                      "rmn_qty": "%s"
                    }
                  ],
                  "ctx_area_fk100": "%s",
                  "ctx_area_nk100": "%s"
                }
                """.formatted(
                orderNumber,
                cumulativeFilledQuantity,
                remainingQuantity,
                contextAreaFk100,
                contextAreaNk100
        );
    }
}
