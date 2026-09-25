package com.stock.market.price.history.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderException;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderFailureType;
import com.stock.market.price.history.provider.kis.dto.KisDailyPriceBarOutput;
import com.stock.market.price.history.provider.kis.dto.KisDailyPriceHistoryResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KisDailyPriceHistoryProvider implements DailyPriceHistoryProvider {
    private static final int KIS_PAGE_SIZE = 100;
    private static final String KIS_RATE_LIMIT_MESSAGE_CODE = "EGW00201";

    private final KisDailyPriceHistoryClient dailyPriceHistoryClient;
    private final KisTokenProvider tokenProvider;
    private final KisDailyPriceHistoryRequestWaiter requestWaiter;
    private final int maxPages;

    public KisDailyPriceHistoryProvider(
            KisDailyPriceHistoryClient dailyPriceHistoryClient,
            KisTokenProvider tokenProvider,
            KisDailyPriceHistoryRequestWaiter requestWaiter,
            int maxPages
    ) {
        this.dailyPriceHistoryClient = Objects.requireNonNull(
                dailyPriceHistoryClient,
                "dailyPriceHistoryClient must not be null."
        );
        this.tokenProvider = Objects.requireNonNull(
                tokenProvider,
                "tokenProvider must not be null."
        );
        this.requestWaiter = Objects.requireNonNull(
                requestWaiter,
                "requestWaiter must not be null."
        );
        if (maxPages <= 0) {
            throw new IllegalArgumentException("maxPages must be positive.");
        }
        this.maxPages = maxPages;
    }

    @Override
    public DailyPriceHistory getDailyPriceHistory(
            DailyPriceHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        try {
            String accessToken = tokenProvider.getAccessToken();
            List<DailyPriceBar> bars = new ArrayList<>();
            LocalDate pageToDate = request.toDate();

            for (int page = 0; page < maxPages; page++) {
                DailyPriceHistoryRequest pageRequest =
                        new DailyPriceHistoryRequest(
                                request.symbol(),
                                request.fromDate(),
                                pageToDate
                        );
                requestWaiter.waitBeforeRequest();
                List<DailyPriceBar> pageBars = getPage(
                        pageRequest,
                        accessToken
                );
                bars.addAll(pageBars);

                if (pageBars.isEmpty()) {
                    return new DailyPriceHistory(request.symbol(), bars);
                }

                LocalDate oldestTradingDate = pageBars.stream()
                        .map(DailyPriceBar::tradingDate)
                        .min(LocalDate::compareTo)
                        .orElseThrow();

                if (pageBars.size() < KIS_PAGE_SIZE
                        || !oldestTradingDate.isAfter(request.fromDate())) {
                    return new DailyPriceHistory(request.symbol(), bars);
                }

                pageToDate = oldestTradingDate.minusDays(1);
                if (pageToDate.isBefore(request.fromDate())) {
                    return new DailyPriceHistory(request.symbol(), bars);
                }
            }

            throw permanentFailure(
                    "KIS daily price history exceeded maxPages=" + maxPages + "."
            );
        } catch (DailyPriceHistoryProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new DailyPriceHistoryProviderException(
                    DailyPriceHistoryProviderFailureType.TEMPORARY,
                    "KIS daily price history request failed temporarily.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new DailyPriceHistoryProviderException(
                    DailyPriceHistoryProviderFailureType.PERMANENT,
                    "KIS daily price history response is invalid.",
                    exception
            );
        }
    }

    private List<DailyPriceBar> getPage(
            DailyPriceHistoryRequest request,
            String accessToken
    ) {
        KisDailyPriceHistoryResponse response =
                dailyPriceHistoryClient.getDailyPriceHistoryPage(
                        request,
                        accessToken
                );
        if (response == null) {
            throw permanentFailure(
                    "KIS daily price history response must not be null."
            );
        }
        if (!response.isSuccessful()) {
            throw failedResponse(response);
        }
        if (response.output() == null) {
            throw permanentFailure(
                    "KIS daily price history output must not be null."
            );
        }
        if (response.output().size() > KIS_PAGE_SIZE) {
            throw permanentFailure(
                    "KIS daily price history output must not exceed "
                            + KIS_PAGE_SIZE
                            + " bars."
            );
        }

        return response.output().stream()
                .map(output -> toBar(output, request))
                .toList();
    }

    private DailyPriceBar toBar(
            KisDailyPriceBarOutput output,
            DailyPriceHistoryRequest request
    ) {
        if (output == null) {
            throw new IllegalStateException(
                    "KIS daily price history bar must not be null."
            );
        }

        DailyPriceBar bar = output.toBar();
        if (bar.tradingDate().isBefore(request.fromDate())
                || bar.tradingDate().isAfter(request.toDate())) {
            throw new IllegalStateException(
                    "KIS daily price history bar is outside requested range."
            );
        }
        return bar;
    }

    private DailyPriceHistoryProviderException failedResponse(
            KisDailyPriceHistoryResponse response
    ) {
        String message = "KIS daily price history response was not successful. "
                + "messageCode="
                + response.messageCode()
                + ", message="
                + response.message();
        DailyPriceHistoryProviderFailureType failureType =
                KIS_RATE_LIMIT_MESSAGE_CODE.equals(response.messageCode())
                        ? DailyPriceHistoryProviderFailureType.TEMPORARY
                        : DailyPriceHistoryProviderFailureType.PERMANENT;

        return new DailyPriceHistoryProviderException(failureType, message);
    }

    private DailyPriceHistoryProviderException httpFailure(
            RestClientResponseException exception
    ) {
        boolean temporary = exception.getStatusCode().value() == 429
                || exception.getStatusCode().is5xxServerError();
        DailyPriceHistoryProviderFailureType failureType = temporary
                ? DailyPriceHistoryProviderFailureType.TEMPORARY
                : DailyPriceHistoryProviderFailureType.PERMANENT;
        String message = temporary
                ? "KIS daily price history request failed temporarily."
                : "KIS daily price history request failed permanently.";

        return new DailyPriceHistoryProviderException(
                failureType,
                message,
                exception
        );
    }

    private DailyPriceHistoryProviderException permanentFailure(
            String message
    ) {
        return new DailyPriceHistoryProviderException(
                DailyPriceHistoryProviderFailureType.PERMANENT,
                message
        );
    }
}
