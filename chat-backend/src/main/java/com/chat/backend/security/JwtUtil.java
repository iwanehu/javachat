package com.chat.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:}")
    private String secretKeyString;

    private final long EXPIRATION_TIME = 86400000; // 24 horas

    private Key getSigningKey() {
        byte[] keyBytes = this.secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Genera un token único usando los datos del usuario como semilla
    public String generarToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extraerUsername(String token) {
        return extraerClaims(token).getSubject();
    }

    public boolean validarToken(String token) {
        try {
            Claims claims = extraerClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            System.err.println("Error validando JWT: " + e.getMessage());
            return false;
        }
    }

    private Claims extraerClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}