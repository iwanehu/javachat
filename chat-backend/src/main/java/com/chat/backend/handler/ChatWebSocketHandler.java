package com.chat.backend.handler;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import com.chat.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MensajeRepository mensajeRepository;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    // Este mapa ahora vincula la sesión física con su HASH ÚNICO exclusivo
    private static final Map<WebSocketSession, String> mapaUsuarios = new ConcurrentHashMap<>();

    @Autowired
    public ChatWebSocketHandler(MensajeRepository mensajeRepository, ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.mensajeRepository = mensajeRepository;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sesiones.add(session);
        mapaUsuarios.put(session, "PENDIENTE_" + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            // Leemos como árbol genérico para no depender de la estructura estricta de una clase
            JsonNode jsonNode = objectMapper.readTree(payload);

            String contenido = jsonNode.has("contenido") ? jsonNode.get("contenido").asText() : null;
            String remitente = jsonNode.has("remitente") ? jsonNode.get("remitente").asText() : null;

            // --- PROTOCOLO CONNECT_INIT ---
            if ("CONNECT_INIT".equals(contenido) && remitente != null) {
                String token = jsonNode.has("token") ? jsonNode.get("token").asText() : null;

                if (token == null || token.isEmpty() || !jwtUtil.validarToken(token)) {
                    System.err.println("Token inválido en CONNECT_INIT.");
                    session.close(CloseStatus.NOT_ACCEPTABLE); // Cierre controlado 406
                    return;
                }

                String emailUsuario = jwtUtil.extraerUsername(token).trim();
                String hashSesionUnico = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

                mapaUsuarios.put(session, hashSesionUnico);
                System.out.println("Usuario [" + emailUsuario + "] autenticado. Hash: " + hashSesionUnico);

                // Confirmación al cliente
                Map<String, Object> respuestaConfirmacion = new ConcurrentHashMap<>();
                respuestaConfirmacion.put("type", "INIT_SUCCESS");
                respuestaConfirmacion.put("hashSesion", hashSesionUnico);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(respuestaConfirmacion)));

                // Notificación al resto
                Map<String, String> avisoUnion = new ConcurrentHashMap<>();
                avisoUnion.put("remitente", "SISTEMA");
                avisoUnion.put("contenido", "Un usuario se ha unido al chat.");
                retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoUnion)));

                enviarListaUsuariosActivos();
                return;
            }

            // --- MENSAJES ORDINARIOS ---
            String hashRemitente = mapaUsuarios.get(session);
            if (hashRemitente == null || hashRemitente.startsWith("PENDIENTE_")) {
                System.err.println("Mensaje bloqueado: Sesión no inicializada.");
                return;
            }

            // Para evitar que Jackson explote si el JSON trae el campo 'token' u otros extras,
            // extraemos solo lo que necesitamos para construir el Mensaje de la BD
            Mensaje nuevoMensaje = new Mensaje();
            nuevoMensaje.setRemitente(hashRemitente);
            nuevoMensaje.setContenido(contenido);
            nuevoMensaje.setTimeStamp(LocalDateTime.now());

            retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(nuevoMensaje)));

            try {
                mensajeRepository.save(nuevoMensaje);
            } catch (Exception mongoEx) {
                System.err.println("Error MongoDB: " + mongoEx.getMessage());
            }

        } catch (Exception e) {
            // Captura cualquier fallo de parseo para que NUNCA tire la conexión física
            System.err.println("Error crítico procesando JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        String hashSaliente = mapaUsuarios.remove(session);

        if (hashSaliente != null && !hashSaliente.startsWith("PENDIENTE_")) {
            try {
                Map<String, String> avisoSalida = new ConcurrentHashMap<>();
                avisoSalida.put("remitente", "SISTEMA");
                avisoSalida.put("contenido", "Un usuario ha abandonado el chat.");

                retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoSalida)));
                enviarListaUsuariosActivos();
            } catch (Exception e) {
                System.err.println("Error en salida: " + e.getMessage());
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

        String[] listaHashes = mapaUsuarios.values().stream()
                .filter(h -> !h.startsWith("PENDIENTE_"))
                .distinct()
                .toArray(String[]::new);

        payloadLista.put("usuarios", listaHashes);
        retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(payloadLista)));
    }
}