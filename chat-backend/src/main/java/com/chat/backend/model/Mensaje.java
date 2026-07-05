package com.chat.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "mensajes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mensaje {

    @Id
    private String id;
    private String remitente;
    private String destinatario;
    private String contenido;
    private LocalDateTime timeStamp;

    public Mensaje(String remitente, String destinatario, String contenido) {
        this.remitente = remitente;
        this.destinatario = destinatario;
        this.contenido = contenido;
        this.timeStamp = LocalDateTime.now();
    }
}