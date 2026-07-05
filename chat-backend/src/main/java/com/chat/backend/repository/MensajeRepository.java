package com.chat.backend.repository;

import com.chat.backend.model.Mensaje;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MensajeRepository extends MongoRepository<Mensaje,String> {

    List<Mensaje> findByDestinatarioOrderByTimeStampAsc(String destinatario);

    //Obtener los ultimos mensaje
    List<Mensaje> findTop30ByOrderByTimeStampDesc();

    //Metodo para obtener mensajes por remitentes
    List<Mensaje> findTop30ByRemitenteOrderByTimeStampDesc(String remitente);
}
