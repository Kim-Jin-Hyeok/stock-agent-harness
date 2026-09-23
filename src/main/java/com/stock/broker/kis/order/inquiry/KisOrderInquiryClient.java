package com.stock.broker.kis.order.inquiry;

import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryOutput;
import com.stock.broker.kis.order.inquiry.dto.KisOrderInquiryResponse;
import com.stock.broker.order.BrokerOrderReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KisOrderInquiryClient {
    private static final String ORDER_INQUIRY_PATH =
            "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";
    private static final String PAPER_ORDER_INQUIRY_TRANSACTION_ID =
            "VTTC0081R";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;
    private final int maxPages;

    public KisOrderInquiryClient(
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

    public KisOrderInquiryResponse getOrderExecutions(
            String accountNumber,
            String accountProductCode,
            LocalDate startDate,
            LocalDate endDate,
            BrokerOrderReference reference,
            String symbol,
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
        Objects.requireNonNull(startDate, "startDate must not be null.");
        Objects.requireNonNull(endDate, "endDate must not be null.");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate must not be after endDate."
            );
        }
        Objects.requireNonNull(reference, "reference must not be null.");
        String validatedSymbol = requireText(symbol, "symbol");
        String validatedAccessToken = requireText(accessToken, "accessToken");

        List<KisOrderInquiryOutput> outputs = new ArrayList<>();
        String contextAreaFk100 = "";
        String contextAreaNk100 = "";
        String requestContinuation = "";

        for (int page = 1; page <= maxPages; page++) {
            ResponseEntity<KisOrderInquiryResponse> entity = requestPage(
                    validatedAccountNumber,
                    validatedAccountProductCode,
                    startDate,
                    endDate,
                    reference,
                    validatedSymbol,
                    validatedAccessToken,
                    contextAreaFk100,
                    contextAreaNk100,
                    requestContinuation
            );
            KisOrderInquiryResponse response = Objects.requireNonNull(
                    entity.getBody(),
                    "KIS order inquiry response must not be null."
            );

            if (!response.isSuccessful()) {
                return response;
            }
            if (response.output1() == null) {
                throw new IllegalStateException(
                        "KIS order inquiry output1 must not be null."
                );
            }

            outputs.addAll(response.output1());

            if (!hasNextPage(entity)) {
                return new KisOrderInquiryResponse(
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
                        "KIS order inquiry pagination exceeded maxPages="
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
                "KIS order inquiry pagination ended unexpectedly."
        );
    }

    private ResponseEntity<KisOrderInquiryResponse> requestPage(
            String accountNumber,
            String accountProductCode,
            LocalDate startDate,
            LocalDate endDate,
            BrokerOrderReference reference,
            String symbol,
            String accessToken,
            String contextAreaFk100,
            String contextAreaNk100,
            String requestContinuation
    ) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ORDER_INQUIRY_PATH)
                        .queryParam("CANO", accountNumber)
                        .queryParam("ACNT_PRDT_CD", accountProductCode)
                        .queryParam(
                                "INQR_STRT_DT",
                                startDate.format(DATE_FORMATTER)
                        )
                        .queryParam(
                                "INQR_END_DT",
                                endDate.format(DATE_FORMATTER)
                        )
                        .queryParam("SLL_BUY_DVSN_CD", "00")
                        .queryParam("PDNO", symbol)
                        .queryParam("CCLD_DVSN", "00")
                        .queryParam("INQR_DVSN", "00")
                        .queryParam("INQR_DVSN_3", "00")
                        .queryParam(
                                "ORD_GNO_BRNO",
                                reference.organizationNumber()
                        )
                        .queryParam("ODNO", reference.orderId())
                        .queryParam("INQR_DVSN_1", "")
                        .queryParam("CTX_AREA_FK100", contextAreaFk100)
                        .queryParam("CTX_AREA_NK100", contextAreaNk100)
                        .queryParam("EXCG_ID_DVSN_CD", "KRX")
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", PAPER_ORDER_INQUIRY_TRANSACTION_ID);

        if (!requestContinuation.isBlank()) {
            request = request.header("tr_cont", requestContinuation);
        }

        return request.retrieve().toEntity(KisOrderInquiryResponse.class);
    }

    private boolean hasNextPage(
            ResponseEntity<KisOrderInquiryResponse> responseEntity
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
