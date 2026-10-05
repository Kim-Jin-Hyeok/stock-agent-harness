package com.stock.market.stock.master.provider.kis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisStockMasterClientTest {
    private final HttpClient httpClient = mock(HttpClient.class);
    private final Flow.Subscription subscription = mock(Flow.Subscription.class);
    private KisStockMasterClient client;
    private HttpRequest capturedRequest;

    @BeforeEach
    void setUp() {
        when(httpClient.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        client = new KisStockMasterClient(httpClient, Duration.ofSeconds(1), 8);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void downloadsOnlyPinnedPublicMarketUrlWithoutBrokerCredentials(KisStockMasterMarket market) throws IOException {
        respond(200, Map.of("Content-Length", List.of("8")), List.of(new byte[]{0, 1}, new byte[]{2, 3, 4, 5, 6, 7}));

        assertThat(client.download(market)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(capturedRequest.uri()).isEqualTo(market.sourceUri());
        assertThat(capturedRequest.method()).isEqualTo("GET");
        assertThat(capturedRequest.timeout()).contains(Duration.ofSeconds(1));
        assertThat(capturedRequest.headers().map()).containsOnlyKeys("Accept-Encoding");
        assertThat(capturedRequest.headers().firstValue("Accept-Encoding")).contains("identity");
    }

    @Test
    void rejectsActualBodyOverflowWithoutContentLengthAndCancelsSubscription() {
        respond(200, Map.of(), List.of(new byte[5], new byte[4]));

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasRootCauseMessage("Stock master archive exceeds maxArchiveBytes.");
        verify(subscription).cancel();
    }

    @Test
    void rejectsAdvertisedOversizeBeforeConsumingBody() {
        respond(200, Map.of("Content-Length", List.of("9")), List.of(new byte[1]));

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasRootCauseMessage("Stock master archive exceeds maxArchiveBytes.");
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 302, 404, 503})
    void rejectsNon200IncludingRedirects(int status) {
        respond(status, Map.of(), List.of(new byte[1]));

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasRootCauseMessage("Stock master download HTTP status=" + status);
    }

    @Test
    void rejectsAdditionalHttpContentEncoding() {
        respond(200, Map.of("Content-Encoding", List.of("gzip")), List.of(new byte[1]));

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasRootCauseMessage("Unexpected stock master HTTP content encoding.");
    }

    @Test
    void rejectsEmptyBody() {
        respond(200, Map.of(), List.of());

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasMessageContaining("nonempty");
    }

    @Test
    void rejectsResponseFromAnotherUrlEvenIfItIsHttp200() {
        var response = response(URI.create("https://example.com/file"), new byte[1]);
        when(httpClient.<byte[]>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasMessageContaining("requested source");
    }

    @Test
    void timesOutAndCancelsAnUnfinishedFullBody() {
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(httpClient.<byte[]>sendAsync(any(HttpRequest.class), any())).thenReturn(pending);
        client = new KisStockMasterClient(httpClient, Duration.ofMillis(30), 8);

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasMessageContaining("exceeded downloadTimeout");
        assertThat(pending).isCancelled();
    }

    @Test
    void cancelsDownloadAndPreservesInterruptFlag() {
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(httpClient.<byte[]>sendAsync(any(HttpRequest.class), any())).thenReturn(pending);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                    .isInstanceOf(IOException.class).hasMessageContaining("interrupted");
            assertThat(pending).isCancelled();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void propagatesTransportFailureWithoutRetry() {
        when(httpClient.<byte[]>sendAsync(any(HttpRequest.class), any()))
                .thenReturn(CompletableFuture.failedFuture(new IOException("transport failure")));

        assertThatThrownBy(() -> client.download(KisStockMasterMarket.KOSPI))
                .isInstanceOf(IOException.class).hasRootCauseMessage("transport failure");
        verify(httpClient).<byte[]>sendAsync(any(HttpRequest.class), any());
    }

    @Test
    void refusesClientsConfiguredToFollowRedirects() {
        when(httpClient.followRedirects()).thenReturn(HttpClient.Redirect.NORMAL);

        assertThatThrownBy(() -> new KisStockMasterClient(httpClient, Duration.ofSeconds(1), 8))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("redirects must be disabled");
    }

    private void respond(int status, Map<String, List<String>> headers, List<byte[]> chunks) {
        when(httpClient.<byte[]>sendAsync(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            capturedRequest = invocation.getArgument(0);
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            HttpResponse.ResponseInfo info = mock(HttpResponse.ResponseInfo.class);
            when(info.statusCode()).thenReturn(status);
            when(info.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
            try {
                var subscriber = handler.apply(info);
                subscriber.onSubscribe(subscription);
                for (byte[] chunk : chunks) {
                    subscriber.onNext(List.of(ByteBuffer.wrap(chunk)));
                }
                subscriber.onComplete();
                return subscriber.getBody().toCompletableFuture().thenApply(bytes -> response(capturedRequest.uri(), bytes));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(URI uri, byte[] body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.uri()).thenReturn(uri);
        when(response.body()).thenReturn(body);
        return response;
    }
}
