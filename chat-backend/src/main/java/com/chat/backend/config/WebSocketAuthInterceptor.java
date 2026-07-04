package com.chat.backend.config;

import com.chat.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        try {
            if (request.getMethod().name().equalsIgnoreCase("OPTIONS")) {
                return true;
            }

            // Recuperamos el token desde las cabeceras de subprotocolo WebSocket
            List<String> protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
            String token = null;

            if (protocols != null && !protocols.isEmpty()) {
                // El navegador envía los protocolos separados por comas; tomamos el primero que será nuestro token
                token = protocols.get(0).split(",")[0].trim();
                // Devolvemos el mismo protocolo en la respuesta para validar el handshake en el cliente
                response.getHeaders().set("Sec-WebSocket-Protocol", token);
            }

            if (token != null && !token.isEmpty()) {
                String username;
                if (token.contains(".")) {
                    if (jwtUtil.validarToken(token)) {
                        username = jwtUtil.extraerUsername(token);
                    } else {
                        System.out.println("WS Rechazado: JWT Inválido.");
                        return false;
                    }
                } else {
                    username = token;
                }

                attributes.put("username", username);
                return true;
            }

            System.out.println("WS Rechazado: No se encontró token en Sec-WebSocket-Protocol.");
            return false;

        } catch (Exception e) {
            System.err.println("Error procesando handshake en Render: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}