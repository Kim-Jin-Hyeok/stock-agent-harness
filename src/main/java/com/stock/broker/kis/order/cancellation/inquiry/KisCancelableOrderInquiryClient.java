package com.stock.broker.kis.order.cancellation.inquiry;

import com.stock.broker.kis.order.cancellation.inquiry.dto.KisCancelableOrderOutput;
import com.stock.broker.kis.order.cancellation.inquiry.dto.KisCancelableOrderResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KisCancelableOrderInquiryClient {
    private static final String CANCELABLE_ORDER_INQUIRY_PATH =
            "/uapi/domestic-stock/v1/trading/inquire-psbl-rvsecncl";
    private static final String CANCELABLE_ORDER_INQUIRY_TRANSACTION_ID =
            "TTTC0084R";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;
    private final int maxPages;

    public KisCancelableOrderInquiryClient(
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

    public KisCancelableOrderResponse getCancelableOrders(
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

        List<KisCancelableOrderOutput> outputs = new ArrayList<>();
        String contextAreaFk100 = "";
        String contextAreaNk100 = "";
        String requestContinuation = "";

        for (int page = 1; page <= maxPages; page++) {
            ResponseEntity<KisCancelableOrderResponse> entity = requestPage(
                    validatedAccountNumber,
                    validatedAccountProductCode,
                    validatedAccessToken,
                    contextAreaFk100,
                    contextAreaNk100,
                    requestContinuation
            );
            KisCancelableOrderResponse response = Objects.requireNonNull(
                    entity.getBody(),
                    "KIS cancelable order response must not be null."
            );

            if (!response.isSuccessful()) {
                return response;
            }
            if (response.output() == null) {
                throw new IllegalStateException(
                        "KIS cancelable order output must not be null."
                );
            }

            outputs.addAll(response.output());

            if (!hasNextPage(entity)) {
                return new KisCancelableOrderResponse(
                        response.resultCode(),
                        response.messageCode(),
                        response.message(),
                        List.copyOf(outputs),
                        response.contextAreaFk100(),
                        response.contextAreaNk100()
                );
            }
            if (page == maxPages) {
                throw new IllegalStateException(
                        "KIS cancelable order pagination exceeded maxPages="
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
                "KIS cancelable order pagination ended unexpectedly."
        );
    }

    private ResponseEntity<KisCancelableOrderResponse> requestPage(
            String accountNumber,
            String accountProductCode,
            String accessToken,
            String contextAreaFk100,
            String contextAreaNk100,
            String requestContinuation
    ) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CANCELABLE_ORDER_INQUIRY_PATH)
                        .queryParam("CANO", accountNumber)
                        .queryParam("ACNT_PRDT_CD", accountProductCode)
                        .queryParam("INQR_DVSN_1", "1")
                        .queryParam("INQR_DVSN_2", "0")
                        .queryParam("CTX_AREA_FK100", contextAreaFk100)
                        .queryParam("CTX_AREA_NK100", contextAreaNk100)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", CANCELABLE_ORDER_INQUIRY_TRANSACTION_ID);

        if (!requestContinuation.isBlank()) {
            request = request.header("tr_cont", requestContinuation);
        }

        return request.retrieve().toEntity(KisCancelableOrderResponse.class);
    }

    private boolean hasNextPage(
            ResponseEntity<KisCancelableOrderResponse> responseEntity
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
