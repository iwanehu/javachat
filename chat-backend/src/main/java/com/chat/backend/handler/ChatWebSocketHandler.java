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
    private ObjectMapper objectMapper; // Jackson para transformar el JSON crudo en objeto Java

    // Lista de sesiones activas (
    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    //  Mapa concurrente para rastrear el nombre de usuario de cada sesión activa
    private static final Map<WebSocketSession, String> mapaUsuarios = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sesiones.add(session);
        System.out.println("Nueva sesión WebSocket conectada: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Mensaje recibido: " + payload);

        try {
            // 1. Mapear el JSON crudo que manda la App al objeto Mensaje de MongoDB
            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);

            // 2. Si el mensaje no trae marca de tiempo o destinatario, se asignan por defecto
            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }
            if (nuevoMensaje.getDestinatario() == null) {
                nuevoMensaje.setDestinatario("TODOS");
            }

            // Interceptamos si el mensaje viene del SISTEMA avisando una unión
            if ("SISTEMA".equals(nuevoMensaje.getRemitente()) && nuevoMensaje.getContenido().contains("se ha unido al chat.")) {
                // Extraemos el nombre limpiamente (ej: "prueba5 se ha unido..." -> "prueba5")
                String nombreUsuario = nuevoMensaje.getContenido().split(" ")[0];
                mapaUsuarios.put(session, nombreUsuario);

                // Retransmitimos el mensaje de unión a todos primero
                retransmitirMensaje(message);
                //Enviamos la lista de usuarios actualizada a todo el mundo
                enviarListaUsuariosActivos();
                return;
            }

            // 3. 💾 GUARDAR EN MONGODB (Solo mensajes legítimos, no payloads de estado)
            mensajeRepository.save(nuevoMensaje);
            System.out.println("Mensaje persistido en MongoDB con ID: " + nuevoMensaje.getId());

        } catch (Exception e) {
            System.err.println("Error al parsear o persistir el mensaje: " + e.getMessage());
        }

        // 4. Retransmitir el mensaje recibido a todos los conectados
        retransmitirMensaje(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        // 🟢 NUEVO: Si la sesión que se cierra tenía un usuario asignado, lo sacamos del mapa
        String usuarioSaliente = mapaUsuarios.remove(session);
        System.out.println("Sesión WebSocket cerrada: " + session.getId());

        if (usuarioSaliente != null) {
            try {
                // Creamos un mensaje simulado del sistema para notificar la salida en el feed
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", usuarioSaliente + " ha salido del chat.");
                String jsonSalida = objectMapper.writeValueAsString(avisoSalida);

                retransmitirMensaje(new TextMessage(jsonSalida));

                // 🔊 Actualizamos la lista de la barra lateral para todos los que quedan
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                System.err.println("Error al gestionar salida de usuario: " + e.getMessage());
            }
        }
    }

    // Auxiliar para evitar duplicar el bucle for de retransmisión
    private void retransmitirMensaje(TextMessage message) throws Exception {
        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(message);
            }
        }
    }

    // 🟢 NUEVO: Genera el JSON especial de control y lo distribuye
    private void enviarListaUsuariosActivos() throws Exception {
        Map<String, Object> payloadLista = new ConcurrentHashMap<>();
        payloadLista.put("type", "LISTA_USUARIOS");
        payloadLista.put("usuarios", mapaUsuarios.values()); // Colección con ["prueba5", "prueba6"]

        String jsonLista = objectMapper.writeValueAsString(payloadLista);
        TextMessage textMessageLista = new TextMessage(jsonLista);

        for (WebSocketSession s : sesiones) {
            if (s.isOpen()) {
                s.sendMessage(textMessageLista);
            }
        }
    }
}