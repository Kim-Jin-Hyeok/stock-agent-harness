package com.stock.market.index.history.persistence;

import com.stock.market.index.history.MarketIndexDailyObservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MarketIndexDailyObservationRepositoryTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final LocalDate OBSERVATION_DATE =
            LocalDate.of(2026, 9, 30);

    @Autowired
    private MarketIndexDailyObservationRepository repository;

    @Test
    void savesAndRestoresObservationWithoutDecimalPrecisionLoss() {
        MarketIndexDailyObservation expected = observation(
                OBSERVATION_DATE,
                "3421.371234"
        );

        MarketIndexDailyObservationEntity saved = repository.saveAndFlush(
                MarketIndexDailyObservationEntity.from(
                        BENCHMARK_ID,
                        expected
                )
        );
        MarketIndexDailyObservationEntity restored = repository
                .findById(saved.getId())
                .orElseThrow();

        assertThat(restored.getBenchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(restored.toObservation().observationDate())
                .isEqualTo(expected.observationDate());
        assertThat(restored.toObservation().closeValue())
                .isEqualByComparingTo(expected.closeValue());
    }

    @Test
    void findsRequestedRangeOrderedByObservationDate() {
        repository.saveAllAndFlush(List.of(
                entity(BENCHMARK_ID, OBSERVATION_DATE, "3421.37"),
                entity(
                        BENCHMARK_ID,
                        OBSERVATION_DATE.minusDays(2),
                        "3380.10"
                ),
                entity(
                        BENCHMARK_ID,
                        OBSERVATION_DATE.minusDays(1),
                        "3400.25"
                ),
                entity(
                        BENCHMARK_ID,
                        OBSERVATION_DATE.minusDays(3),
                        "3350.50"
                ),
                entity(
                        "KOSDAQ",
                        OBSERVATION_DATE.minusDays(1),
                        "950.75"
                )
        ));

        List<MarketIndexDailyObservationEntity> result = repository
                .findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                        BENCHMARK_ID,
                        OBSERVATION_DATE.minusDays(2),
                        OBSERVATION_DATE
                );

        assertThat(result)
                .extracting(
                        MarketIndexDailyObservationEntity::getObservationDate
                )
                .containsExactly(
                        OBSERVATION_DATE.minusDays(2),
                        OBSERVATION_DATE.minusDays(1),
                        OBSERVATION_DATE
                );
        assertThat(result)
                .extracting(
                        MarketIndexDailyObservationEntity::getBenchmarkId
                )
                .containsOnly(BENCHMARK_ID);
    }

    @Test
    void findsLatestObservationForBenchmark() {
        repository.saveAllAndFlush(List.of(
                entity(
                        BENCHMARK_ID,
                        OBSERVATION_DATE.minusDays(1),
                        "3400.25"
                ),
                entity(BENCHMARK_ID, OBSERVATION_DATE, "3421.37"),
                entity(
                        "KOSDAQ",
                        OBSERVATION_DATE.plusDays(1),
                        "960.10"
                )
        ));

        MarketIndexDailyObservationEntity latest = repository
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )
                .orElseThrow();

        assertThat(latest.getObservationDate())
                .isEqualTo(OBSERVATION_DATE);
        assertThat(latest.getCloseValue())
                .isEqualByComparingTo(new BigDecimal("3421.37"));
    }

    @Test
    void findsEarliestObservationForBenchmark() {
        repository.saveAllAndFlush(List.of(
                entity(BENCHMARK_ID, OBSERVATION_DATE, "3421.37"),
                entity(BENCHMARK_ID, OBSERVATION_DATE.minusDays(1), "3400.25"),
                entity("KOSDAQ", OBSERVATION_DATE.minusDays(2), "950.75")
        ));

        var earliest = repository.findTopByBenchmarkIdOrderByObservationDateAsc(BENCHMARK_ID)
                .orElseThrow();

        assertThat(earliest.getObservationDate()).isEqualTo(OBSERVATION_DATE.minusDays(1));
        assertThat(earliest.getCloseValue()).isEqualByComparingTo("3400.25");
    }

    @Test
    void rejectsDuplicateBenchmarkAndObservationDate() {
        repository.saveAndFlush(entity(
                BENCHMARK_ID,
                OBSERVATION_DATE,
                "3421.37"
        ));

        assertThatThrownBy(() -> repository.saveAndFlush(entity(
                BENCHMARK_ID,
                OBSERVATION_DATE,
                "3500.25"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesSameObservationDateForDifferentBenchmarks() {
        repository.saveAllAndFlush(List.of(
                entity(BENCHMARK_ID, OBSERVATION_DATE, "3421.37"),
                entity("KOSDAQ", OBSERVATION_DATE, "950.75")
        ));

        assertThat(repository.count()).isEqualTo(2L);
    }

    @Test
    void rejectsBlankBenchmarkIdBeforePersistence() {
        assertThatThrownBy(() ->
                MarketIndexDailyObservationEntity.from(
                        " ",
                        observation(OBSERVATION_DATE, "3421.37")
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
    }

    private MarketIndexDailyObservationEntity entity(
            String benchmarkId,
            LocalDate observationDate,
            String closeValue
    ) {
        return MarketIndexDailyObservationEntity.from(
                benchmarkId,
                observation(observationDate, closeValue)
        );
    }

    private MarketIndexDailyObservation observation(
            LocalDate observationDate,
            String closeValue
    ) {
        return new MarketIndexDailyObservation(
                observationDate,
                new BigDecimal(closeValue)
        );
    }
}
