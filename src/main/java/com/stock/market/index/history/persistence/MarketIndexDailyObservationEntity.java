package com.stock.market.index.history.persistence;

import com.stock.market.index.history.MarketIndexDailyObservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "market_index_daily_observation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_market_index_daily_observation_id_date",
                columnNames = {"benchmark_id", "observation_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndexDailyObservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "benchmark_id", nullable = false, length = 30)
    private String benchmarkId;

    @Column(name = "observation_date", nullable = false)
    private LocalDate observationDate;

    @Column(
            name = "close_value",
            nullable = false,
            precision = 19,
            scale = 6
    )
    private BigDecimal closeValue;

    public static MarketIndexDailyObservationEntity from(
            String benchmarkId,
            MarketIndexDailyObservation observation
    ) {
        if (benchmarkId == null || benchmarkId.isBlank()) {
            throw new IllegalArgumentException(
                    "benchmarkId must not be blank."
            );
        }
        Objects.requireNonNull(
                observation,
                "observation must not be null."
        );

        MarketIndexDailyObservationEntity entity =
                new MarketIndexDailyObservationEntity();
        entity.benchmarkId = benchmarkId;
        entity.observationDate = observation.observationDate();
        entity.closeValue = observation.closeValue();
        return entity;
    }

    public MarketIndexDailyObservation toObservation() {
        return new MarketIndexDailyObservation(
                observationDate,
                closeValue
        );
    }
}
