package com.stock.broker.kis.account;

import com.stock.broker.kis.account.dto.KisAccountBalanceResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public class KisAccountBalanceClient {
    private static final String ACCOUNT_BALANCE_PATH =
            "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String ACCOUNT_BALANCE_TRANSACTION_ID = "VTTC8434R";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;

    public KisAccountBalanceClient(
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

    public KisAccountBalanceResponse getAccountBalance(
            String accountNumber,
            String accountProductCode,
            String accessToken
    ) {
        String validatedAccountNumber = requireText(
                accountNumber,
                "accountNumber"
        );
        String validatedAccountProductCode = requireText(
                accountProductCode,
                "accountProductCode"
        );
        String validatedAccessToken = requireText(accessToken, "accessToken");

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ACCOUNT_BALANCE_PATH)
                        .queryParam("CANO", validatedAccountNumber)
                        .queryParam("ACNT_PRDT_CD", validatedAccountProductCode)
                        .queryParam("AFHR_FLPR_YN", "N")
                        .queryParam("OFL_YN", "")
                        .queryParam("INQR_DVSN", "02")
                        .queryParam("UNPR_DVSN", "01")
                        .queryParam("FUND_STTL_ICLD_YN", "N")
                        .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                        .queryParam("PRCS_DVSN", "00")
                        .queryParam("CTX_AREA_FK100", "")
                        .queryParam("CTX_AREA_NK100", "")
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("authorization", "Bearer " + validatedAccessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", ACCOUNT_BALANCE_TRANSACTION_ID)
                .retrieve()
                .body(KisAccountBalanceResponse.class);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
