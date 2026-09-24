package com.stock.market.price.observation.persistence;

import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.observation.CurrentPriceObservation;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "current_price_observation",
        indexes = {
                @Index(
                        name = "idx_current_price_observation_run_id",
                        columnList = "run_id"
                ),
                @Index(
                        name = "idx_current_price_observation_strategy_symbol_time",
                        columnList = "strategy_id, strategy_version, horizon, symbol, observed_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurrentPriceObservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 100)
    private String runId;

    @Column(name = "strategy_id", nullable = false, length = 100)
    private String strategyId;

    @Column(name = "strategy_version", nullable = false)
    private int strategyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "horizon", nullable = false, length = 30)
    private InvestmentHorizon horizon;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "price_krw", nullable = false)
    private long priceKrw;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CurrentPriceLookupSource source;

    public static CurrentPriceObservationEntity from(
            CurrentPriceObservation observation
    ) {
        CurrentPriceObservationEntity entity =
                new CurrentPriceObservationEntity();
        entity.id = observation.id();
        entity.runId = observation.runId();
        entity.strategyId = observation.strategyIdentity().strategyId();
        entity.strategyVersion =
                observation.strategyIdentity().strategyVersion();
        entity.horizon = observation.strategyIdentity().horizon();
        entity.symbol = observation.symbol();
        entity.priceKrw = observation.priceKrw();
        entity.observedAt = observation.observedAt();
        entity.source = observation.source();
        return entity;
    }

    public CurrentPriceObservation toObservation() {
        return new CurrentPriceObservation(
                id,
                runId,
                new InvestmentStrategyIdentity(
                        strategyId,
                        strategyVersion,
                        horizon
                ),
                symbol,
                priceKrw,
                observedAt,
                source
        );
    }
}
