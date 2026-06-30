package com.chat.backend.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;




@Document(collection = "usuarios")
@Data //genera getter ,setter ,tostring ,equals y hashcode
@NoArgsConstructor //contructor vacio obligatorio en sping
@AllArgsConstructor  // constructor con todos los campos
public class Usuario {

    @Id
    private String id;


    @Indexed(unique = true)
    private String username;



    private String password;
    private boolean conectado;




    public Usuario(String username, String password) {
        this.username = username;
        this.password = password;
        this.conectado = false;
    }




}
