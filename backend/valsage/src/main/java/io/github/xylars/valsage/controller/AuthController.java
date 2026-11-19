
package io.github.xylars.valsage.controller;

import io.github.xylars.valsage.dto.LoginRequest;
import io.github.xylars.valsage.dto.RegisterRequest;
import io.github.xylars.valsage.entity.MyUser;
import io.github.xylars.valsage.entity.RefreshToken;
import io.github.xylars.valsage.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MyUserService myUserService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;

    public AuthController(
            MyUserService myUserService,
            AuthenticationManager authenticationManager,
            TokenBlacklistService tokenBlacklistService,
            RefreshTokenService refreshTokenService,
            JwtService jwtService, AuthCookieService authCookieService

    ) {
        this.myUserService = myUserService;
        this.authenticationManager = authenticationManager;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerUserAccount(
            @RequestBody @Valid RegisterRequest request
    ) {
        myUserService.registerUser(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUserAccount(
            @RequestBody @Valid LoginRequest request,
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String existingRefreshToken
    ) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        RefreshToken refreshToken =
                refreshTokenService.createOrReuseSession(
                        request.email(),
                        request.rememberMe(),
                        existingRefreshToken
                );

        String accessToken = jwtService.generateToken(
                request.email(),
                authentication.getAuthorities(),
                refreshToken.getSessionId()
        );

        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(
                refreshToken.getToken(),
                refreshToken.getExpiryDate(),
                request.rememberMe()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of(
                        "accessToken", accessToken,
                        "message", "Login successful"
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logoutUserAccount(
            JwtAuthenticationToken authentication
    ) {
        Jwt jwt = authentication.getToken();

        String sessionId = jwt.getClaimAsString("sessionId");

        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid session"
            );
        }

        refreshTokenService.deleteBySessionId(
                sessionId,
                jwt.getSubject()
        );

        tokenBlacklistService.blacklistToken(jwt.getTokenValue());

        ResponseCookie clearCookie =
                authCookieService.clearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(Map.of(
                        "message", "Logged out successfully"
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshAccessToken(
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String incomingRefreshToken
    ) {
        if (incomingRefreshToken == null ||
                incomingRefreshToken.isBlank()) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Refresh token missing"
                    ));
        }

        return refreshTokenService
                .validateAndVerifyExpiry(incomingRefreshToken)
                .map(token -> {

                    MyUser user = token.getUser();

                    String newAccessToken = jwtService.generateToken(
                            user.getEmail(),
                            user.getAuthorities(),
                            token.getSessionId()
                    );

                    Map<String, Object> response = new HashMap<>();
                    response.put("accessToken", newAccessToken);
                    response.put("refreshTokenExpiresAt", token.getExpiryDate());

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "error",
                                        "Invalid or expired session. Please sign in again."
                                ))
                );
    }
}
