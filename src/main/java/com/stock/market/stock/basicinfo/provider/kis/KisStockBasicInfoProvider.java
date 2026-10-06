package com.stock.market.stock.basicinfo.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;

import java.util.Objects;

public class KisStockBasicInfoProvider {
    private final KisStockBasicInfoClient client;
    private final KisTokenProvider tokenProvider;

    public KisStockBasicInfoProvider(KisStockBasicInfoClient client, KisTokenProvider tokenProvider) {
        this.client = Objects.requireNonNull(client, "client must not be null.");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null.");
    }

    public KisStockBasicInfoRawResponse getStockBasicInfo(String symbol) {
        KisStockBasicInfoClient.validateSymbol(symbol);
        String accessToken = tokenProvider.getAccessToken();
        KisStockBasicInfoRawResponse response = client.getStockBasicInfo(symbol, accessToken);
        if (response == null) {
            throw new IllegalStateException("KIS stock basic info response must not be null.");
        }
        return response;
    }
}
