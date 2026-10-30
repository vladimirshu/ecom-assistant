package com.ecomassistant.listing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCursorCodecTest {

    private final CatalogCursorCodec codec = new CatalogCursorCodec("test-secret");

    @Test
    void roundTripPreservesAllFields() {
        CatalogCursor cursor = new CatalogCursor("rev-1", "men-jeans", 24, 48, 1_700_000_000L);
        String token = codec.encode(cursor);

        CatalogCursor decoded = codec.decode(token);

        assertThat(decoded.revision()).isEqualTo("rev-1");
        assertThat(decoded.category()).isEqualTo("men-jeans");
        assertThat(decoded.limit()).isEqualTo(24);
        assertThat(decoded.offset()).isEqualTo(48);
        assertThat(decoded.issuedAt()).isEqualTo(1_700_000_000L);
    }

    @Test
    void nullCategoryRoundTrips() {
        CatalogCursor cursor = new CatalogCursor("rev-1", null, 24, 0, 1L);
        assertThat(codec.decode(codec.encode(cursor)).category()).isNull();
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = codec.encode(new CatalogCursor("rev-1", null, 24, 0, 1L));
        int dot = token.lastIndexOf('.');
        char firstSigChar = token.charAt(dot + 1);
        char flipped = firstSigChar == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, dot + 1) + flipped + token.substring(dot + 2);

        assertThatThrownBy(() -> codec.decode(tampered))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-token"))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void blankTokenIsRejected() {
        assertThatThrownBy(() -> codec.decode(null)).isInstanceOf(CatalogRequestException.class);
        assertThatThrownBy(() -> codec.decode("")).isInstanceOf(CatalogRequestException.class);
    }
}
