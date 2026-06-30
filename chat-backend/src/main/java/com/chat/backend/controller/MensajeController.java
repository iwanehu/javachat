package com.chat.backend.controller;


import com.chat.backend.model.Mensaje;
import com.chat.backend.repository.MensajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mensajes")
public class MensajeController {

    @Autowired
    private MensajeRepository mensajeRepository;

    @GetMapping("/publicos")
    public List<Mensaje> obtenerMensajesPublico(){
        return mensajeRepository.findByDestinatarioOrderByTimeStampAsc("TODOS");
    }

    @PostMapping("/guardar")
    public Mensaje guardarMensaje(@RequestBody Mensaje mensaje){
        return mensajeRepository.save(mensaje);
    }

}
