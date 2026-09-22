package com.stock.broker.kis.account;

import com.stock.broker.kis.account.dto.KisAccountBalancePositionOutput;
import com.stock.broker.kis.account.dto.KisAccountBalanceResponse;
import com.stock.broker.kis.account.dto.KisAccountBalanceSummaryOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KisAccountBalanceClient {
    private static final String ACCOUNT_BALANCE_PATH =
            "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String ACCOUNT_BALANCE_TRANSACTION_ID = "VTTC8434R";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;
    private final int maxPages;

    public KisAccountBalanceClient(
            RestClient restClient,
            String appKey,
            String appSecret,
            int maxPages
    ) {
        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null."
        );
        this.appKey = requireText(appKey, "appKey");
        this.appSecret = requireText(appSecret, "appSecret");
        if (maxPages <= 0) {
            throw new IllegalArgumentException("maxPages must be positive.");
        }
        this.maxPages = maxPages;
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

        List<KisAccountBalancePositionOutput> positions = new ArrayList<>();
        List<KisAccountBalanceSummaryOutput> summaries = null;
        String contextAreaFk100 = "";
        String contextAreaNk100 = "";
        String requestContinuation = "";

        for (int page = 1; page <= maxPages; page++) {
            ResponseEntity<KisAccountBalanceResponse> entity = requestPage(
                    validatedAccountNumber,
                    validatedAccountProductCode,
                    validatedAccessToken,
                    contextAreaFk100,
                    contextAreaNk100,
                    requestContinuation
            );
            KisAccountBalanceResponse response = Objects.requireNonNull(
                    entity.getBody(),
                    "KIS account balance response must not be null."
            );

            if (!response.isSuccessful()) {
                return response;
            }
            if (response.output1() == null) {
                throw new IllegalStateException(
                        "KIS account balance output1 must not be null."
                );
            }

            positions.addAll(response.output1());
            if (summaries == null) {
                summaries = response.output2();
            }

            if (!hasNextPage(entity)) {
                return new KisAccountBalanceResponse(
                        response.resultCode(),
                        response.messageCode(),
                        response.message(),
                        List.copyOf(positions),
                        summaries,
                        response.contextAreaFk100(),
                        response.contextAreaNk100()
                );
            }
            if (page == maxPages) {
                throw new IllegalStateException(
                        "KIS account balance pagination exceeded maxPages="
                                + maxPages + "."
                );
            }

            contextAreaFk100 = requireContinuationValue(
                    response.contextAreaFk100(),
                    "contextAreaFk100"
            );
            contextAreaNk100 = requireContinuationValue(
                    response.contextAreaNk100(),
                    "contextAreaNk100"
            );
            requestContinuation = "N";
        }

        throw new IllegalStateException(
                "KIS account balance pagination ended unexpectedly."
        );
    }

    private ResponseEntity<KisAccountBalanceResponse> requestPage(
            String accountNumber,
            String accountProductCode,
            String accessToken,
            String contextAreaFk100,
            String contextAreaNk100,
            String requestContinuation
    ) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ACCOUNT_BALANCE_PATH)
                        .queryParam("CANO", accountNumber)
                        .queryParam("ACNT_PRDT_CD", accountProductCode)
                        .queryParam("AFHR_FLPR_YN", "N")
                        .queryParam("OFL_YN", "")
                        .queryParam("INQR_DVSN", "02")
                        .queryParam("UNPR_DVSN", "01")
                        .queryParam("FUND_STTL_ICLD_YN", "N")
                        .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                        .queryParam("PRCS_DVSN", "00")
                        .queryParam("CTX_AREA_FK100", contextAreaFk100)
                        .queryParam("CTX_AREA_NK100", contextAreaNk100)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", ACCOUNT_BALANCE_TRANSACTION_ID);

        if (!requestContinuation.isBlank()) {
            request = request.header("tr_cont", requestContinuation);
        }

        return request.retrieve().toEntity(KisAccountBalanceResponse.class);
    }

    private boolean hasNextPage(
            ResponseEntity<KisAccountBalanceResponse> responseEntity
    ) {
        String continuation = responseEntity.getHeaders().getFirst("tr_cont");
        return "M".equalsIgnoreCase(continuation)
                || "F".equalsIgnoreCase(continuation);
    }

    private String requireContinuationValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank.");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
