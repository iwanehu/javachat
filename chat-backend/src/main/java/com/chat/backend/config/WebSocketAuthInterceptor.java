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

        // Extraemos los parámetros directamente de la Query de la URI de forma nativa e infalible
        String query = request.getURI().getQuery();
        String token = null;

        if (query != null && query.contains("token=")) {
            token = query.split("token=")[1].split("&")[0];
            token = URLDecoder.decode(token, StandardCharsets.UTF_8);
        }

        if (token != null && !token.trim().isEmpty()) {
            String username;

            // Si contiene puntos, validamos como JWT legítimo
            if (token.contains(".")) {
                if (jwtUtil.validarToken(token)) {
                    username = jwtUtil.extraerUsername(token);
                } else {
                    System.out.println("Conexión WebSocket rechazada: Token JWT inválido.");
                    return false;
                }
            } else {
                // Modo bypass para pruebas locales o con alias planos (ej. prueba4)
                username = token;
            }

            // Inyectamos el username en los atributos de la sesión
            attributes.put("username", username);
            System.out.println("Handshake aprobado nativamente para el usuario: " + username);
            return true;
        }

        System.out.println("Conexión WebSocket rechazada: Token ausente en la URI.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}