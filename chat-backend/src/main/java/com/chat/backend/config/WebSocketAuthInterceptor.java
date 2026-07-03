package com.chat.backend.config;

import com.chat.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            // Extraemos el parámetro 'token' de la Query String (?token=...)
            String token = servletRequest.getServletRequest().getParameter("token");

            if (token != null && !token.trim().isEmpty()) {
                String username;

                // SI CONTIENE PUNTOS, ES UN JWT REAL (Estructura habitual de un token firmado)
                if (token.contains(".")) {
                    if (jwtUtil.validarToken(token)) {
                        username = jwtUtil.extraerUsername(token);
                    } else {
                        System.out.println("Conexión WebSocket rechazada: Token JWT inválido.");
                        return false;
                    }
                } else {
                    // 🟢 MODO BYPASS PARA PRUEBAS: Si pasas texto plano (ej. "prueba1"), lo tratamos directo como el username
                    username = token;
                }

                // Atamos el usuario autenticado a los atributos de la sesión WebSocket
                attributes.put("username", username);
                System.out.println("Handshake aprobado para el usuario: " + username);
                return true;
            }
        }

        System.out.println("Conexión WebSocket rechazada: Token inválido o ausente.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
}