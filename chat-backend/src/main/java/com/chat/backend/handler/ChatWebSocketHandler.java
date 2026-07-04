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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Inyectamos el componente de seguridad para la validación en caliente
    @Autowired
    private JwtUtil jwtUtil;

    // Lista de sesiones activas globales
    private static final List<WebSocketSession> sesiones = new CopyOnWriteArrayList<>();

    // Mapa concurrente para rastrear el nombre de usuario de cada sesión activa
    private static final Map<WebSocketSession, String> mapaUsuarios = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // En Render las cabeceras se pierden, por lo que entramos de forma provisional
        sesiones.add(session);
        // Le asignamos un nombre temporal hasta que envíe el evento CONNECT_INIT
        mapaUsuarios.put(session, "PENDIENTE_" + session.getId());
        System.out.println("Canal TCP abierto provisionalmente para la sesión: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            // Leemos como JsonNode para evitar problemas de casteo genérico con Map.class
            JsonNode jsonNode = objectMapper.readTree(payload);
            String contenido = jsonNode.has("contenido") ? jsonNode.get("contenido").asText() : null;
            String remitente = jsonNode.has("remitente") ? jsonNode.get("remitente").asText() : null;

            // --- CASO ESPECIAL: Inicialización de Handshake en diferido con validación JWT ---
            if ("CONNECT_INIT".equals(contenido) && remitente != null) {
                String nombreUsuario = remitente.trim();
                String sessionIdActual = session.getId();

                // Extraemos el token del JSON enviado por el ChatRoom de React
                String token = jsonNode.has("token") ? jsonNode.get("token").asText() : null;

                System.out.println("Validando CONNECT_INIT mediante JWT para: " + nombreUsuario + " (Sesión: " + sessionIdActual + ")");

                // --- FILTRO DE SEGURIDAD JWT ---
                if (token == null || token.isEmpty() || !jwtUtil.validarToken(token) || !nombreUsuario.equals(jwtUtil.extraerUsername(token))) {
                    System.err.println("¡ALERTA DE SEGURIDAD! Intento de suplantación o token inválido para: " + nombreUsuario);
                    // Tumbamos la sesión tcp provisional por falta de credenciales legítimas
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                    return;
                }

                // LIMPIEZA MUTEX: Cerramos SOLO sesiones antiguas reales de ese usuario, protegiendo la sesión actual
                mapaUsuarios.entrySet().removeIf(entry -> {
                    WebSocketSession sesionVieja = entry.getKey();
                    String usuarioAsociado = entry.getValue();

                    // Si es el mismo nombre de usuario pero una conexión física DISTINTA, la purgamos
                    if (usuarioAsociado.equals(nombreUsuario) && !sesionVieja.getId().equals(sessionIdActual)) {
                        sesiones.remove(sesionVieja);
                        if (sesionVieja.isOpen()) {
                            try {
                                sesionVieja.close(CloseStatus.SESSION_NOT_RELIABLE);
                                System.out.println("-> Sesión antigua duplicada cerrada para el usuario: " + nombreUsuario);
                            } catch (Exception e) {
                                System.err.println("Error al cerrar sesión duplicada: " + e.getMessage());
                            }
                        }
                        return true; // Elimina del mapaUsuarios de forma segura
                    }
                    return false;
                });

                // Registramos de forma segura al usuario actual con su nombre real verificado por JWT
                mapaUsuarios.put(session, nombreUsuario);
                System.out.println("Sesión WebSocket AUTENTICADA con JWT con éxito: " + nombreUsuario + " (" + sessionIdActual + ")");

                // El servidor genera el aviso de unión global
                Map<String, String> avisoUnion = new ConcurrentHashMap<>();
                avisoUnion.put("remitente", "SISTEMA");
                avisoUnion.put("contenido", nombreUsuario + " se ha unido al chat.");
                String jsonUnion = objectMapper.writeValueAsString(avisoUnion);

                retransmitirMensaje(new TextMessage(jsonUnion));
                enviarListaUsuariosActivos();
                return; // Cortamos la ejecución, mensaje de control procesado con éxito
            }

            // --- CASO NORMAL: Flujo ordinario de chat ---
            String usuarioAutenticado = mapaUsuarios.get(session);
            if (usuarioAutenticado == null || usuarioAutenticado.startsWith("PENDIENTE_")) {
                System.err.println("Mensaje rechazado: La sesión no ha completado la autenticación JWT inicial.");
                return;
            }

            Mensaje nuevoMensaje = objectMapper.readValue(payload, Mensaje.class);
            nuevoMensaje.setRemitente(usuarioAutenticado);

            if (nuevoMensaje.getTimeStamp() == null) {
                nuevoMensaje.setTimeStamp(LocalDateTime.now());
            }
            if (nuevoMensaje.getDestinatario() == null) {
                nuevoMensaje.setDestinatario("TODOS");
            }

            // RETRANSMISIÓN EN VIVO
            String jsonMensajeVerificado = objectMapper.writeValueAsString(nuevoMensaje);
            retransmitirMensaje(new TextMessage(jsonMensajeVerificado));

            // PERSISTENCIA ASÍNCRONA EN MONGODB
            try {
                mensajeRepository.save(nuevoMensaje);
                System.out.println("Mensaje de " + usuarioAutenticado + " persistido en MongoDB Atlas.");
            } catch (Exception mongoEx) {
                System.err.println("Error al guardar en MongoDB (pero el mensaje se distribuyó): " + mongoEx.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Error al parsear el mensaje en el handler: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sesiones.remove(session);
        String usuarioSaliente = mapaUsuarios.remove(session);
        System.out.println("Sesión WebSocket cerrada: " + session.getId() + " perteneciente a: " + usuarioSaliente);

        // Solo notificamos la salida si el usuario llegó a completar el registro real
        if (usuarioSaliente != null && !usuarioSaliente.startsWith("PENDIENTE_")) {
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

        // Obtenemos solo los nombres únicos, ignorando los temporales ("PENDIENTE_...")
        String[] usuariosUnicos = mapaUsuarios.values().stream()
                .filter(usuario -> !usuario.startsWith("PENDIENTE_"))
                .distinct()
                .toArray(String[]::new);

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