package com.stock.broker.kis.order;

import com.stock.broker.kis.order.dto.KisCashOrderRequest;
import com.stock.broker.kis.order.dto.KisCashOrderResponse;
import com.stock.broker.order.BrokerOrderSide;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public class KisCashOrderClient {
    private static final String CASH_ORDER_PATH =
            "/uapi/domestic-stock/v1/trading/order-cash";
    private static final String PAPER_SELL_TRANSACTION_ID = "VTTC0011U";
    private static final String PAPER_BUY_TRANSACTION_ID = "VTTC0012U";
    private static final String PERSONAL_CUSTOMER_TYPE = "P";
    private static final MediaType KIS_JSON_MEDIA_TYPE =
            MediaType.parseMediaType("application/json; charset=utf-8");

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;

    public KisCashOrderClient(
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

    public KisCashOrderResponse submitLimitOrder(
            BrokerOrderSide side,
            String accountNumber,
            String accountProductCode,
            String symbol,
            long quantity,
            long limitPriceKrw,
            String accessToken
    ) {
        Objects.requireNonNull(side, "side must not be null.");
        String validatedAccessToken = requireText(accessToken, "accessToken");
        KisCashOrderRequest request = KisCashOrderRequest.limitOrder(
                accountNumber,
                accountProductCode,
                side,
                symbol,
                quantity,
                limitPriceKrw
        );

        return restClient.post()
                .uri(CASH_ORDER_PATH)
                .contentType(KIS_JSON_MEDIA_TYPE)
                .header("authorization", "Bearer " + validatedAccessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", transactionId(side))
                .header("custtype", PERSONAL_CUSTOMER_TYPE)
                .body(request)
                .retrieve()
                .body(KisCashOrderResponse.class);
    }

    private String transactionId(BrokerOrderSide side) {
        return side == BrokerOrderSide.BUY
                ? PAPER_BUY_TRANSACTION_ID
                : PAPER_SELL_TRANSACTION_ID;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
