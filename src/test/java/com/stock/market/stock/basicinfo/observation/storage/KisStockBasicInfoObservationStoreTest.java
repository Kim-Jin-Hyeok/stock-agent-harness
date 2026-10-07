package com.stock.market.stock.basicinfo.observation.storage;

import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockBasicInfoObservationStoreTest {
    private final KisStockBasicInfoObservationRepository repository = mock(KisStockBasicInfoObservationRepository.class);
    private final Clock clock = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
    private final KisStockBasicInfoObservationStore store = new KisStockBasicInfoObservationStore(repository, clock);

    @ParameterizedTest(name = "content case {index}")
    @MethodSource("com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture#contents")
    void insertsReceivedRawContentBeforeReturningGeneratedId(byte[] bytes) {
        var original = response(bytes);
        var persisted = persisted(17L);
        when(repository.saveAndFlush(any())).thenReturn(persisted);

        assertThat(store.save(original)).isEqualTo(17L);

        var captor = ArgumentCaptor.forClass(KisStockBasicInfoObservationEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        verifyNoMoreInteractions(repository);
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getRecordedAt()).isEqualTo(RECORDED_AT.truncatedTo(ChronoUnit.MICROS));
        assertThat(captor.getValue().toRawResponse()).isEqualTo(normalized(original));
    }

    @Test
    void createsSeparateNewEntitiesForIdenticalResponsesWithoutLookingForExistingSymbolOrHash() {
        var first = persisted(17L);
        var second = persisted(18L);
        when(repository.saveAndFlush(any())).thenReturn(first, second);

        assertThat(store.save(response())).isEqualTo(17L);
        assertThat(store.save(response())).isEqualTo(18L);

        var captor = ArgumentCaptor.forClass(KisStockBasicInfoObservationEntity.class);
        verify(repository, times(2)).saveAndFlush(captor.capture());
        verifyNoMoreInteractions(repository);
        var entities = captor.getAllValues();
        assertThat(entities.get(0)).isNotSameAs(entities.get(1));
        assertThat(entities).allSatisfy(entity -> assertThat(entity.getId()).isNull());
    }

    @Test
    void restoresExistingObservationWithoutSavingOrReEvaluating() {
        var entity = KisStockBasicInfoObservationEntity.from(response(), RECORDED_AT);
        when(repository.findById(17L)).thenReturn(Optional.of(entity));

        assertThat(store.findById(17L)).contains(normalized(response()));
        verify(repository).findById(17L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void returnsEmptyOnlyForMissingObservation() {
        when(repository.findById(17L)).thenReturn(Optional.empty());

        assertThat(store.findById(17L)).isEmpty();
        verify(repository).findById(17L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsCorruptObservationWithoutReturningEmptyOrRepairingIt() {
        var entity = KisStockBasicInfoObservationEntity.from(response(), RECORDED_AT);
        ReflectionTestUtils.setField(entity, "id", 17L);
        ReflectionTestUtils.setField(entity, "contentSha256", "0".repeat(64));
        when(repository.findById(17L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> store.findById(17L)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Stored KIS stock basic info content integrity check failed. id=17").hasNoCause();
        verify(repository).findById(17L);
        verifyNoMoreInteractions(repository);
        assertThat(entity.getContentSha256()).isEqualTo("0".repeat(64));
    }

    @Test
    void propagatesInsertFailureWithoutReturningAnIdOrRetrying() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> store.save(response())).isSameAs(failure);
        verify(repository).saveAndFlush(any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void propagatesReadFailureInsteadOfReturningEmptyOrRetrying() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(repository.findById(17L)).thenThrow(failure);

        assertThatThrownBy(() -> store.findById(17L)).isSameAs(failure);
        verify(repository).findById(17L);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsNonPositiveIdBeforeReading(Long id) {
        assertThatThrownBy(() -> store.findById(id)).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must be positive.");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullInputsBeforeDatabaseAccess() {
        assertThatThrownBy(() -> store.save(null)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("response must not be null.");
        assertThatThrownBy(() -> store.findById(null)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("id must not be null.");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new KisStockBasicInfoObservationStore(null, clock))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("repository must not be null.");
        assertThatThrownBy(() -> new KisStockBasicInfoObservationStore(repository, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("clock must not be null.");
    }

    @Test
    void selectsOnlyOneIdUsingExplicitCutoffWithoutReadingRawContentOrClock() {
        var unusedClock = mock(Clock.class);
        var selector = new KisStockBasicInfoObservationStore(repository, unusedClock);
        var cutoff = RECORDED_AT.truncatedTo(ChronoUnit.MICROS);
        when(repository.findObservationIdsAvailableAt(eq(SYMBOL), eq(cutoff), any())).thenReturn(List.of(17L));

        assertThat(selector.findLatestObservationId(SYMBOL, RECORDED_AT)).contains(17L);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findObservationIdsAvailableAt(eq(SYMBOL), eq(cutoff), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(unusedClock);
    }

    @Test
    void returnsEmptyWhenNoObservationIdIsAvailableWithoutFallbackOrWrites() {
        var cutoff = RECORDED_AT.truncatedTo(ChronoUnit.MICROS);
        when(repository.findObservationIdsAvailableAt(SYMBOL, cutoff, PageRequest.of(0, 1))).thenReturn(List.of());

        assertThat(store.findLatestObservationId(SYMBOL, RECORDED_AT)).isEmpty();

        verify(repository).findObservationIdsAvailableAt(SYMBOL, cutoff, PageRequest.of(0, 1));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void propagatesIdSelectionFailureWithoutReturningEmptyOrRetrying() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        var cutoff = RECORDED_AT.truncatedTo(ChronoUnit.MICROS);
        when(repository.findObservationIdsAvailableAt(SYMBOL, cutoff, PageRequest.of(0, 1))).thenThrow(failure);

        assertThatThrownBy(() -> store.findLatestObservationId(SYMBOL, RECORDED_AT)).isSameAs(failure);

        verify(repository).findObservationIdsAvailableAt(SYMBOL, cutoff, PageRequest.of(0, 1));
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 ", "005-30"})
    void rejectsInvalidSelectionSymbolBeforeDatabaseAccess(String symbol) {
        assertThatThrownBy(() -> store.findLatestObservationId(symbol, RECORDED_AT))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullEvaluationTimeBeforeDatabaseAccess() {
        assertThatThrownBy(() -> store.findLatestObservationId(SYMBOL, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("evaluatedAt must not be null.");
        verifyNoInteractions(repository);
    }

    private KisStockBasicInfoObservationEntity persisted(Long id) {
        var entity = mock(KisStockBasicInfoObservationEntity.class);
        when(entity.getId()).thenReturn(id);
        return entity;
    }
}
