package com.chat.backend.repository;

import com.chat.backend.model.Mensaje;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface MensajeRepository extends MongoRepository<Mensaje,String> {

    List<Mensaje> findByDestinatarioOrderByTimeStampAsc(String destinatario);

    // Método para obtener los últimos mensajes
    @Query(value = "{}", sort = "{ 'timeStamp' : -1 }")
    List<Mensaje> findTop30ByOrderByTimeStampDesc();

    // Método para obtener mensajes por remitente
    @Query(value = "{ 'remitente': ?0 }", sort = "{ 'timeStamp' : -1 }")
    List<Mensaje> findTop30ByRemitenteOrderByTimeStampDesc(String remitente);
}
