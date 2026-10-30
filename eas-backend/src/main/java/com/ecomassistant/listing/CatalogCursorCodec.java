package com.ecomassistant.listing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Signs and verifies opaque catalog cursors with HMAC-SHA256. A blank configured secret
 * causes a random per-process key, so a service restart invalidates previously issued
 * cursors (an allowed behavior). Cursor payloads are never encrypted against the owner
 * (they hold no secrets), only authenticated against tampering.
 */
@Component
public class CatalogCursorCodec {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public CatalogCursorCodec(@Value("${ecom.browsing.cursor-secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
        }
        else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String encode(CatalogCursor cursor) {
        try {
            byte[] payload = JSON_MAPPER.writeValueAsBytes(cursor);
            return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to encode catalog cursor", e);
        }
    }

    public CatalogCursor decode(String token) {
        if (token == null || token.isBlank()) {
            throw new CatalogRequestException("Cursor is missing");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw new CatalogRequestException("Cursor is malformed");
        }
        try {
            byte[] payload = DECODER.decode(parts[0]);
            byte[] signature = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                throw new CatalogRequestException("Cursor is invalid");
            }
            return JSON_MAPPER.readValue(payload, CatalogCursor.class);
        }
        catch (CatalogRequestException e) {
            throw e;
        }
        catch (Exception e) {
            throw new CatalogRequestException("Cursor is malformed");
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to sign catalog cursor", e);
        }
    }
}
