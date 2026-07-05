package com.chat.backend.controller;

import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mensajes")
public class MensajeController {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/publicos")
    public List<Mensaje> obtenerMensajesPublico() {
        return mensajeRepository.findByDestinatarioOrderByTimeStampAsc("TODOS");
    }

    @GetMapping("/historial")
    public List<Mensaje> obtenerHistorial(@RequestParam(defaultValue = "30") int limite) {
        // Obtener los últimos 'limite' mensajes ordenados por timestamp descendente
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "timeStamp"));
        query.limit(limite);

        List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);

        // Revertir el orden para mostrar del más antiguo al más nuevo
        java.util.Collections.reverse(mensajes);

        return mensajes;
    }

    @GetMapping("/historial/usuario/{hash}")
    public List<Mensaje> obtenerHistorialPorUsuario(@PathVariable String hash, @RequestParam(defaultValue = "30") int limite) {
        Query query = new Query();
        query.addCriteria(Criteria.where("remitente").is(hash));
        query.with(Sort.by(Sort.Direction.DESC, "timeStamp"));
        query.limit(limite);

        List<Mensaje> mensajes = mongoTemplate.find(query, Mensaje.class);
        java.util.Collections.reverse(mensajes);

        return mensajes;
    }

    @PostMapping("/guardar")
    public Mensaje guardarMensaje(@RequestBody Mensaje mensaje) {
        return mensajeRepository.save(mensaje);
    }
}