package com.stock.market.index.history.provider.kis;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.provider.kis.dto.KisMarketIndexDailyHistoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
public class KisMarketIndexDailyHistoryClient {
    private static final String DAILY_INDEX_HISTORY_PATH =
            "/uapi/domestic-stock/v1/quotations/"
                    + "inquire-daily-indexchartprice";
    private static final String DAILY_INDEX_HISTORY_TRANSACTION_ID =
            "FHKUP03500100";
    private static final String INDEX_MARKET_DIVISION_CODE = "U";
    private static final String DAILY_PERIOD_CODE = "D";

    private final RestClient restClient;
    private final String appKey;
    private final String appSecret;
    private final Clock clock;

    public KisMarketIndexDailyHistoryClient(
            RestClient restClient,
            String appKey,
            String appSecret,
            Clock clock
    ) {
        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null."
        );
        this.appKey = requireText(appKey, "appKey");
        this.appSecret = requireText(appSecret, "appSecret");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    public KisMarketIndexDailyHistoryResponse getDailyHistoryPage(
            MarketIndexDailyHistoryRequest request,
            String accessToken
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        String validatedAccessToken = requireText(
                accessToken,
                "accessToken"
        );
        KisMarketIndex marketIndex = KisMarketIndex.fromBenchmarkId(
                request.benchmarkId()
        );

        Instant startedAt = clock.instant();
        try {
            KisMarketIndexDailyHistoryResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DAILY_INDEX_HISTORY_PATH)
                            .queryParam(
                                    "FID_COND_MRKT_DIV_CODE",
                                    INDEX_MARKET_DIVISION_CODE
                            )
                            .queryParam(
                                    "FID_INPUT_ISCD",
                                    marketIndex.inputCode()
                            )
                            .queryParam(
                                    "FID_INPUT_DATE_1",
                                    formatDate(request.fromDate())
                            )
                            .queryParam(
                                    "FID_INPUT_DATE_2",
                                    formatDate(request.toDate())
                            )
                            .queryParam(
                                    "FID_PERIOD_DIV_CODE",
                                    DAILY_PERIOD_CODE
                            )
                            .build())
                    .header(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                    )
                    .header(
                            "authorization",
                            "Bearer " + validatedAccessToken
                    )
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header(
                            "tr_id",
                            DAILY_INDEX_HISTORY_TRANSACTION_ID
                    )
                    .retrieve()
                    .body(KisMarketIndexDailyHistoryResponse.class);
            logResponse(request, startedAt, response);
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                    "KIS market index daily history request failed. "
                            + "benchmarkId={}, fromDate={}, toDate={}, "
                            + "durationMs={}, failureType={}",
                    request.benchmarkId(),
                    request.fromDate(),
                    request.toDate(),
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private void logResponse(
            MarketIndexDailyHistoryRequest request,
            Instant startedAt,
            KisMarketIndexDailyHistoryResponse response
    ) {
        boolean successful = response != null && response.isSuccessful();
        String messageCode = response == null
                ? null
                : response.messageCode();
        int outputCount = response == null || response.output() == null
                ? 0
                : response.output().size();
        log.info(
                "KIS market index daily history request completed. "
                        + "status={}, benchmarkId={}, fromDate={}, toDate={}, "
                        + "durationMs={}, messageCode={}, outputCount={}",
                successful ? "SUCCESS" : "FAILED",
                request.benchmarkId(),
                request.fromDate(),
                request.toDate(),
                elapsedMillis(startedAt),
                messageCode,
                outputCount
        );
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, clock.instant()).toMillis();
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
