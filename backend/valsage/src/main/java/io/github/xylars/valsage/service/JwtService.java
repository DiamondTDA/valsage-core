package io.github.xylars.valsage.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final int EXPIRATION_MINUTES = 15;
    private static final String ISSUER = "self";

    private final JwtEncoder jwtEncoder;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    // Used during login
    public String generateToken(Authentication authentication, String sessionId) {

        String email = authentication.getPrincipal() instanceof UserDetails user
                ? user.getUsername()
                : authentication.getName();

        return generateToken(email, authentication.getAuthorities(), sessionId);
    }

    // Used during login and token refresh
    public String generateToken(
            String email,
            Collection<? extends GrantedAuthority> authorities,
            String sessionId
    ) {
        Instant now = Instant.now();

        String scope = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES))
                .subject(email)
                .claim("scope", scope)
                .claim("sessionId", sessionId)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}