/*
 * Decompiled with CFR 0.152.
 */
package feign;

import feign.Client;
import feign.Experimental;
import feign.Request;
import feign.Response;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Experimental
public interface AsyncClient<C> {
    public CompletableFuture<Response> execute(Request var1, Request.Options var2, Optional<C> var3);

    public static class Pseudo<C>
    implements AsyncClient<C> {
        private final Client client;

        public Pseudo(Client client) {
            this.client = client;
        }

        @Override
        public CompletableFuture<Response> execute(Request request, Request.Options options, Optional<C> requestContext) {
            CompletableFuture<Response> result = new CompletableFuture<Response>();
            try {
                result.complete(this.client.execute(request, options));
            }
            catch (Exception e) {
                result.completeExceptionally(e);
            }
            return result;
        }
    }

    public static class Default<C>
    implements AsyncClient<C> {
        private final Client client;
        private final ExecutorService executorService;

        public Default(Client client, ExecutorService executorService) {
            this.client = client;
            this.executorService = executorService;
        }

        @Override
        public CompletableFuture<Response> execute(Request request, Request.Options options, Optional<C> requestContext) {
            CompletableFuture<Response> result = new CompletableFuture<Response>();
            Future<?> future = this.executorService.submit(() -> {
                try {
                    result.complete(this.client.execute(request, options));
                }
                catch (Exception e) {
                    result.completeExceptionally(e);
                }
            });
            result.whenComplete((response, throwable) -> {
                if (result.isCancelled()) {
                    future.cancel(true);
                }
            });
            return result;
        }
    }
}

