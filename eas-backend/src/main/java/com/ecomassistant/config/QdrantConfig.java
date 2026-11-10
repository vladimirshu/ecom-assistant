package com.ecomassistant.config;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.TlsChannelCredentials;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantConnectionDetails;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "qdrant", matchIfMissing = true)
class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    QdrantClient qdrantClient(QdrantConnectionDetails connection, QdrantVectorStoreProperties properties) {
        String configuredHost = connection.getHost();
        URI url = hostUrl(configuredHost);
        String host = url == null ? configuredHost : url.getHost();
        int port = connection.getPort();
        boolean tls = properties.isUseTls() || (url != null && "https".equalsIgnoreCase(url.getScheme()));
        String apiKey = connection.getApiKey();
        boolean hasApiKey = StringUtils.hasText(apiKey);
        log.info("Qdrant: host={} port={} tls={} apiKey={}", host, port, tls,
                hasApiKey ? "present" : "absent");

        var credentials = tls ? TlsChannelCredentials.create() : InsecureChannelCredentials.create();
        var channel = Grpc.newChannelBuilderForAddress(host, port, credentials)
                .intercept(new ConnectionErrors(host + ":" + port, hasApiKey ? apiKey : null))
                .build();
        // The SDK's version probe runs before it attaches the API key and has no deadline.
        var builder = QdrantGrpcClient.newBuilder(channel, true, false).withTimeout(REQUEST_TIMEOUT);
        if (hasApiKey) {
            builder.withApiKey(apiKey);
        }
        return new QdrantClient(builder.build());
    }

    private static URI hostUrl(String host) {
        if (!host.contains("://")) {
            return null;
        }
        try {
            URI url = URI.create(host);
            if (("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))
                    && url.getHost() != null && url.getPort() == -1 && url.getUserInfo() == null
                    && (url.getPath().isEmpty() || "/".equals(url.getPath()))
                    && url.getQuery() == null && url.getFragment() == null) {
                return url;
            }
        } catch (IllegalArgumentException ignored) {
            // Report invalid configuration without including potentially sensitive URL contents.
        }
        throw new IllegalArgumentException("QDRANT_HOST must be a hostname or HTTP(S) URL without credentials, port, path, query or fragment; configure the port with QDRANT_PORT");
    }

    // Translate errors at the shared channel so schema creation and every startup runner see them.
    private static final class ConnectionErrors implements ClientInterceptor {

        private final String target;
        private final String apiKey;

        private ConnectionErrors(String target, String apiKey) {
            this.target = target;
            this.apiKey = apiKey;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                CallOptions options, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
                @Override
                public void start(Listener<RespT> listener, Metadata headers) {
                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(listener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            Status diagnostic = diagnosticStatus(status);
                            super.onClose(diagnostic, diagnostic == status ? trailers : new Metadata());
                        }
                    }, headers);
                }
            };
        }

        private Status diagnosticStatus(Status status) {
            return switch (status.getCode()) {
                case UNAUTHENTICATED, PERMISSION_DENIED -> status.getCode().toStatus()
                        .withDescription("Qdrant authentication failed for " + target
                                + ". Check QDRANT_API_KEY and its permissions.");
                case UNAVAILABLE, DEADLINE_EXCEEDED -> status.getCode().toStatus()
                        .withDescription("Qdrant cluster at " + target
                                + " is unreachable or timed out. Check QDRANT_HOST, QDRANT_PORT and QDRANT_USE_TLS.");
                default -> status.isOk() || apiKey == null ? status : withoutCredentials(status);
            };
        }

        private Status withoutCredentials(Status status) {
            String description = status.getDescription();
            return status.getCode().toStatus().withDescription(
                    description == null ? null : description.replace(apiKey, "[redacted]"));
        }
    }
}
