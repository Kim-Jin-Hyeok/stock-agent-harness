package com.stock.market.index.history.collection.backfill;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.collection.backfill.result.MarketIndexDailyHistoryBackfillStatus;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationEntity;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
class MarketIndexDailyHistoryBackfillPersistenceTest {
    private static final LocalDate FROM = LocalDate.of(2023, 9, 25);
    private static final LocalDate EARLIEST = LocalDate.of(2025, 9, 30);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Autowired
    private MarketIndexDailyObservationRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void extendsHistoryWithoutChangingStoredRowsAndSkipsRepeatedRequest() {
        var original = repository.saveAndFlush(entity("KOSPI", EARLIEST, "3421.371234"));
        var originalId = original.getId();
        repository.saveAndFlush(entity("KOSDAQ", FROM, "950.123456"));
        var provider = mock(MarketIndexDailyHistoryProvider.class);
        var actualRequest = new MarketIndexDailyHistoryRequest("KOSPI", FROM, EARLIEST.minusDays(1));
        when(provider.getDailyHistory(actualRequest)).thenReturn(new MarketIndexDailyHistory(
                "KOSPI", List.of(
                        observation(FROM, "2500.123456"),
                        observation(EARLIEST.minusDays(1), "3400.234567")
                )
        ));
        var service = new MarketIndexDailyHistoryBackfillService(provider, repository);
        var request = new MarketIndexDailyHistoryRequest("KOSPI", FROM, TO);

        var first = service.backfill(request);
        entityManager.clear();
        var repeated = service.backfill(request);

        assertThat(first.savedCount()).isEqualTo(2);
        assertThat(repeated.status()).isEqualTo(MarketIndexDailyHistoryBackfillStatus.NO_EARLIER_RANGE);
        assertThat(repository.count()).isEqualTo(4);
        var restored = repository.findById(originalId).orElseThrow();
        assertThat(restored.getObservationDate()).isEqualTo(EARLIEST);
        assertThat(restored.getCloseValue()).isEqualByComparingTo("3421.371234");
        assertThat(repository.findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                "KOSPI", FROM, TO
        )).extracting(MarketIndexDailyObservationEntity::getObservationDate)
                .containsExactly(FROM, EARLIEST.minusDays(1), EARLIEST);
        assertThat(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSDAQ")
                .orElseThrow().getCloseValue()).isEqualByComparingTo("950.123456");
        verify(provider, times(1)).getDailyHistory(actualRequest);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void preservesRowAddedDuringProviderCallInsteadOfOverwritingItsValue() {
        repository.saveAndFlush(entity("KOSPI", EARLIEST, "3421.371234"));
        var provider = mock(MarketIndexDailyHistoryProvider.class);
        var request = new MarketIndexDailyHistoryRequest("KOSPI", FROM, EARLIEST.minusDays(1));
        when(provider.getDailyHistory(request)).thenAnswer(invocation -> {
            repository.saveAndFlush(entity("KOSPI", FROM, "2500.123456"));
            return new MarketIndexDailyHistory("KOSPI", List.of(
                    observation(FROM, "9999.999999"),
                    observation(EARLIEST.minusDays(1), "3400.234567")
            ));
        });

        var result = new MarketIndexDailyHistoryBackfillService(provider, repository).backfill(request);
        entityManager.clear();

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSPI")
                .orElseThrow().getCloseValue()).isEqualByComparingTo("2500.123456");
    }

    private MarketIndexDailyObservationEntity entity(String benchmarkId, LocalDate date, String value) {
        return MarketIndexDailyObservationEntity.from(benchmarkId, observation(date, value));
    }

    private MarketIndexDailyObservation observation(LocalDate date, String value) {
        return new MarketIndexDailyObservation(date, new BigDecimal(value));
    }
}
