package com.stock.market.price.observation.persistence;

import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.observation.CurrentPriceObservation;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CurrentPriceObservationRepositoryTest {
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-24T00:00:00Z");

    @Autowired
    private CurrentPriceObservationRepository repository;

    @Test
    void savesAndRestoresCurrentPriceObservation() {
        CurrentPriceObservationEntity saved = repository.saveAndFlush(
                CurrentPriceObservationEntity.from(observation(
                        "run-1",
                        CurrentPriceLookupSource.PROVIDER
                ))
        );

        CurrentPriceObservation restored = repository.findById(saved.getId())
                .orElseThrow()
                .toObservation();

        assertThat(restored.id()).isEqualTo(saved.getId());
        assertThat(restored.runId()).isEqualTo("run-1");
        assertThat(restored.strategyIdentity()).isEqualTo(strategyIdentity());
        assertThat(restored.symbol()).isEqualTo("005930");
        assertThat(restored.priceKrw()).isEqualTo(70_000L);
        assertThat(restored.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(restored.source())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }

    @Test
    void storesSameMarketObservationForDifferentRuns() {
        repository.save(CurrentPriceObservationEntity.from(observation(
                "run-provider",
                CurrentPriceLookupSource.PROVIDER
        )));
        repository.saveAndFlush(CurrentPriceObservationEntity.from(observation(
                "run-cache",
                CurrentPriceLookupSource.CACHE
        )));

        assertThat(repository.count()).isEqualTo(2L);
        assertThat(repository
                .findAllByRunIdOrderByObservedAtAsc("run-provider"))
                .singleElement()
                .extracting(CurrentPriceObservationEntity::getSource)
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(repository
                .findAllByRunIdOrderByObservedAtAsc("run-cache"))
                .singleElement()
                .extracting(CurrentPriceObservationEntity::getSource)
                .isEqualTo(CurrentPriceLookupSource.CACHE);
    }

    private CurrentPriceObservation observation(
            String runId,
            CurrentPriceLookupSource source
    ) {
        return new CurrentPriceObservation(
                null,
                runId,
                strategyIdentity(),
                "005930",
                70_000L,
                OBSERVED_AT,
                source
        );
    }

    private InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(
                "DAY_TRADING_V1",
                1,
                InvestmentHorizon.DAY_TRADING
        );
    }
}
