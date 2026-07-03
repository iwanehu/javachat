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

            if (token != null && jwtUtil.validarToken(token)) {
                String username = jwtUtil.extraerUsername(token);
                //  Atamos el usuario autenticado a los atributos de la sesión WebSocket de manera segura
                attributes.put("username", username);
                return true; // Token válido, permitimos la conexión
            }
        }
        System.out.println("Conexión WebSocket rechazada: Token inválido o ausente.");
        return false; // Rechaza la conexión HTTP Handshake (401 Unauthorized)
    }


    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}



}
