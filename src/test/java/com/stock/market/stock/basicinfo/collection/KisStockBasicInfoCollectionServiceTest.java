package com.stock.market.stock.basicinfo.collection;

import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.END;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.content;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockBasicInfoCollectionServiceTest {
    private final KisStockBasicInfoProvider provider = mock(KisStockBasicInfoProvider.class);
    private final KisStockBasicInfoObservationStore store = mock(KisStockBasicInfoObservationStore.class);
    private final KisStockBasicInfoCollectionService service = new KisStockBasicInfoCollectionService(provider, store);

    @Test
    void constructionDoesNotFetchOrSaveObservations() {
        verifyNoInteractions(provider, store);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new KisStockBasicInfoCollectionService(null, store))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("provider must not be null.");
        assertThatThrownBy(() -> new KisStockBasicInfoCollectionService(provider, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("store must not be null.");
        verifyNoInteractions(provider, store);
    }

    @ParameterizedTest
    @ValueSource(strings = {"005930", "0004Y0"})
    void fetchesOnceThenSavesTheOriginalResponseAndReturnsItsObservationId(String symbol) {
        var original = new KisStockBasicInfoRawResponse(symbol, START, END, 200, content());
        when(provider.getStockBasicInfo(symbol)).thenReturn(original);
        when(store.save(same(original))).thenReturn(17L);

        assertThat(service.collect(symbol)).isEqualTo(17L);

        var order = inOrder(provider, store);
        order.verify(provider).getStockBasicInfo(symbol);
        order.verify(store).save(same(original));
        verifyNoMoreInteractions(provider, store);
    }

    @ParameterizedTest(name = "content case {index}")
    @MethodSource("com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture#contents")
    void savesReceivedBytesWithoutInterpretingBusinessSuccessOrParsingJson(byte[] bytes) {
        var original = response(bytes);
        when(provider.getStockBasicInfo(SYMBOL)).thenReturn(original);
        when(store.save(same(original))).thenReturn(18L);

        assertThat(service.collect(SYMBOL)).isEqualTo(18L);

        verify(provider).getStockBasicInfo(SYMBOL);
        verify(store).save(same(original));
        verifyNoMoreInteractions(provider, store);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 ", "00593?", "005930\n"})
    void reliesOnExistingProviderValidationBeforeTokenLookupOrStorage(String symbol) {
        var client = mock(KisStockBasicInfoClient.class);
        var tokenProvider = mock(KisTokenProvider.class);
        var validatingService = new KisStockBasicInfoCollectionService(new KisStockBasicInfoProvider(client, tokenProvider), store);

        assertThatThrownBy(() -> validatingService.collect(symbol))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.").hasNoCause();

        verifyNoInteractions(client, tokenProvider, store, provider);
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void propagatesProviderFailureWithoutSavingOrRetrying(RuntimeException failure) {
        when(provider.getStockBasicInfo(SYMBOL)).thenThrow(failure);

        assertThatThrownBy(() -> service.collect(SYMBOL)).isSameAs(failure);

        verify(provider).getStockBasicInfo(SYMBOL);
        verifyNoMoreInteractions(provider);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsNullResponseWithoutSavingOrRetrying() {
        assertThatThrownBy(() -> service.collect(SYMBOL))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info response must not be null.").hasNoCause();

        verify(provider).getStockBasicInfo(SYMBOL);
        verifyNoMoreInteractions(provider);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMismatchedRequestMetadataWithoutSavingOrRevealingResponseContent() {
        when(provider.getStockBasicInfo("005930")).thenReturn(response());

        assertThatThrownBy(() -> service.collect("005930"))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info response requested symbol does not match collection request.")
                .hasNoCause();

        verify(provider).getStockBasicInfo("005930");
        verifyNoMoreInteractions(provider);
        verifyNoInteractions(store);
    }

    @Test
    void propagatesSaveFailureWithoutReturningAnIdRefetchingOrRetryingStorage() {
        var original = response();
        var failure = new DataAccessResourceFailureException("Synthetic storage failure.");
        when(provider.getStockBasicInfo(SYMBOL)).thenReturn(original);
        when(store.save(same(original))).thenThrow(failure);

        assertThatThrownBy(() -> service.collect(SYMBOL)).isSameAs(failure);

        var order = inOrder(provider, store);
        order.verify(provider).getStockBasicInfo(SYMBOL);
        order.verify(store).save(same(original));
        verifyNoMoreInteractions(provider, store);
    }

    @Test
    void explicitRepeatedCollectionsFetchAndSaveEachObservationWithoutDeduplication() {
        var original = response();
        when(provider.getStockBasicInfo(SYMBOL)).thenReturn(original);
        when(store.save(same(original))).thenReturn(17L, 18L);

        assertThat(service.collect(SYMBOL)).isEqualTo(17L);
        assertThat(service.collect(SYMBOL)).isEqualTo(18L);

        var order = inOrder(provider, store);
        order.verify(provider).getStockBasicInfo(SYMBOL);
        order.verify(store).save(same(original));
        order.verify(provider).getStockBasicInfo(SYMBOL);
        order.verify(store).save(same(original));
        verifyNoMoreInteractions(provider, store);
    }

    private static Stream<RuntimeException> providerFailures() {
        return Stream.of(new IllegalArgumentException("Synthetic input failure."),
                new ResourceAccessException("KIS token request failed."),
                new IllegalStateException("KIS stock basic info response HTTP status=503."));
    }
}
