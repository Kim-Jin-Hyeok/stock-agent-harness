package com.stock.market.price.history.provider.kis;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.provider.kis.dto.KisDailyPriceHistoryResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class KisDailyPriceHistoryClient {
    private static final String DAILY_PRICE_HISTORY_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String DAILY_PRICE_HISTORY_TRANSACTION_ID =
            "FHKST03010100";
    private static final String KRX_MARKET_CODE = "J";
    private static final String DAILY_PERIOD_CODE = "D";
    private static final String ADJUSTED_PRICE_CODE = "0";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;

    public KisDailyPriceHistoryClient(
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

    public KisDailyPriceHistoryResponse getDailyPriceHistoryPage(
            DailyPriceHistoryRequest request,
            String accessToken
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        String validatedAccessToken = requireText(accessToken, "accessToken");

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(DAILY_PRICE_HISTORY_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", KRX_MARKET_CODE)
                        .queryParam("FID_INPUT_ISCD", request.symbol())
                        .queryParam(
                                "FID_INPUT_DATE_1",
                                formatDate(request.fromDate())
                        )
                        .queryParam(
                                "FID_INPUT_DATE_2",
                                formatDate(request.toDate())
                        )
                        .queryParam("FID_PERIOD_DIV_CODE", DAILY_PERIOD_CODE)
                        .queryParam("FID_ORG_ADJ_PRC", ADJUSTED_PRICE_CODE)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("authorization", "Bearer " + validatedAccessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", DAILY_PRICE_HISTORY_TRANSACTION_ID)
                .retrieve()
                .body(KisDailyPriceHistoryResponse.class);
    }

    private String formatDate(LocalDate date) {
        return DateTimeFormatter.BASIC_ISO_DATE.format(date);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
