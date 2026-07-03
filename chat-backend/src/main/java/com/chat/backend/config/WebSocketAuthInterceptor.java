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
        } catch (Exception e) {
            System.err.println("Error procesando handshake: " + e.getMessage());
        }

        System.out.println("WS Rechazado: Falta token.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}