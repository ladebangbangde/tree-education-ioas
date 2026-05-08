package com.treeeducation.ioas.auth;

import com.treeeducation.ioas.system.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Issues and validates JWT access tokens. */
@Service
public class JwtService {
    private final JwtProperties props; private final SecretKey key;
    public JwtService(JwtProperties props) { this.props = props; this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8)); }
    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(props.issuer()).subject(user.getUsername()).claim("uid", user.getId()).claim("role", user.getRoleCode())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(props.expiresMinutes() * 60))).signWith(key).compact();
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
