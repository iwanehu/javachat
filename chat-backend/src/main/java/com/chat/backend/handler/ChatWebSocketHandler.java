package com.chat.backend.handler;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Lista de sesiones activas globales
    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    // Mapa concurrente para rastrear el nombre de usuario de cada sesión activa
    private static final Map<WebSocketSession, String> mapaUsuarios = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!sesiones.contains(session)) {
            sesiones.add(session);
        }
        System.out.println("Nueva sesión WebSocket conectada: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Mensaje recibido: " + payload);

        try {
            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);

            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }
            if (nuevoMensaje.getDestinatario() == null) {
                nuevoMensaje.setDestinatario("TODOS");
            }

            // Interceptamos el mensaje de unión del SISTEMA
            if ("SISTEMA".equals(nuevoMensaje.getRemitente()) && nuevoMensaje.getContenido().contains("se ha unido al chat.")) {
                String nombreUsuario = nuevoMensaje.getContenido().split(" ")[0].trim();

                // Eliminamos sesiones colgadas previas del mismo usuario de AMBAS listas
                for (Map.Entry<WebSocketSession, String> entry : mapaUsuarios.entrySet()) {
                    if (entry.getValue().equals(nombreUsuario) && !entry.getKey().getId().equals(session.getId())) {
                        WebSocketSession sesionVieja = entry.getKey();
                        sesiones.remove(sesionVieja); // Lo sacamos del bucle de envíos global
                        mapaUsuarios.remove(sesionVieja); // Lo sacamos del mapa
                        if (sesionVieja.isOpen()) {
                            try { sesionVieja.close(); } catch (Exception e) {}
                        }
                    }
                }

                // Registramos la sesión limpia actual
                mapaUsuarios.put(session, nombreUsuario);
                System.out.println("Usuario registrado en memoria: " + nombreUsuario);

                // Retransmitimos el aviso de unión a los chats
                retransmitirMensaje(message);

                // Enviamos la lista actualizada
                enviarListaUsuariosActivos();
                return;
            }

            // Guardar en MongoDB mensajes normales
            mensajeRepository.save(nuevoMensaje);
            System.out.println("Mensaje persistido en MongoDB con ID: " + nuevoMensaje.getId());

        } catch (Exception e) {
            System.err.println("Error al parsear o persistir el mensaje: " + e.getMessage());
        }

        retransmitirMensaje(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        String usuarioSaliente = mapaUsuarios.remove(session);
        System.out.println("Sesión WebSocket cerrada: " + session.getId());

        if (usuarioSaliente != null) {
            try {
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", usuarioSaliente + " ha salido del chat.");
                String jsonSalida = objectMapper.writeValueAsString(avisoSalida);

                retransmitirMensaje(new TextMessage(jsonSalida));
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                System.err.println("Error al gestionar salida de usuario: " + e.getMessage());
            }
        }
    }

    private void retransmitirMensaje(TextMessage message) throws Exception {
        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(message);
            }
        }
    }

    private void enviarListaUsuariosActivos() throws Exception {
        Map<String, Object> payloadLista = new ConcurrentHashMap<>();
        payloadLista.put("type", "LISTA_USUARIOS");

        // Obtenemos solo los nombres únicos por seguridad antes de convertir a array
        String[] usuariosUnicos = mapaUsuarios.values().stream().distinct().toArray(String[]::new);
        payloadLista.put("usuarios", usuariosUnicos);

        String jsonLista = objectMapper.writeValueAsString(payloadLista);
        TextMessage textMessageLista = new TextMessage(jsonLista);

        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(textMessageLista);
            }
        }
    }
}