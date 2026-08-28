package com.mohistmc.youer.ai.http;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class BoundedStringBodyHandler implements HttpResponse.BodyHandler<String> {

    private final int maxBytes;

    BoundedStringBodyHandler(int maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {
        long declaredLength = responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declaredLength > maxBytes) {
            return new RejectingSubscriber(tooLarge());
        }
        return new BoundedSubscriber(maxBytes);
    }

    private static AiHttpException tooLarge() {
        return new AiHttpException(AiHttpException.Reason.RESPONSE_TOO_LARGE, null);
    }

    private record RejectingSubscriber(CompletableFuture<String> body) implements HttpResponse.BodySubscriber<String> {

            private RejectingSubscriber(Throwable failure) {
                this(CompletableFuture.failedFuture(failure));
            }

            @Override
            public CompletionStage<String> getBody() {
                return body;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.cancel();
            }

            @Override
            public void onNext(List<ByteBuffer> item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        }

    private static final class BoundedSubscriber implements HttpResponse.BodySubscriber<String> {

        private final int maxBytes;
        private final ByteArrayOutputStream bytes;
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int size;

        private BoundedSubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.bytes = new ByteArrayOutputStream(Math.min(maxBytes, 8_192));
        }

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            long incoming = 0;
            for (ByteBuffer buffer : buffers) {
                incoming += buffer.remaining();
            }
            if (incoming > maxBytes - size) {
                subscription.cancel();
                body.completeExceptionally(tooLarge());
                return;
            }
            for (ByteBuffer source : buffers) {
                ByteBuffer buffer = source.duplicate();
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
                size += chunk.length;
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toString(StandardCharsets.UTF_8));
        }
    }
}
