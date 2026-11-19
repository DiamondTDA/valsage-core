
package io.github.xylars.valsage.service;

import io.github.xylars.valsage.entity.MyUser;
import io.github.xylars.valsage.entity.RefreshToken;
import io.github.xylars.valsage.repo.MyUserRepo;
import io.github.xylars.valsage.repo.RefreshTokenRepo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final MyUserRepo myUserRepo;
    private final RefreshTokenRepo refreshTokenRepo;

    public RefreshTokenService(
            MyUserRepo myUserRepo,
            RefreshTokenRepo refreshTokenRepo
    ) {
        this.myUserRepo = myUserRepo;
        this.refreshTokenRepo = refreshTokenRepo;
    }

    // Create an independent session for each login

    @Transactional
    public RefreshToken createOrReuseSession(
            String email,
            boolean rememberMe,
            String existingRefreshToken
    ) {
        MyUser user = myUserRepo.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        Instant now = Instant.now();

        Instant expiry = now.plus(
                rememberMe ? 30 : 1,
                ChronoUnit.DAYS
        );

        boolean cookieReceived = existingRefreshToken != null
                && !existingRefreshToken.isBlank();

        System.out.println("COOKIE RECEIVED: " + cookieReceived);

        // Check whether the client has an existing session
        if (cookieReceived) {

            Optional<RefreshToken> existing =
                    refreshTokenRepo.findByToken(existingRefreshToken);

            System.out.println("TOKEN FOUND: " + existing.isPresent());

            if (existing.isPresent()) {

                RefreshToken token = existing.get();

                boolean sameUser =
                        token.getUser().getId().equals(user.getId());

                boolean notExpired =
                        token.getExpiryDate().isAfter(now);

                System.out.println("SAME USER: " + sameUser);
                System.out.println("NOT EXPIRED: " + notExpired);

                if (sameUser && notExpired) {

                    System.out.println(
                            "REUSING SESSION: " + token.getSessionId()
                    );

                    // Preserve session ID but rotate refresh token
                    token.setToken(UUID.randomUUID().toString());
                    token.setExpiryDate(expiry);

                    return refreshTokenRepo.save(token);
                }
            }
        }

        // Create a new session if no reusable session was found
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUser(user);
        refreshToken.setSessionId(UUID.randomUUID().toString());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(expiry);

        System.out.println(
                "CREATING NEW SESSION: " + refreshToken.getSessionId()
        );

        return refreshTokenRepo.save(refreshToken);
    }



    // Revoke a refresh token after verifying its owner
    @Transactional
    public void deleteByToken(String tokenValue, String email) {

        RefreshToken refreshToken = refreshTokenRepo.findByToken(tokenValue)
                .orElseThrow(() ->
                        new IllegalArgumentException("Refresh token not found")
                );

        validateOwnership(refreshToken, email);

        refreshTokenRepo.delete(refreshToken);
    }

    // Validate refresh token and delete it if expired
    @Transactional
    public Optional<RefreshToken> validateAndVerifyExpiry(String tokenString) {

        if (tokenString == null || tokenString.isBlank()) {
            return Optional.empty();
        }

        Optional<RefreshToken> existingToken =
                refreshTokenRepo.findByToken(tokenString);

        if (existingToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken refreshToken = existingToken.get();

        if (!refreshToken.getExpiryDate().isAfter(Instant.now())) {
            refreshTokenRepo.delete(refreshToken);
            return Optional.empty();
        }

        return Optional.of(refreshToken);
    }

    // Logout from the current device using the JWT session ID
    @Transactional
    public void deleteBySessionId(String sessionId, String email) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }

        RefreshToken refreshToken = refreshTokenRepo
                .findBySessionId(sessionId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Session not found")
                );

        validateOwnership(refreshToken, email);

        refreshTokenRepo.delete(refreshToken);
    }

    // Logout from every device
    @Transactional
    public void deleteAllByEmail(String email) {

        MyUser user = myUserRepo.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        refreshTokenRepo.deleteAllByUser(user);
    }

    // Shared ownership validation
    private void validateOwnership(RefreshToken refreshToken, String email) {

        if (!refreshToken.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException(
                    "Refresh token does not belong to the authenticated user"
            );
        }
    }
}
