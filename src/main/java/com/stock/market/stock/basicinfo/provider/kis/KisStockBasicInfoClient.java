package com.stock.market.stock.basicinfo.provider.kis;

import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class KisStockBasicInfoClient {
    private static final String BASIC_INFO_PATH = "/uapi/domestic-stock/v1/quotations/search-stock-info";
    private static final String BASIC_INFO_TRANSACTION_ID = "CTPF1002R";
    private static final String PRODUCT_TYPE_CODE = "300";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;
    private final Clock clock;

    public KisStockBasicInfoClient(RestClient restClient, String appKey, String appSecret, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null.");
        this.appKey = requireHeaderValue(appKey, "appKey");
        this.appSecret = requireHeaderValue(appSecret, "appSecret");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    public KisStockBasicInfoRawResponse getStockBasicInfo(String symbol, String accessToken) {
        if (symbol == null || !symbol.matches("[0-9A-Z]{6}")) {
            throw new IllegalArgumentException("symbol must be exactly 6 uppercase alphanumeric characters.");
        }
        String validatedAccessToken = requireHeaderValue(accessToken, "accessToken");
        Instant startedAt = clock.instant();
        try {
            return restClient.get()
                    .uri(builder -> requireReadOnlyUri(builder.path(BASIC_INFO_PATH)
                            .queryParam("PRDT_TYPE_CD", PRODUCT_TYPE_CODE).queryParam("PDNO", symbol).build(), symbol))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validatedAccessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", BASIC_INFO_TRANSACTION_ID)
                    .header("custtype", "P")
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            throw new RequestFailure("KIS stock basic info response HTTP status=" + status + ".");
                        }
                        if (response.getHeaders().getContentLength() > KisStockBasicInfoParser.MAX_CONTENT_BYTES) {
                            throw new RequestFailure("KIS stock basic info content exceeds 1 MiB.");
                        }
                        String encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
                        if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
                            throw new RequestFailure("KIS stock basic info content encoding must be identity.");
                        }
                        // Read only one extra byte to detect an oversized body without buffering it in full.
                        try (var body = response.getBody()) {
                            byte[] content = body.readNBytes(KisStockBasicInfoParser.MAX_CONTENT_BYTES + 1);
                            if (content.length == 0) {
                                throw new RequestFailure("KIS stock basic info content must not be empty.");
                            }
                            if (content.length > KisStockBasicInfoParser.MAX_CONTENT_BYTES) {
                                throw new RequestFailure("KIS stock basic info content exceeds 1 MiB.");
                            }
                            return new KisStockBasicInfoRawResponse(symbol, startedAt, clock.instant(), status, content);
                        }
                    });
        } catch (RequestFailure failure) {
            // A stream-close error may add a sensitive suppressed exception. Do not propagate it.
            throw new IllegalStateException(failure.getMessage());
        } catch (RuntimeException failure) {
            // HTTP bodies, headers and transport exception causes can contain credentials.
            throw new IllegalStateException("KIS stock basic info request failed.");
        }
    }

    private static URI requireReadOnlyUri(URI uri, String symbol) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"openapi.koreainvestment.com".equalsIgnoreCase(uri.getHost()) || uri.getPort() != 9443
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !BASIC_INFO_PATH.equals(uri.getRawPath())
                || !("PRDT_TYPE_CD=" + PRODUCT_TYPE_CODE + "&PDNO=" + symbol).equals(uri.getRawQuery())) {
            throw new RequestFailure("KIS stock basic info requires the isolated live read-only endpoint.");
        }
        return uri;
    }

    private static String requireHeaderValue(String value, String name) {
        if (value == null || value.isBlank() || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException(name + " must be nonblank visible ASCII without whitespace.");
        }
        return value;
    }

    private static final class RequestFailure extends IllegalStateException {
        private RequestFailure(String message) {
            super(message);
        }
    }
}
