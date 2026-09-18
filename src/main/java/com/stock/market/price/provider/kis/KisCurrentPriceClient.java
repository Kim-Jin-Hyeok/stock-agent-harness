package com.stock.market.price.provider.kis;

import com.stock.market.price.provider.kis.dto.KisCurrentPriceResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public class KisCurrentPriceClient {
    private static final String CURRENT_PRICE_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String CURRENT_PRICE_TRANSACTION_ID = "FHKST01010100";
    private static final String KRX_MARKET_CODE = "J";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;

    public KisCurrentPriceClient(
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

    public KisCurrentPriceResponse getCurrentPrice(
            String symbol,
            String accessToken
    ) {
        String validatedSymbol = requireText(symbol, "symbol");
        String validatedAccessToken = requireText(accessToken, "accessToken");

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CURRENT_PRICE_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", KRX_MARKET_CODE)
                        .queryParam("FID_INPUT_ISCD", validatedSymbol)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("authorization", "Bearer " + validatedAccessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", CURRENT_PRICE_TRANSACTION_ID)
                .retrieve()
                .body(KisCurrentPriceResponse.class);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
