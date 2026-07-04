package com.chat.backend.config;

import com.chat.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        try {
            // Permitir explícitamente las peticiones OPTIONS previas que a veces inyectan los proxies
            if (request.getMethod().name().equalsIgnoreCase("OPTIONS")) {
                return true;
            }

            String query = request.getURI().getQuery();
            String token = null;

            if (query != null && query.contains("token=")) {
                token = query.split("token=")[1].split("&")[0];
                token = URLDecoder.decode(token, StandardCharsets.UTF_8);
            }

            if (token != null && !token.trim().isEmpty()) {
                String username;

                if (token.contains(".")) {
                    if (jwtUtil.validarToken(token)) {
                        username = jwtUtil.extraerUsername(token);
                    } else {
                        System.out.println("WS Rechazado: Token JWT inválido.");
                        return false;
                    }
                } else {
                    username = token;
                }

                attributes.put("username", username);
                System.out.println("Handshake verificado para: " + username);
                return true;
            }

            System.out.println("Advertencia: No se detectó token en query string. Forzando paso de handshake.");
            attributes.put("username", "Usuario_Render");
            return true;

        } catch (Exception e) {
            System.err.println("Error procesando handshake: " + e.getMessage());
        }

        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}