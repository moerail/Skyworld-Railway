package net.skyworld.skypcc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Mutations require an enabled control channel and a matching bearer token. */
final class ControlAccess {
    static boolean permitted(boolean enabled, String secret,
            String method, String authorization, String contentType) {
        if (!enabled || secret == null || secret.length() < 32
                || !"POST".equals(method) || authorization == null
                || contentType == null || !contentType.split(";", 2)[0].trim().equals("application/json")) return false;
        return MessageDigest.isEqual(("Bearer " + secret).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8));
    }
}
