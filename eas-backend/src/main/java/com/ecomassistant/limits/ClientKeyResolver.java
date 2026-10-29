package com.ecomassistant.limits;

import com.ecomassistant.config.EcomLimitsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Identifies the client behind the CDN. The origin is reachable only through CloudFront,
 * which appends the viewer IP as the last {@code X-Forwarded-For} entry, so earlier
 * (client-supplied) entries are ignored: the entry {@code trustedProxyHops} positions from
 * the right is used. When the header is absent (local development, tests) the servlet
 * remote address is used. IPv6 clients are reduced to their /64 prefix so one client's
 * 2^64 addresses count as a single key.
 *
 * <p>The trust assumption is documented in the design: if the origin were reachable
 * directly, the header could be forged; the origin lock-down in {@code aws-backend-host}
 * prevents that.
 */
@Component
public class ClientKeyResolver {

    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private final int trustedProxyHops;

    public ClientKeyResolver(EcomLimitsProperties properties) {
        this.trustedProxyHops = properties.trustedProxyHops();
    }

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        String address = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : selectForwardedEntry(forwarded);
        return normalize(address);
    }

    private String selectForwardedEntry(String forwarded) {
        String[] entries = forwarded.split(",");
        int index = Math.max(0, entries.length - trustedProxyHops);
        return entries[index].trim();
    }

    private String normalize(String address) {
        if (address == null || address.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = address.trim();
        return trimmed.indexOf(':') >= 0 ? reduceIpv6(trimmed) : trimmed;
    }

    private String reduceIpv6(String address) {
        try {
            InetAddress inet = InetAddress.getByName(address);
            if (inet instanceof Inet6Address) {
                byte[] bytes = inet.getAddress();
                Arrays.fill(bytes, 8, 16, (byte) 0);
                return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
            }
        } catch (UnknownHostException ignored) {
            // Not a parseable IPv6 literal: keep the raw value.
        }
        return address;
    }
}
