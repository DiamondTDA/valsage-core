
package io.github.xylars.valsage.service;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthCookieService {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    public ResponseCookie createRefreshTokenCookie(
            String token,
            Instant expiresAt,
            boolean rememberMe
    ) {
        ResponseCookie.ResponseCookieBuilder builder =
                ResponseCookie.from(COOKIE_NAME, token)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .path(COOKIE_PATH);

        if (rememberMe) {
            Duration remaining = Duration.between(
                    Instant.now(), expiresAt
            );

            builder.maxAge(
                    remaining.isNegative()
                            ? Duration.ZERO
                            : remaining
            );
        }

        return builder.build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
