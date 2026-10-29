package com.ecomassistant.health;

import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QdrantHealthIndicatorTest {

    @Test
    void reportsUpWhenCollectionExists() throws Exception {
        QdrantClient client = mock(QdrantClient.class);
        ListenableFuture<Boolean> future = futureReturning(true);
        when(client.collectionExistsAsync("products")).thenReturn(future);

        Health health = new QdrantHealthIndicator(client, "products").health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenCollectionMissing() throws Exception {
        QdrantClient client = mock(QdrantClient.class);
        ListenableFuture<Boolean> future = futureReturning(false);
        when(client.collectionExistsAsync("products")).thenReturn(future);

        Health health = new QdrantHealthIndicator(client, "products").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenCollectionCheckTimesOut() throws Exception {
        QdrantClient client = mock(QdrantClient.class);
        ListenableFuture<Boolean> future = futureFailingWith(new TimeoutException());
        when(client.collectionExistsAsync("products")).thenReturn(future);

        Health health = new QdrantHealthIndicator(client, "products").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenCollectionCheckFails() throws Exception {
        QdrantClient client = mock(QdrantClient.class);
        ListenableFuture<Boolean> future =
                futureFailingWith(new ExecutionException("connection refused", new RuntimeException()));
        when(client.collectionExistsAsync("products")).thenReturn(future);

        Health health = new QdrantHealthIndicator(client, "products").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @SuppressWarnings("unchecked")
    private static ListenableFuture<Boolean> futureReturning(boolean value) throws Exception {
        ListenableFuture<Boolean> future = (ListenableFuture<Boolean>) mock(ListenableFuture.class);
        when(future.get(1, TimeUnit.SECONDS)).thenReturn(value);
        return future;
    }

    @SuppressWarnings("unchecked")
    private static ListenableFuture<Boolean> futureFailingWith(Exception exception) throws Exception {
        ListenableFuture<Boolean> future = (ListenableFuture<Boolean>) mock(ListenableFuture.class);
        when(future.get(1, TimeUnit.SECONDS)).thenThrow(exception);
        return future;
    }
}
