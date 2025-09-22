package com.ecomassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class HttpClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientConfig.class);

    @Bean
    public RestClientCustomizer restClientLogger() {
        return restClient -> restClient.requestInterceptor(
                (request, body, execution) -> {
                    // Log basic request line
                    System.out.println("AI REQUEST: " + request.getMethod() + " " + request.getURI());

                    // Log body (if present)
                    if (body != null && body.length > 0) {
                        String payload = new String(body, StandardCharsets.UTF_8);
                        System.out.println("BODY: " + payload);
                    }

                    ClientHttpResponse response = execution.execute(request, body);
                    ClientHttpResponse bufferedResponse = new BufferingClientHttpResponseWrapper(response);

                    // Log response body
                    try (InputStream inputStream = bufferedResponse.getBody()) {
                        byte[] bytes = inputStream.readAllBytes();
                        if (bytes.length > 0) {
                            System.out.println("AI RESPONSE BODY: " + new String(bytes, StandardCharsets.UTF_8));
                        }
                    }

                    return bufferedResponse;
                });
    }

    private static class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

        private final ClientHttpResponse response;
        private byte[] body;

        BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
            this.response = response;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return this.response.getStatusCode();
        }

        @Override
        public int getRawStatusCode() throws IOException {
            return this.response.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return this.response.getStatusText();
        }

        @Override
        public void close() {
            this.response.close();
        }

        @Override
        public InputStream getBody() throws IOException {
            if (this.body == null) {
                try (InputStream bodyStream = this.response.getBody()) {
                    if (bodyStream != null) {
                        this.body = bodyStream.readAllBytes();
                    } else {
                        this.body = new byte[0];
                    }
                }
            }
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.response.getHeaders();
        }
    }
}