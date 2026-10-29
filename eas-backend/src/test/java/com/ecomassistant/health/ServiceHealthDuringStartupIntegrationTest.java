package com.ecomassistant.health;

import com.ecomassistant.EcomAssistantApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.testcontainers.qdrant.QdrantContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code GET /api/v1/health} stays 503 while a startup runner is still
 * running (readiness {@code REFUSING_TRAFFIC}) and becomes 200 once it completes. The
 * application is started by hand so the probe can run during the blocked startup phase.
 */
class ServiceHealthDuringStartupIntegrationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void healthReturns503UntilStartupRunnerCompletes() throws Exception {
        try (QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.13.4")) {
            qdrant.start();

            CountDownLatch runnerStarted = new CountDownLatch(1);
            CountDownLatch releaseRunner = new CountDownLatch(1);
            AtomicInteger port = new AtomicInteger();

            SpringApplication app = new SpringApplication(EcomAssistantApplication.class);
            app.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "startupBlockingRunner",
                (CommandLineRunner) args -> {
                    runnerStarted.countDown();
                    if (!releaseRunner.await(60, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting for release");
                    }
                }));
            app.addListeners((ApplicationListener<WebServerInitializedEvent>) event ->
                port.set(event.getWebServer().getPort()));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<ConfigurableApplicationContext> contextFuture = executor.submit(() -> app.run(
                "--server.port=0",
                "--spring.ai.vectorstore.type=qdrant",
                "--spring.ai.vectorstore.qdrant.host=" + qdrant.getHost(),
                "--spring.ai.vectorstore.qdrant.port=" + qdrant.getGrpcPort(),
                "--spring.ai.model.chat=openai",
                "--spring.ai.model.embedding=none",
                "--spring.ai.openai.api-key=test-key",
                "--spring.ai.openai.base-url=http://localhost:1",
                "--ecom.import.sample-file=sampledata/test-apparel.csv"));

            try {
                assertThat(runnerStarted.await(120, TimeUnit.SECONDS))
                    .as("blocking runner should have started").isTrue();
                assertThat(awaitPort(port)).isPositive();

                HealthResult blocked = probeHealth(port.get());
                assertThat(blocked.status()).isEqualTo(503);
                JsonNode blockedBody = JSON_MAPPER.readTree(blocked.body());
                assertThat(blockedBody.get("status").asText()).isEqualTo("DOWN");
                assertThat(blockedBody.size()).isEqualTo(1);
            } finally {
                releaseRunner.countDown();
            }

            ConfigurableApplicationContext context = contextFuture.get(120, TimeUnit.SECONDS);
            try {
                HealthResult ready = probeHealth(port.get());
                assertThat(ready.status()).isEqualTo(200);
                JsonNode readyBody = JSON_MAPPER.readTree(ready.body());
                assertThat(readyBody.get("status").asText()).isEqualTo("UP");
                assertThat(readyBody.size()).isEqualTo(1);
            } finally {
                context.close();
                executor.shutdownNow();
            }
        }
    }

    private static int awaitPort(AtomicInteger port) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (port.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        return port.get();
    }

    private static HealthResult probeHealth(int port) {
        return RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/api/v1/health")
            .exchange((request, response) -> new HealthResult(
                response.getStatusCode().value(),
                readBody(response)));
    }

    private static String readBody(ClientHttpResponse response) {
        try (var body = response.getBody()) {
            return body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private record HealthResult(int status, String body) {
    }
}
