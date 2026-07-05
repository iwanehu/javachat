package com.chat.backend.repository;

import com.chat.backend.model.Mensaje;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface MensajeRepository extends MongoRepository<Mensaje, String> {

}