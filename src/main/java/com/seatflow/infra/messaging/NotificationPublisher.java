package com.seatflow.infra.messaging;

import java.util.concurrent.CompletableFuture;

/**
 * Seam between the notification outbox and the broker.
 *
 * <p>The outbox dispatcher owns "when to publish and when to mark a row as
 * sent"; this interface owns "how a message reaches the broker". Keeping them
 * apart means the dispatcher can be unit tested with a fake publisher, and the
 * broker can be replaced without touching the business flow.
 */
public interface NotificationPublisher {

    /**
     * Publishes one notification. Implementations must not swallow failures:
     * the caller relies on the exception to keep the outbox row pending.
     *
     * @return
     */
    CompletableFuture<Boolean> publish(NotificationMessage message);
}
