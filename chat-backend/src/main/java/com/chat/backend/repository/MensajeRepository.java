package com.chat.backend.repository;

import com.chat.backend.model.Mensaje;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MensajeRepository extends MongoRepository<Mensaje,String> {

    List<Mensaje> findByDestinatarioOrderByTimeStampAsc(String destinatario);
}
