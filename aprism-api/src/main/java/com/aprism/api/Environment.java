package com.aprism.api;

/**
 * Selects which sides of the game a mod is active on.
 *
 * <p>Mirrors the Fabric environment selector so that mods authored against
 * Fabric idioms map cleanly into the Aprism lifecycle. {@link #COMMON} runs on
 * both the client and the dedicated server, {@link #CLIENT} only runs where a
 * rendering client exists, and {@link #DEDICATED_SERVER} only runs on a
 * dedicated server.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum Environment {
    /** Active on both the client and the dedicated server. */
    COMMON,
    /** Active only on the integrated and dedicated client. */
    CLIENT,
    /** Active only on a dedicated server. */
    DEDICATED_SERVER;

    /**
     * Parses a manifest environment token into an {@link Environment}.
     *
     * @param token the token from the manifest ({@code "*"}, {@code "client"},
     *              {@code "server"} or {@code "dedicated_server"}); case-insensitive
     * @return the matching environment, defaulting to {@link #COMMON} for {@code "*"} or null
     */
    public static Environment parse(String token) {
        if (token == null || token.isBlank() || "*".equals(token.trim())) {
            return COMMON;
        }
        return switch (token.trim().toLowerCase()) {
            case "client" -> CLIENT;
            case "server", "dedicated_server" -> DEDICATED_SERVER;
            default -> COMMON;
        };
    }
}
