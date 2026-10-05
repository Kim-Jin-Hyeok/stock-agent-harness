package com.stock.market.stock.master.provider.kis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KisStockMasterClient {
    public static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;

    private final HttpClient httpClient;
    private final Duration downloadTimeout;
    private final int maxArchiveBytes;

    public KisStockMasterClient(HttpClient httpClient, Duration downloadTimeout, int maxArchiveBytes) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null.");
        this.downloadTimeout = Objects.requireNonNull(downloadTimeout, "downloadTimeout must not be null.");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Stock master redirects must be disabled.");
        }
        if (downloadTimeout.isNegative() || downloadTimeout.isZero()
                || downloadTimeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("downloadTimeout must be positive and at most 5 minutes.");
        }
        if (maxArchiveBytes <= 0 || maxArchiveBytes > MAX_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("maxArchiveBytes must be positive and at most 64 MiB.");
        }
        this.maxArchiveBytes = maxArchiveBytes;
    }

    public byte[] download(KisStockMasterMarket market) throws IOException {
        Objects.requireNonNull(market, "market must not be null.");
        HttpRequest request = HttpRequest.newBuilder(market.sourceUri())
                .timeout(downloadTimeout)
                .header("Accept-Encoding", "identity")
                .GET().build();
        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(request, info -> {
            if (info.statusCode() != 200) {
                throw new IllegalStateException("Stock master download HTTP status=" + info.statusCode());
            }
            if (info.headers().firstValueAsLong("Content-Length").orElse(0) > maxArchiveBytes) {
                throw new IllegalStateException("Stock master archive exceeds maxArchiveBytes.");
            }
            if (!info.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")) {
                throw new IllegalStateException("Unexpected stock master HTTP content encoding.");
            }
            return new LimitedBodySubscriber(maxArchiveBytes);
        });
        try {
            // Await the full bounded body, not only the response headers.
            HttpResponse<byte[]> response = future.get(downloadTimeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200 || !market.sourceUri().equals(response.uri())) {
                throw new IOException("Stock master response must be HTTP 200 from the requested source.");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > maxArchiveBytes) {
                throw new IOException("Stock master archive must be nonempty and within maxArchiveBytes.");
            }
            return body;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IOException("Stock master download exceeded downloadTimeout.", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Stock master download was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IOException("Stock master download failed.", e.getCause());
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Stock master archive exceeds maxArchiveBytes."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
