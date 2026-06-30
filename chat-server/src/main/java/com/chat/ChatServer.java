package com.chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ChatServer {

    private static final int PUERTO = 12345;

    private static final List<ClientHandler> clientes = new ArrayList<>();

    public static void main(String[] args) {

        System.out.println("[SERVIDOR] Iniciando .Esperando conexiones en el puerto" + PUERTO +"...");

        try(ServerSocket serverSocket = new ServerSocket(PUERTO)){

            while(true){
                Socket clienteSocket = serverSocket.accept();
                System.out.println("[SERVIDOR] Nuevo Cliente conectado desde: "+ clienteSocket.getInetAddress());


                //instanciamos el gestor para este cliente especifico
                ClientHandler handler = new ClientHandler(clienteSocket,clientes);

                //lo añadimos a la lista compartida de forma segura
                synchronized (clientes){
                    clientes.add(handler);
                }

                //creamos el hilo para que clienhadler empiece a hablar con el
                new Thread(handler).start();

            }

        }
        catch (IOException e){
            System.out.println("[ERROR SERVIDOR] : " + e.getMessage());
        }





  }

}
