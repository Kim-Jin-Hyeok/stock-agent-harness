package com.stock.strategy.universe.candidate.evaluation.snapshot.storage;

import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.candidate.evaluation.snapshot.json.StockCandidateEvaluationSnapshotJsonConverter;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotEntity;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.Optional;

import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCandidateEvaluationSnapshotStoreTest {
    @Mock
    private StockCandidateEvaluationSnapshotRepository repository;
    @Mock
    private StockCandidateEvaluationSnapshotJsonConverter converter;

    private StockCandidateEvaluationSnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new StockCandidateEvaluationSnapshotStore(repository, converter);
    }

    @ParameterizedTest(name = "case {index}")
    @MethodSource("com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture#snapshots")
    void serializesAndInsertsNewEntityBeforeReturningGeneratedId(StockCandidateEvaluationSnapshot snapshot) {
        StockCandidateEvaluationSnapshotEntity persisted = mock(StockCandidateEvaluationSnapshotEntity.class);
        when(converter.toJson(snapshot)).thenReturn("serialized-snapshot");
        when(repository.saveAndFlush(any())).thenReturn(persisted);
        when(persisted.getId()).thenReturn(17L);
        Instant before = Instant.now();

        Long id = store.save(snapshot);

        Instant after = Instant.now();
        ArgumentCaptor<StockCandidateEvaluationSnapshotEntity> captor =
                ArgumentCaptor.forClass(StockCandidateEvaluationSnapshotEntity.class);
        var ordered = inOrder(converter, repository);
        ordered.verify(converter).toJson(snapshot);
        ordered.verify(repository).saveAndFlush(captor.capture());
        ordered.verifyNoMoreInteractions();
        assertThat(id).isEqualTo(17L);
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getSnapshotJson()).isEqualTo("serialized-snapshot");
        assertThat(captor.getValue().getEvaluationStatus()).isEqualTo(snapshot.evaluationResult().status());
        assertThat(captor.getValue().getSelectionAsOfDate()).isEqualTo(DATE);
        assertThat(captor.getValue().getRecordedAt()).isBetween(before, after);
    }

    @Test
    void serializationFailureDoesNotCallRepository() {
        StockCandidateEvaluationSnapshot snapshot = completeSnapshot();
        var failure = new IllegalArgumentException("serialization failed");
        when(converter.toJson(snapshot)).thenThrow(failure);

        assertThatThrownBy(() -> store.save(snapshot)).isSameAs(failure);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsBlankSerializedResponseBeforeInsert(String json) {
        StockCandidateEvaluationSnapshot snapshot = completeSnapshot();
        when(converter.toJson(snapshot)).thenReturn(json);

        assertThatThrownBy(() -> store.save(snapshot)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("snapshotJson must not be blank.");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullSerializedResponseBeforeInsert() {
        StockCandidateEvaluationSnapshot snapshot = completeSnapshot();
        when(converter.toJson(snapshot)).thenReturn(null);

        assertThatThrownBy(() -> store.save(snapshot)).isInstanceOf(NullPointerException.class)
                .hasMessage("snapshotJson must not be null.");
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesInsertFailureWithoutReturningSuccessId() {
        StockCandidateEvaluationSnapshot snapshot = completeSnapshot();
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(converter.toJson(snapshot)).thenReturn("serialized-snapshot");
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> store.save(snapshot)).isSameAs(failure);
        verify(converter).toJson(snapshot);
    }

    @ParameterizedTest(name = "case {index}")
    @MethodSource("com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture#snapshots")
    void restoresExistingPayloadWithoutReEvaluatingOrSaving(StockCandidateEvaluationSnapshot snapshot) {
        var entity = StockCandidateEvaluationSnapshotEntity.from(snapshot, "saved-json", Instant.now());
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
        var entity = StockCandidateEvaluationSnapshotEntity.from(completeSnapshot(), "broken-json", Instant.now());
        var failure = new IllegalArgumentException("decode failed");
        when(repository.findById(17L)).thenReturn(Optional.of(entity));
        when(converter.fromJson("broken-json")).thenThrow(failure);

        assertThatThrownBy(() -> store.findById(17L)).isSameAs(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "status"})
    void rejectsMetadataMismatchWithoutSavingOrReturningEmpty(String mismatch) {
        var entity = mock(StockCandidateEvaluationSnapshotEntity.class);
        when(repository.findById(17L)).thenReturn(Optional.of(entity));
        when(entity.getSnapshotJson()).thenReturn("saved-json");
        when(entity.getId()).thenReturn(17L);
        when(entity.getSelectionAsOfDate()).thenReturn(mismatch.equals("date") ? DATE.plusDays(1) : DATE);
        if (mismatch.equals("status")) {
            when(entity.getEvaluationStatus()).thenReturn(StockCandidateEvaluationStatus.INCOMPLETE);
        }
        when(converter.fromJson("saved-json")).thenReturn(completeSnapshot());

        assertThatThrownBy(() -> store.findById(17L)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored stock candidate evaluation snapshot metadata does not match payload. id=17");
        var ordered = inOrder(repository, converter);
        ordered.verify(repository).findById(17L);
        ordered.verify(converter).fromJson("saved-json");
        ordered.verifyNoMoreInteractions();
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
        assertThatThrownBy(() -> new StockCandidateEvaluationSnapshotStore(null, converter))
                .isInstanceOf(NullPointerException.class).hasMessage("repository must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationSnapshotStore(repository, null))
                .isInstanceOf(NullPointerException.class).hasMessage("converter must not be null.");
    }
}
