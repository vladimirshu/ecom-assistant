package com.ecomassistant.limits;

import com.ecomassistant.config.EcomLimitsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeyResolverTest {

    private final ClientKeyResolver resolver = new ClientKeyResolver(
            new EcomLimitsProperties(5, 20, 300, 1));

    @Test
    void forgedLeadingEntriesAreIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientKeyResolver.X_FORWARDED_FOR, "1.2.3.4, 203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void oneTrustedHopUsesRightmostEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientKeyResolver.X_FORWARDED_FOR, "203.0.113.9, 198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void twoTrustedHopsUseSecondFromRight() {
        ClientKeyResolver twoHops = new ClientKeyResolver(new EcomLimitsProperties(5, 20, 300, 2));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientKeyResolver.X_FORWARDED_FOR, "10.0.0.1, 203.0.113.9, 198.51.100.7");

        assertThat(twoHops.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void noHeaderUsesRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.42");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.42");
    }

    @Test
    void ipv6ClientsSharePrefix() {
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setRemoteAddr("2001:db8:1:2::1");
        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setRemoteAddr("2001:db8:1:2:ffff::7");

        assertThat(resolver.resolve(first)).isEqualTo(resolver.resolve(second));
        assertThat(resolver.resolve(first)).isEqualTo("2001:db8:1:2:0:0:0:0/64");
    }

    @Test
    void blankForwardedHeaderFallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientKeyResolver.X_FORWARDED_FOR, "  ");
        request.setRemoteAddr("192.0.2.42");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.42");
    }
}
