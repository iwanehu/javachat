package com.chat.backend.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        List<String> protocols = request.getHeaders().get("Sec-WebSocket-Protocol");

        if (protocols != null && !protocols.isEmpty()) {
            // Si el cliente envió protocolos, le devolvemos el primero para no romper el handshake
            response.getHeaders().set("Sec-WebSocket-Protocol", protocols.get(0));
        } else {

            response.getHeaders().set("Sec-WebSocket-Protocol", "text");
        }

        System.out.println("-> Handshake autorizado hacia /ws. Cabeceras de protocolo alineadas.");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}