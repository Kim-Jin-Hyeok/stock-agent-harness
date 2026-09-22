package com.stock.broker.kis.account;

import com.stock.broker.kis.account.dto.KisAccountBalanceResponse;
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

class KisAccountBalanceClientTest {
    private static final String BASE_URL =
            "https://openapivts.koreainvestment.com:29443";
    private static final String APP_KEY = "test-app-key";
    private static final String APP_SECRET = "test-app-secret";
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final int MAX_PAGES = 10;

    private MockRestServiceServer server;
    private KisAccountBalanceClient client;

    @BeforeEach
    void setUp() {
        createClient(MAX_PAGES);
    }

    private void createClient(int maxPages) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KisAccountBalanceClient(
                builder.build(),
                APP_KEY,
                APP_SECRET,
                maxPages
        );
    }

    @Test
    void sendsAccountBalanceRequestAndDeserializesResponse() {
        server.expect(requestTo(BASE_URL
                        + "/uapi/domestic-stock/v1/trading/inquire-balance"
                        + "?CANO=12345678"
                        + "&ACNT_PRDT_CD=01"
                        + "&AFHR_FLPR_YN=N"
                        + "&OFL_YN="
                        + "&INQR_DVSN=02"
                        + "&UNPR_DVSN=01"
                        + "&FUND_STTL_ICLD_YN=N"
                        + "&FNCG_AMT_AUTO_RDPT_YN=N"
                        + "&PRCS_DVSN=00"
                        + "&CTX_AREA_FK100="
                        + "&CTX_AREA_NK100="))
                .andExpect(method(GET))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY))
                .andExpect(header("appsecret", APP_SECRET))
                .andExpect(header("tr_id", "VTTC8434R"))
                .andExpect(queryParam("CANO", ACCOUNT_NUMBER))
                .andExpect(queryParam("ACNT_PRDT_CD", ACCOUNT_PRODUCT_CODE))
                .andExpect(queryParam("INQR_DVSN", "02"))
                .andRespond(withSuccess(
                        """
                                {
                                  "rt_cd": "0",
                                  "msg_cd": "KIOK0560",
                                  "msg1": "Request completed successfully.",
                                  "output1": [
                                    {
                                      "pdno": "005930",
                                      "hldg_qty": "10",
                                      "pchs_avg_pric": "70000.0000",
                                      "evlu_amt": "720000"
                                    }
                                  ],
                                  "output2": [
                                    {
                                      "dnca_tot_amt": "9280000",
                                      "tot_evlu_amt": "10000000"
                                    }
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisAccountBalanceResponse response = client.getAccountBalance(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.output1()).hasSize(1);
        assertThat(response.output1().getFirst().symbol()).isEqualTo("005930");
        assertThat(response.output2()).hasSize(1);
        server.verify();
    }

    @Test
    void requestsNextPageAndMergesPositions() {
        server.expect(requestTo(accountBalanceUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse("005930", "next-fk", "next-nk"),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));
        server.expect(requestTo(accountBalanceUrl("next-fk", "next-nk")))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(
                        successfulResponse("000660", "", ""),
                        MediaType.APPLICATION_JSON
                ));

        KisAccountBalanceResponse response = client.getAccountBalance(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        );

        assertThat(response.output1())
                .extracting(position -> position.symbol())
                .containsExactly("005930", "000660");
        assertThat(response.output2()).hasSize(1);
        server.verify();
    }

    @Test
    void returnsFailureWithoutPartialPositionsWhenNextPageFails() {
        server.expect(requestTo(accountBalanceUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse("005930", "next-fk", "next-nk"),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));
        server.expect(requestTo(accountBalanceUrl("next-fk", "next-nk")))
                .andExpect(header("tr_cont", "N"))
                .andRespond(withSuccess(
                        """
                                {
                                  "rt_cd": "1",
                                  "msg_cd": "EGW00123",
                                  "msg1": "Request failed.",
                                  "output1": [],
                                  "output2": []
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        KisAccountBalanceResponse response = client.getAccountBalance(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        );

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.output1()).isEmpty();
        server.verify();
    }

    @Test
    void rejectsPaginationBeyondMaxPages() {
        createClient(1);
        server.expect(requestTo(accountBalanceUrl("", "")))
                .andRespond(withSuccess(
                        successfulResponse("005930", "next-fk", "next-nk"),
                        MediaType.APPLICATION_JSON
                ).header("tr_cont", "M"));

        assertThatIllegalStateException()
                .isThrownBy(() -> client.getAccountBalance(
                        ACCOUNT_NUMBER,
                        ACCOUNT_PRODUCT_CODE,
                        ACCESS_TOKEN
                ))
                .withMessage(
                        "KIS account balance pagination exceeded maxPages=1."
                );
        server.verify();
    }

    @Test
    void rejectsBlankAccountNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getAccountBalance(
                        " ",
                        ACCOUNT_PRODUCT_CODE,
                        ACCESS_TOKEN
                ))
                .withMessage("accountNumber must not be blank.");
    }

    @Test
    void rejectsBlankAccountProductCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getAccountBalance(
                        ACCOUNT_NUMBER,
                        " ",
                        ACCESS_TOKEN
                ))
                .withMessage("accountProductCode must not be blank.");
    }

    @Test
    void rejectsBlankAccessToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.getAccountBalance(
                        ACCOUNT_NUMBER,
                        ACCOUNT_PRODUCT_CODE,
                        " "
                ))
                .withMessage("accessToken must not be blank.");
    }

    private String accountBalanceUrl(
            String contextAreaFk100,
            String contextAreaNk100
    ) {
        return BASE_URL
                + "/uapi/domestic-stock/v1/trading/inquire-balance"
                + "?CANO=12345678"
                + "&ACNT_PRDT_CD=01"
                + "&AFHR_FLPR_YN=N"
                + "&OFL_YN="
                + "&INQR_DVSN=02"
                + "&UNPR_DVSN=01"
                + "&FUND_STTL_ICLD_YN=N"
                + "&FNCG_AMT_AUTO_RDPT_YN=N"
                + "&PRCS_DVSN=00"
                + "&CTX_AREA_FK100=" + contextAreaFk100
                + "&CTX_AREA_NK100=" + contextAreaNk100;
    }

    private String successfulResponse(
            String symbol,
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
                      "pdno": "%s",
                      "hldg_qty": "1",
                      "pchs_avg_pric": "70000.0000",
                      "evlu_amt": "72000"
                    }
                  ],
                  "output2": [
                    {
                      "dnca_tot_amt": "9928000",
                      "tot_evlu_amt": "10000000"
                    }
                  ],
                  "ctx_area_fk100": "%s",
                  "ctx_area_nk100": "%s"
                }
                """.formatted(symbol, contextAreaFk100, contextAreaNk100);
    }
}
