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
            JsonNode jsonNode = objectMapper.readTree(payload);
            String contenido = jsonNode.has("contenido") ? jsonNode.get("contenido").asText() : null;
            String remitente = jsonNode.has("remitente") ? jsonNode.get("remitente").asText() : null;

            // --- PROTOCOLO CONNECT_INIT CON GENERACIÓN DE HASH ÚNICO ---
            if ("CONNECT_INIT".equals(contenido) && remitente != null) {
                String token = jsonNode.has("token") ? jsonNode.get("token").asText() : null;

                if (token == null || token.isEmpty() || !jwtUtil.validarToken(token)) {
                    System.err.println("Token inválido en CONNECT_INIT.");
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }

                // El token es válido. Ahora generamos un HASH ÚNICO e irrepetible para esta sesión
                String emailUsuario = jwtUtil.extraerUsername(token).trim();
                String hashSesionUnico = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

                // Asignamos el Hash Único a la sesión en nuestro mapa interno
                mapaUsuarios.put(session, hashSesionUnico);
                System.out.println("Usuario [" + emailUsuario + "] autenticado. Hash asignado: " + hashSesionUnico);

                // 1. Enviamos una respuesta PRIVADA al usuario para notificarle su Hash Único asignado
                Map<String, Object> respuestaConfirmacion = new ConcurrentHashMap<>();
                respuestaConfirmacion.put("type", "INIT_SUCCESS");
                respuestaConfirmacion.put("hashSesion", hashSesionUnico);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(respuestaConfirmacion)));

                // 2. Notificamos al resto del chat la llegada del nuevo usuario usando su Hash
                Map<String, String> avisoUnion = new ConcurrentHashMap<>();
                avisoUnion.put("remitente", "SISTEMA");
                avisoUnion.put("contenido", "Un usuario se ha unido al chat.");
                retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(avisoUnion)));

                // 3. Actualizamos la lista pública del chat (enviando solo los hashes anónimos)
                enviarListaUsuariosActivos();
                return;
            }

            // --- FLUJO DE MENSAJES ORDINARIOS ---
            String hashRemitente = mapaUsuarios.get(session);
            if (hashRemitente == null || hashRemitente.startsWith("PENDIENTE_")) {
                System.err.println("Mensaje bloqueado: Sesión no inicializada.");
                return;
            }

            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);
            // Forzamos a que el remitente del mensaje sea obligatoriamente su HASH ÚNICO verificado
            nuevoMensaje.setRemitente(hashRemitente);

            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }

            // Transmitimos el mensaje firmado con el Hash a todos los clientes conectados
            retransmitirMensaje(new TextMessage(objectMapper.writeValueAsString(nuevoMensaje)));

            // Persistencia en base de datos
            try {
                mensajeRepository.save(nuevoMensaje);
            } catch (Exception mongoEx) {
                System.err.println("Error MongoDB: " + mongoEx.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
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