package com.stock.strategy.universe.liquidity.evaluation.snapshot.storage;

import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotEntity;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.Optional;

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.incompleteSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyTradingValueSelectionSnapshotStoreTest {
    @Mock
    private DailyTradingValueSelectionSnapshotRepository repository;
    @Mock
    private DailyTradingValueSelectionSnapshotJsonConverter converter;

    private DailyTradingValueSelectionSnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new DailyTradingValueSelectionSnapshotStore(repository, converter);
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void serializesAndInsertsNewEntityBeforeReturningGeneratedId(DailyTradingValueSelectionEvaluationStatus status) {
        DailyTradingValueSelectionSnapshot snapshot = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();
        DailyTradingValueSelectionSnapshotEntity persisted = mock(DailyTradingValueSelectionSnapshotEntity.class);
        when(converter.toJson(snapshot)).thenReturn("serialized-snapshot");
        when(repository.saveAndFlush(any())).thenReturn(persisted);
        when(persisted.getId()).thenReturn(17L);
        Instant before = Instant.now();

        Long id = store.save(snapshot);

        Instant after = Instant.now();
        ArgumentCaptor<DailyTradingValueSelectionSnapshotEntity> captor =
                ArgumentCaptor.forClass(DailyTradingValueSelectionSnapshotEntity.class);
        var ordered = inOrder(converter, repository);
        ordered.verify(converter).toJson(snapshot);
        ordered.verify(repository).saveAndFlush(captor.capture());
        ordered.verifyNoMoreInteractions();
        assertThat(id).isEqualTo(17L);
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getSnapshotJson()).isEqualTo("serialized-snapshot");
        assertThat(captor.getValue().getEvaluationStatus()).isEqualTo(status);
        assertThat(captor.getValue().getSelectionAsOfDate())
                .isEqualTo(snapshot.evaluationResult().request().selectionAsOfDate());
        assertThat(captor.getValue().getRecordedAt()).isBetween(before, after);
    }

    @Test
    void serializationFailureDoesNotCallRepository() {
        DailyTradingValueSelectionSnapshot snapshot = completeSnapshot();
        var failure = new IllegalArgumentException("serialization failed");
        when(converter.toJson(snapshot)).thenThrow(failure);

        assertThatThrownBy(() -> store.save(snapshot)).isSameAs(failure);
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesInsertFailureWithoutReturningSuccessId() {
        DailyTradingValueSelectionSnapshot snapshot = completeSnapshot();
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(converter.toJson(snapshot)).thenReturn("serialized-snapshot");
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> store.save(snapshot)).isSameAs(failure);
        verify(converter).toJson(snapshot);
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void restoresExistingPayloadWithoutReEvaluatingOrSaving(DailyTradingValueSelectionEvaluationStatus status) {
        DailyTradingValueSelectionSnapshot snapshot = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();
        var entity = DailyTradingValueSelectionSnapshotEntity.from(snapshot, "saved-json", Instant.now());
        when(repository.findById(17L)).thenReturn(Optional.of(entity));
        when(converter.fromJson("saved-json")).thenReturn(snapshot);

        assertThat(store.findById(17L)).contains(snapshot);
        var ordered = inOrder(repository, converter);
        ordered.verify(repository).findById(17L);
        ordered.verify(converter).fromJson("saved-json");
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void returnsEmptyOnlyWhenIdDoesNotExist() {
        when(repository.findById(17L)).thenReturn(Optional.empty());

        assertThat(store.findById(17L)).isEmpty();
        verifyNoInteractions(converter);
    }

    @Test
    void propagatesReadFailureInsteadOfReturningEmpty() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(repository.findById(17L)).thenThrow(failure);

        assertThatThrownBy(() -> store.findById(17L)).isSameAs(failure);
        verifyNoInteractions(converter);
    }

    @Test
    void propagatesDecodeFailureInsteadOfReturningEmpty() {
        var entity = DailyTradingValueSelectionSnapshotEntity.from(completeSnapshot(), "broken-json", Instant.now());
        var failure = new IllegalArgumentException("decode failed");
        when(repository.findById(17L)).thenReturn(Optional.of(entity));
        when(converter.fromJson("broken-json")).thenThrow(failure);

        assertThatThrownBy(() -> store.findById(17L)).isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsNonPositiveIdBeforeReading(Long id) {
        assertThatThrownBy(() -> store.findById(id))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("id must be positive.");
        verifyNoInteractions(repository, converter);
    }

    @Test
    void rejectsNullArgumentsBeforeIo() {
        assertThatThrownBy(() -> store.save(null))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshot must not be null.");
        assertThatThrownBy(() -> store.findById(null))
                .isInstanceOf(NullPointerException.class).hasMessage("id must not be null.");
        verifyNoInteractions(repository, converter);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionSnapshotStore(null, converter))
                .isInstanceOf(NullPointerException.class).hasMessage("repository must not be null.");
        assertThatThrownBy(() -> new DailyTradingValueSelectionSnapshotStore(repository, null))
                .isInstanceOf(NullPointerException.class).hasMessage("converter must not be null.");
    }
}
