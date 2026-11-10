package com.ecomassistant.config;

import com.ecomassistant.retrieval.FakeEmbeddingModel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionDescription;
import io.qdrant.client.grpc.Collections.ListCollectionsRequest;
import io.qdrant.client.grpc.Collections.ListCollectionsResponse;
import io.qdrant.client.grpc.CollectionsGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantConnectionDetails;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class QdrantConfigTest {

    private static final String TEST_KEY = "test-qdrant-secret-value";
    private static final Metadata.Key<String> API_KEY = Metadata.Key.of("api-key", Metadata.ASCII_STRING_MARSHALLER);

    private Server server;

    @AfterEach
    void stopServer() throws InterruptedException {
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void bindsLocalDefaults() {
        context(Map.of()).withPropertyValues("spring.ai.vectorstore.qdrant.initialize-schema=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(QdrantClient.class);
                    var connection = ctx.getBean(QdrantConnectionDetails.class);
                    assertThat(connection.getHost()).isEqualTo("localhost");
                    assertThat(connection.getPort()).isEqualTo(6334);
                    assertThat(connection.getApiKey()).isNullOrEmpty();
                    assertThat(ctx.getBean(QdrantVectorStoreProperties.class).isUseTls()).isFalse();
                });
    }

    @Test
    void acceptsHttpsUrlAsHost(CapturedOutput output) {
        context(Map.of("QDRANT_HOST", "https://example.cloud.qdrant.io", "QDRANT_PORT", 6334))
                .withPropertyValues("spring.ai.vectorstore.qdrant.initialize-schema=false")
                .run(ctx -> assertThat(ctx).hasSingleBean(QdrantClient.class));
        assertThat(output).contains("Qdrant: host=example.cloud.qdrant.io port=6334 tls=true");
    }

    @Test
    void acceptsHttpUrlAsHost() throws IOException {
        AtomicReference<String> receivedKey = startServer(Status.OK);
        context(Map.of("QDRANT_HOST", "http://localhost", "QDRANT_PORT", server.getPort(),
                "QDRANT_API_KEY", TEST_KEY)).run(ctx -> {
                    assertThat(ctx).hasSingleBean(QdrantClient.class);
                    assertThat(receivedKey.get()).isEqualTo(TEST_KEY);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com:6334", "https://example.com/path",
            "https://secret@example.com", "https://example.com?key=secret", "ftp://example.com"})
    void rejectsAmbiguousHostUrlsWithoutLeakingContents(String host) {
        context(Map.of("QDRANT_HOST", host))
                .withPropertyValues("spring.ai.vectorstore.qdrant.initialize-schema=false")
                .run(ctx -> {
                    Throwable failure = ctx.getStartupFailure();
                    assertThat(failure).hasStackTraceContaining("QDRANT_HOST must be a hostname or HTTP(S) URL");
                    assertThat(org.assertj.core.util.Throwables.getStackTrace(failure)).doesNotContain("secret");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", TEST_KEY})
    void sendsOnlyNonemptyKeys(String apiKey, CapturedOutput output) throws IOException {
        AtomicReference<String> receivedKey = startServer(Status.OK);

        context(endpoint(apiKey, false)).run(ctx -> {
            assertThat(ctx).hasSingleBean(QdrantClient.class);
            assertThat(receivedKey.get()).isEqualTo(apiKey.isBlank() ? null : apiKey);
        });
        assertThat(output).contains("Qdrant: host=localhost port=" + server.getPort() + " tls=false apiKey="
                + (apiKey.isBlank() ? "absent" : "present")).doesNotContain(TEST_KEY);
    }

    @Test
    void reportsRejectedKeyDuringSchemaInitializationWithoutLeakingIt(CapturedOutput output) throws IOException {
        startServer(Status.PERMISSION_DENIED.withDescription("Rejected key " + TEST_KEY));

        context(endpoint(TEST_KEY, false)).run(ctx -> {
            Throwable failure = ctx.getStartupFailure();
            assertThat(failure)
                    .hasStackTraceContaining("Qdrant authentication failed for localhost:" + server.getPort())
                    .hasStackTraceContaining("Check QDRANT_API_KEY");
            assertThat(org.assertj.core.util.Throwables.getStackTrace(failure)).doesNotContain(TEST_KEY);
        });
        assertThat(output).doesNotContain(TEST_KEY);
    }

    @Test
    void usesTlsWhenRequestedAndReportsProtocolMismatch(CapturedOutput output) throws IOException {
        AtomicReference<String> receivedKey = startServer(Status.OK);

        context(endpoint(TEST_KEY, true)).run(ctx -> assertThat(ctx.getStartupFailure())
                .hasStackTraceContaining("Qdrant cluster at localhost:" + server.getPort())
                .hasStackTraceContaining("Check QDRANT_HOST, QDRANT_PORT and QDRANT_USE_TLS"));
        assertThat(receivedKey).hasNullValue();
        assertThat(output).contains("tls=true apiKey=present").doesNotContain(TEST_KEY);
    }

    private ApplicationContextRunner context(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> {
                    var sources = ctx.getEnvironment().getPropertySources();
                    String sourceName = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
                    sources.replace(sourceName, new SystemEnvironmentPropertySource(sourceName, variables));
                    new ConfigDataApplicationContextInitializer().initialize(ctx);
                })
                .withUserConfiguration(QdrantConfig.class, FakeEmbeddingModel.class)
                .withConfiguration(AutoConfigurations.of(QdrantVectorStoreAutoConfiguration.class));
    }

    private Map<String, Object> endpoint(String apiKey, boolean tls) {
        return Map.of("QDRANT_HOST", "localhost", "QDRANT_PORT", server.getPort(),
                "QDRANT_API_KEY", apiKey, "QDRANT_USE_TLS", tls);
    }

    private AtomicReference<String> startServer(Status response) throws IOException {
        AtomicReference<String> receivedKey = new AtomicReference<>();
        server = ServerBuilder.forPort(0)
                .addService(new CollectionsGrpc.CollectionsImplBase() {
                    @Override
                    public void list(ListCollectionsRequest request, StreamObserver<ListCollectionsResponse> observer) {
                        if (!response.isOk()) {
                            observer.onError(response.asRuntimeException());
                            return;
                        }
                        observer.onNext(ListCollectionsResponse.newBuilder()
                                .addCollections(CollectionDescription.newBuilder().setName("products"))
                                .build());
                        observer.onCompleted();
                    }
                })
                .intercept(new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                        receivedKey.set(headers.get(API_KEY));
                        return next.startCall(call, headers);
                    }
                })
                .build().start();
        return receivedKey;
    }
}
