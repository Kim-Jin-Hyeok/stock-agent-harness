package com.stock.broker.kis.order.cancellation;

import com.stock.broker.kis.order.cancellation.dto.KisOrderCancellationRequest;
import com.stock.broker.kis.order.cancellation.dto.KisOrderCancellationResponse;
import com.stock.broker.order.BrokerOrderReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public class KisOrderCancellationClient {
    private static final String ORDER_CANCELLATION_PATH =
            "/uapi/domestic-stock/v1/trading/order-rvsecncl";
    private static final String PAPER_CANCELLATION_TRANSACTION_ID =
            "VTTC0013U";
    private static final String PERSONAL_CUSTOMER_TYPE = "P";
    private static final MediaType KIS_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/json; charset=utf-8");

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;

    public KisOrderCancellationClient(
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

    public KisOrderCancellationResponse cancelAllRemaining(
            String accountNumber,
            String accountProductCode,
            BrokerOrderReference originalReference,
            String orderDivision,
            long cancelableQuantity,
            long originalOrderPriceKrw,
            String exchangeId,
            String accessToken
    ) {
        String validatedAccessToken = requireText(accessToken, "accessToken");
        KisOrderCancellationRequest request =
                KisOrderCancellationRequest.allRemaining(
                        accountNumber,
                        accountProductCode,
                        originalReference,
                        orderDivision,
                        cancelableQuantity,
                        originalOrderPriceKrw,
                        exchangeId
                );

        return restClient.post()
                .uri(ORDER_CANCELLATION_PATH)
                .contentType(KIS_JSON_MEDIA_TYPE)
                .header("authorization", "Bearer " + validatedAccessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", PAPER_CANCELLATION_TRANSACTION_ID)
                .header("custtype", PERSONAL_CUSTOMER_TYPE)
                .body(request)
                .retrieve()
                .body(KisOrderCancellationResponse.class);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
