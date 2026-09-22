package com.stock.market.price.provider.kis;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.error.CurrentPriceProviderException;
import com.stock.market.price.provider.error.CurrentPriceProviderFailureType;
import com.stock.market.price.provider.kis.dto.KisCurrentPriceResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.util.Objects;

public class KisCurrentPriceProvider implements CurrentPriceProvider {
    private final KisCurrentPriceClient currentPriceClient;
    private final KisTokenProvider tokenProvider;
    private final Clock clock;

    public KisCurrentPriceProvider(
            KisCurrentPriceClient currentPriceClient,
            KisTokenProvider tokenProvider,
            Clock clock
    ) {
        this.currentPriceClient = Objects.requireNonNull(
                currentPriceClient,
                "currentPriceClient must not be null."
        );
        this.tokenProvider = Objects.requireNonNull(
                tokenProvider,
                "tokenProvider must not be null."
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Override
    public CurrentPriceSnapshot getCurrentPrice(String symbol) {
        try {
            String accessToken = tokenProvider.getAccessToken();
            KisCurrentPriceResponse response = currentPriceClient.getCurrentPrice(
                    symbol,
                    accessToken
            );

            if (response == null) {
                throw permanentFailure(
                        "KIS current price response must not be null."
                );
            }
            if (!response.isSuccessful()) {
                throw permanentFailure(
                        "KIS current price response was not successful. messageCode="
                                + response.messageCode()
                );
            }
            if (response.output() == null) {
                throw permanentFailure(
                        "KIS current price output must not be null."
                );
            }

            return response.output().toSnapshot(symbol, clock.instant());
        } catch (CurrentPriceProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new CurrentPriceProviderException(
                    CurrentPriceProviderFailureType.TEMPORARY,
                    "KIS current price request failed temporarily.",
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new CurrentPriceProviderException(
                    CurrentPriceProviderFailureType.PERMANENT,
                    "KIS current price response is invalid.",
                    exception
            );
        }
    }

    private CurrentPriceProviderException httpFailure(
            RestClientResponseException exception
    ) {
        boolean temporary = exception.getStatusCode().value() == 429
                || exception.getStatusCode().is5xxServerError();
        CurrentPriceProviderFailureType failureType = temporary
                ? CurrentPriceProviderFailureType.TEMPORARY
                : CurrentPriceProviderFailureType.PERMANENT;
        String message = temporary
                ? "KIS current price request failed temporarily."
                : "KIS current price request failed permanently.";

        return new CurrentPriceProviderException(
                failureType,
                message,
                exception
        );
    }

    private CurrentPriceProviderException permanentFailure(String message) {
        return new CurrentPriceProviderException(
                CurrentPriceProviderFailureType.PERMANENT,
                message
        );
    }
}
