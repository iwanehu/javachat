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

            String token = null;

            // 1. Intento A: Leer desde el subprotocolo WebSockets (Sec-WebSocket-Protocol)
            List<String> protocols = request.getHeaders().get("Sec-WebSocket-Protocol");
            if (protocols != null && !protocols.isEmpty()) {
                String rawProtocol = protocols.get(0);
                // Extraemos el primer elemento si viene separado por comas
                token = rawProtocol.split(",")[0].trim();
                // OBLIGATORIO: Devolver la cabecera idéntica en la respuesta para validar el handshake
                response.getHeaders().set("Sec-WebSocket-Protocol", rawProtocol);
                System.out.println("Token detectado vía Sec-WebSocket-Protocol");
            }

            // 2. Intento B: Si falló el subprotocolo, buscamos en el query string tradicional (?token=...)
            if (token == null || token.isEmpty()) {
                String query = request.getURI().getQuery();
                if (query != null && query.contains("token=")) {
                    token = query.split("token=")[1].split("&")[0];
                    token = URLDecoder.decode(token, StandardCharsets.UTF_8);
                    System.out.println("Token detectado vía Query String alternativo");
                }
            }

            // 3. Procesar y validar el token obtenido
            if (token != null && !token.trim().isEmpty()) {
                String username;

                // Si parece un JWT estructurado (contiene puntos)
                if (token.contains(".")) {
                    if (jwtUtil.validarToken(token)) {
                        username = jwtUtil.extraerUsername(token);
                    } else {
                        System.out.println("WS Rechazado: El Token JWT no es válido.");
                        return false;
                    }
                } else {
                    // Fallback para desarrollo/usuarios de prueba sin JWT completo
                    username = token;
                }

                attributes.put("username", username);
                System.out.println("Handshake exitoso en Render para el usuario: " + username);
                return true;
            }

            // 4. Última línea de defensa: Si seguimos sin token, imprimimos alertas para depurar
            System.err.println("WS Rechazado: Cabeceras recibidas: " + request.getHeaders().keySet());
            return false;

        } catch (Exception e) {
            System.err.println("Excepción crítica en el handshake: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}