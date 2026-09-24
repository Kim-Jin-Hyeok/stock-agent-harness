package com.stock.market.price.observation;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.observation.persistence.CurrentPriceObservationEntity;
import com.stock.market.price.observation.persistence.CurrentPriceObservationRepository;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentPriceObservationServiceTest {
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-24T00:00:00Z");
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final CurrentPriceObservationRepository repository =
            mock(CurrentPriceObservationRepository.class);
    private final CurrentPriceObservationService service =
            new CurrentPriceObservationService(repository);

    @Test
    void recordsRunStrategyAndCurrentPriceValues() {
        when(repository.save(any(CurrentPriceObservationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CurrentPriceObservation result = service.record(
                "run-1",
                STRATEGY_IDENTITY,
                new CurrentPriceSnapshot(
                        "005930",
                        70_000L,
                        OBSERVED_AT
                ),
                CurrentPriceLookupSource.PROVIDER
        );

        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.strategyIdentity()).isEqualTo(STRATEGY_IDENTITY);
        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.priceKrw()).isEqualTo(70_000L);
        assertThat(result.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(result.source())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        verify(repository).save(any(CurrentPriceObservationEntity.class));
    }

    @Test
    void clearDeletesAllObservations() {
        service.clear();

        verify(repository).deleteAll();
    }
}
