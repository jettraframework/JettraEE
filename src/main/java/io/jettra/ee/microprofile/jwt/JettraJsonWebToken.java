package io.jettra.ee.microprofile.jwt;

import io.jettra.jwt.JettraJWT;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.*;

/**
 * Implementación de JsonWebToken para Eclipse MicroProfile JWT Auth 2.1 en JettraEE.
 */
public class JettraJsonWebToken implements JsonWebToken {

    private final String rawToken;
    private final String subject;
    private final Map<String, Object> claims = new HashMap<>();

    public JettraJsonWebToken(String rawToken, String defaultSecret) {
        this.rawToken = rawToken;
        String user = "anonymous";
        if (rawToken != null && !rawToken.isBlank()) {
            try {
                JettraJWT jwt = new JettraJWT(defaultSecret, 3600000);
                Map<String, Object> payload = jwt.getPayload(rawToken);
                if (payload != null) {
                    claims.putAll(payload);
                    user = jwt.extractUsername(rawToken);
                }
            } catch (Exception ignored) {}
        }
        this.subject = user;
    }

    public JettraJsonWebToken(String rawToken, String subject, Map<String, Object> claims) {
        this.rawToken = rawToken;
        this.subject = subject;
        if (claims != null) {
            this.claims.putAll(claims);
        }
    }

    @Override
    public String getName() {
        return subject;
    }

    @Override
    public String getRawToken() {
        return rawToken;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public Set<String> getClaimNames() {
        return claims.keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getClaim(String claimName) {
        return (T) claims.get(claimName);
    }

    @Override
    public Set<String> getGroups() {
        Object g = claims.get("groups");
        if (g == null) g = claims.get("roles");
        if (g instanceof Collection<?> col) {
            Set<String> set = new HashSet<>();
            for (Object o : col) set.add(o.toString());
            return set;
        }
        if (g instanceof String s) {
            return new HashSet<>(Arrays.asList(s.split(",")));
        }
        return Collections.emptySet();
    }
}
