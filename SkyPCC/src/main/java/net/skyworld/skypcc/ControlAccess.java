package net.skyworld.skypcc;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Mutations are local-only, same-origin and bearer authenticated. No token in URLs. */
final class ControlAccess {
    static boolean permitted(boolean enabled, String secret, int port, InetAddress remote,
            String method, String host, String origin, String authorization, String contentType) {
        if (!enabled || secret == null || secret.length() < 32 || remote == null || !remote.isLoopbackAddress()
                || !"POST".equals(method) || host == null || origin == null || authorization == null
                || contentType == null || !contentType.split(";", 2)[0].trim().equals("application/json")) return false;
        try {
            URI uri = URI.create(origin);
            if (!"http".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !"".equals(uri.getRawPath()) || uri.getPort() != port
                    || !java.util.Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost())
                    || !host.equalsIgnoreCase(uri.getRawAuthority())) return false;
            return MessageDigest.isEqual(("Bearer " + secret).getBytes(StandardCharsets.UTF_8),
                    authorization.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ex) { return false; }
    }
}
