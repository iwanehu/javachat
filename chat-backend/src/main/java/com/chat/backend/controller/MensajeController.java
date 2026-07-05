package com.chat.backend.controller;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/mensajes")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MensajeController {

    @Autowired
    private MensajeRepository mensajeRepository;

    // Obtener mensajes públicos (destinatario = "TODOS")
    @GetMapping("/publicos")
    public List<Mensaje> obtenerMensajesPublico() {
        try {
            return mensajeRepository.findByDestinatarioOrderByTimeStampAsc("TODOS");
        } catch (Exception e) {
            System.err.println("Error obteniendo mensajes públicos: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Obtener historial general (últimos 30 mensajes)
    @GetMapping("/historial")
    public List<Mensaje> obtenerHistorial(@RequestParam(defaultValue = "30") int limite) {
        try {
            System.out.println("📡 Solicitando historial con límite: " + limite);

            // Obtener los últimos mensajes de TODOS los destinatarios
            List<Mensaje> mensajes = mensajeRepository.findTop30ByOrderByTimeStampDesc();

            if (mensajes == null || mensajes.isEmpty()) {
                System.out.println("📭 No hay mensajes en la base de datos");
                return Collections.emptyList();
            }

            // Revertir para mostrar del más antiguo al más nuevo
            Collections.reverse(mensajes);
            System.out.println("📜 Historial encontrado: " + mensajes.size() + " mensajes");

            return mensajes;
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo historial: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // Obtener historial de un destinatario específico
    @GetMapping("/historial/destinatario/{destinatario}")
    public List<Mensaje> obtenerHistorialPorDestinatario(
            @PathVariable String destinatario,
            @RequestParam(defaultValue = "30") int limite) {
        try {
            System.out.println("📡 Solicitando historial para destinatario: " + destinatario);

            List<Mensaje> mensajes = mensajeRepository.findTop30ByDestinatarioOrderByTimeStampDesc(destinatario);

            if (mensajes == null || mensajes.isEmpty()) {
                System.out.println("📭 No hay mensajes para el destinatario: " + destinatario);
                return Collections.emptyList();
            }

            Collections.reverse(mensajes);
            System.out.println("📜 Historial destinatario encontrado: " + mensajes.size() + " mensajes");

            return mensajes;
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo historial por destinatario: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // Obtener historial de un usuario específico (por remitente)
    @GetMapping("/historial/usuario/{hash}")
    public List<Mensaje> obtenerHistorialPorUsuario(
            @PathVariable String hash,
            @RequestParam(defaultValue = "30") int limite) {
        try {
            System.out.println("📡 Solicitando historial para usuario: " + hash);

            List<Mensaje> mensajes = mensajeRepository.findTop30ByRemitenteOrderByTimeStampDesc(hash);

            if (mensajes == null || mensajes.isEmpty()) {
                System.out.println("📭 No hay mensajes para el usuario: " + hash);
                return Collections.emptyList();
            }

            Collections.reverse(mensajes);
            System.out.println("📜 Historial usuario encontrado: " + mensajes.size() + " mensajes");

            return mensajes;
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo historial por usuario: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // Contar mensajes totales
    @GetMapping("/count")
    public String contarMensajes() {
        try {
            long total = mensajeRepository.count();
            long publicos = mensajeRepository.countByDestinatario("TODOS");
            return "Total: " + total + " | Públicos: " + publicos;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Guardar un mensaje
    @PostMapping("/guardar")
    public Mensaje guardarMensaje(@RequestBody Mensaje mensaje) {
        try {
            if (mensaje.getTimeStamp() == null) {
                mensaje.setTimeStamp(LocalDateTime.now());
            }
            if (mensaje.getDestinatario() == null || mensaje.getDestinatario().isEmpty()) {
                mensaje.setDestinatario("TODOS");
            }

            System.out.println("💾 Guardando mensaje:");
            System.out.println("  - Remitente: " + mensaje.getRemitente());
            System.out.println("  - Destinatario: " + mensaje.getDestinatario());
            System.out.println("  - Contenido: " + mensaje.getContenido());

            return mensajeRepository.save(mensaje);
        } catch (Exception e) {
            System.err.println("❌ Error guardando mensaje: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Endpoint para crear mensajes de prueba
    @PostMapping("/crear-prueba")
    public String crearMensajePrueba() {
        try {
            Mensaje msg1 = new Mensaje("SISTEMA", "TODOS", "Mensaje de prueba 1 - Chat iniciado");
            mensajeRepository.save(msg1);

            Mensaje msg2 = new Mensaje("usr_test", "TODOS", "Mensaje de prueba 2 - Hola mundo");
            mensajeRepository.save(msg2);

            return "Mensajes de prueba creados correctamente";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}