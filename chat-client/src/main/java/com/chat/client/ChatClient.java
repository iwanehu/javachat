package com.chat.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {
    private final String host;
    private final int puerto;
    private final Consumer<String> onMessageReceived;

    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private boolean conectado = false;
    private String ultimoNombreRegistrado;


    public ChatClient(String host, int puerto,Consumer<String> onMessageReceived) {
        this.host = host;
        this.puerto = puerto;
        this.onMessageReceived = onMessageReceived;
    }

public void conectar(){
        new Thread(()->{
           try {

               this.socket= new Socket(host,puerto);
               this.salida = new PrintWriter(socket.getOutputStream(),true);
               this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
               this.conectado=true;

               onMessageReceived.accept("[SISTEMA] Conexion establecida con el servidor.");

             if (ultimoNombreRegistrado != null){
                 enviarMensaje(ultimoNombreRegistrado);
             }


               String linea ;
               while ((linea = entrada.readLine()) != null){
                   //Enviamos el mensaje recibido directamente a la UI a traves del callback
                   onMessageReceived.accept(linea);
               }

           }
           catch (Exception e) {
               onMessageReceived.accept("[ALERTA] Servidor inaccessible. Reintentando en 3 segundos...");
                conectado=false;
                try {
                    Thread.sleep(30000);
                }catch (InterruptedException ignored){}
           }
           finally {
              liberarRecursos();
           }
        }).start();
}

public void enviarMensaje(String mensaje){
        if(salida!=null && conectado){
            salida.println(mensaje);
           if (ultimoNombreRegistrado == null){
               this.ultimoNombreRegistrado = mensaje;
           }
        }
}


public void liberarRecursos(){
        try {
            conectado=false;
            if(entrada!=null)entrada.close();
            if(salida!=null)salida.close();
            if (socket != null && !socket.isClosed()) socket.close();
        }catch (Exception ignored){}
}


}

