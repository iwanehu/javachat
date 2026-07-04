package com.chat.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64; // IMPORTANTE: Usaremos el decodificador nativo
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:ClaveDeDesarrolloLocalParaEvitarErroresDeLongitudMinimaDeMasDeTreintaYDosCaracteres}")
    private String secretKeyString;

    private final long EXPIRATION_TIME = 86400000; // 24 horas

    private Key getSigningKey() {
        try {
            // Intentamos decodificarlo como Base64 real (lo correcto para producción y .env)
            byte[] keyBytes = Base64.getDecoder().decode(this.secretKeyString.trim());
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            // Fallback defensivo: Si no es Base64 válido (como la clave larga de fallback en local),
            // leemos los bytes planos en UTF-8 para que no rompa el inicio.
            byte[] keyBytes = this.secretKeyString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return Keys.hmacShaKeyFor(keyBytes);
        }
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
            boolean noHaExpirado = claims.getExpiration().after(new Date());
            if (!noHaExpirado) {
                System.err.println("Validación JWT: El token ha expirado.");
            }
            return noHaExpirado;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            System.err.println("Validación JWT: La firma no coincide. Error de clave.");
            return false;
        } catch (Exception e) {
            System.err.println("Error validando JWT en WebSocket: " + e.getMessage());
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