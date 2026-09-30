package com.stock.market.index.history.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;
import com.stock.market.index.history.provider.error.MarketIndexDailyHistoryProviderException;
import com.stock.market.index.history.provider.error.MarketIndexDailyHistoryProviderFailureType;
import com.stock.market.index.history.provider.kis.dto.KisMarketIndexDailyHistoryResponse;
import com.stock.market.index.history.provider.kis.dto.KisMarketIndexDailyObservationOutput;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KisMarketIndexDailyHistoryProvider
        implements MarketIndexDailyHistoryProvider {
    private static final String KIS_RATE_LIMIT_MESSAGE_CODE = "EGW00201";

    private final KisMarketIndexDailyHistoryClient historyClient;
    private final KisTokenProvider tokenProvider;
    private final KisMarketIndexDailyHistoryRequestWaiter requestWaiter;
    private final int maxPages;

    public KisMarketIndexDailyHistoryProvider(
            KisMarketIndexDailyHistoryClient historyClient,
            KisTokenProvider tokenProvider,
            KisMarketIndexDailyHistoryRequestWaiter requestWaiter,
            int maxPages
    ) {
        this.historyClient = Objects.requireNonNull(
                historyClient,
                "historyClient must not be null."
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
            throw new IllegalArgumentException(
                    "maxPages must be positive."
            );
        }
        this.maxPages = maxPages;
    }

    @Override
    public MarketIndexDailyHistory getDailyHistory(
            MarketIndexDailyHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        try {
            String accessToken = tokenProvider.getAccessToken();
            List<MarketIndexDailyObservation> observations =
                    new ArrayList<>();
            LocalDate pageToDate = request.toDate();

            for (int page = 0; page < maxPages; page++) {
                MarketIndexDailyHistoryRequest pageRequest =
                        new MarketIndexDailyHistoryRequest(
                                request.benchmarkId(),
                                request.fromDate(),
                                pageToDate
                        );
                requestWaiter.waitBeforeRequest();
                List<MarketIndexDailyObservation> pageObservations =
                        getPage(pageRequest, accessToken);
                observations.addAll(pageObservations);

                if (pageObservations.isEmpty()) {
                    return history(request, observations);
                }

                LocalDate oldestObservationDate = pageObservations.stream()
                        .map(MarketIndexDailyObservation::observationDate)
                        .min(LocalDate::compareTo)
                        .orElseThrow();
                if (!oldestObservationDate.isAfter(request.fromDate())) {
                    return history(request, observations);
                }

                LocalDate nextPageToDate = oldestObservationDate.minusDays(1);
                if (!nextPageToDate.isBefore(pageToDate)) {
                    throw permanentFailure(
                            "KIS market index daily history pagination "
                                    + "did not advance."
                    );
                }
                pageToDate = nextPageToDate;
                if (pageToDate.isBefore(request.fromDate())) {
                    return history(request, observations);
                }
            }

            throw permanentFailure(
                    "KIS market index daily history exceeded maxPages="
                            + maxPages + "."
            );
        } catch (MarketIndexDailyHistoryProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new MarketIndexDailyHistoryProviderException(
                    MarketIndexDailyHistoryProviderFailureType.TEMPORARY,
                    "KIS market index daily history request failed "
                            + "temporarily.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MarketIndexDailyHistoryProviderException(
                    MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                    "KIS market index daily history response is invalid.",
                    exception
            );
        }
    }

    private List<MarketIndexDailyObservation> getPage(
            MarketIndexDailyHistoryRequest request,
            String accessToken
    ) {
        KisMarketIndexDailyHistoryResponse response =
                historyClient.getDailyHistoryPage(request, accessToken);
        if (response == null) {
            throw permanentFailure(
                    "KIS market index daily history response must not be "
                            + "null."
            );
        }
        if (!response.isSuccessful()) {
            throw failedResponse(response);
        }
        if (response.output() == null) {
            throw permanentFailure(
                    "KIS market index daily history output must not be null."
            );
        }

        return response.output().stream()
                .map(output -> toObservation(output, request))
                .toList();
    }

    private MarketIndexDailyObservation toObservation(
            KisMarketIndexDailyObservationOutput output,
            MarketIndexDailyHistoryRequest request
    ) {
        if (output == null) {
            throw new IllegalStateException(
                    "KIS market index daily history observation must not "
                            + "be null."
            );
        }
        MarketIndexDailyObservation observation = output.toObservation();
        if (observation.observationDate().isBefore(request.fromDate())
                || observation.observationDate().isAfter(request.toDate())) {
            throw new IllegalStateException(
                    "KIS market index daily history observation is outside "
                            + "requested range."
            );
        }
        return observation;
    }

    private MarketIndexDailyHistory history(
            MarketIndexDailyHistoryRequest request,
            List<MarketIndexDailyObservation> observations
    ) {
        return new MarketIndexDailyHistory(
                request.benchmarkId(),
                observations
        );
    }

    private MarketIndexDailyHistoryProviderException failedResponse(
            KisMarketIndexDailyHistoryResponse response
    ) {
        String message = "KIS market index daily history response was not "
                + "successful. messageCode="
                + response.messageCode()
                + ", message="
                + response.message();
        MarketIndexDailyHistoryProviderFailureType failureType =
                KIS_RATE_LIMIT_MESSAGE_CODE.equals(response.messageCode())
                        ? MarketIndexDailyHistoryProviderFailureType.TEMPORARY
                        : MarketIndexDailyHistoryProviderFailureType.PERMANENT;
        return new MarketIndexDailyHistoryProviderException(
                failureType,
                message
        );
    }

    private MarketIndexDailyHistoryProviderException httpFailure(
            RestClientResponseException exception
    ) {
        boolean temporary = exception.getStatusCode().value() == 429
                || exception.getStatusCode().is5xxServerError();
        MarketIndexDailyHistoryProviderFailureType failureType = temporary
                ? MarketIndexDailyHistoryProviderFailureType.TEMPORARY
                : MarketIndexDailyHistoryProviderFailureType.PERMANENT;
        String message = temporary
                ? "KIS market index daily history request failed "
                        + "temporarily."
                : "KIS market index daily history request failed "
                        + "permanently.";
        return new MarketIndexDailyHistoryProviderException(
                failureType,
                message,
                exception
        );
    }

    private MarketIndexDailyHistoryProviderException permanentFailure(
            String message
    ) {
        return new MarketIndexDailyHistoryProviderException(
                MarketIndexDailyHistoryProviderFailureType.PERMANENT,
                message
        );
    }
}
