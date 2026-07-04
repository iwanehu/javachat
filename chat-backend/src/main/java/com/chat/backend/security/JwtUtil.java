package com.chat.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct; // Asegura la ejecución post-inyección
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:ClaveDeDesarrolloLocalParaEvitarErroresDeLongitudMinimaDeMasDeTreintaYDosCaracteres}")
    private String secretKeyString;

    // La clave final calculada compartida de forma segura por todos los hilos
    private Key cachedSigningKey;

    private final long EXPIRATION_TIME = 86400000; // 24 horas

    @PostConstruct
    public void init() {
        String cleanedKey = this.secretKeyString.trim();
        System.out.println("====== [JWT CONFIG] Inicializando clave del sistema. Longitud: " + cleanedKey.length() + " ======");

        try {
            // Intentamos decodificarlo como Base64 real (Entorno de producción)
            byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
            this.cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
            System.out.println("[JWT CONFIG] Clave Base64 decodificada e instanciada con éxito.");
        } catch (IllegalArgumentException e) {
            // Fallback defensivo para desarrollo local si la clave no está en Base64
            System.out.println("[JWT WARN] La clave no es Base64 válida. Utilizando bytes UTF-8 planos.");
            byte[] keyBytes = cleanedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            this.cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    private Key getSigningKey() {
        return this.cachedSigningKey;
    }

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
            // Aseguramos una limpieza básica si el token viene con espacios accidentales del JSON
            String tokenLimpio = token.trim();
            Claims claims = extraerClaims(tokenLimpio);
            return claims.getExpiration().after(new Date());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            System.err.println("Validación JWT: La firma no coincide con la clave compartida.");
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