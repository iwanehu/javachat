package com.chat.server;


import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


public class ClientHandler implements Runnable{

    private final Socket socket;
    private final List<ClientHandler> clientes;
    private PrintWriter salida;
    private BufferedReader entrada;
    private String  nickname;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");


    public ClientHandler(Socket socket,List<ClientHandler> clientes){
        this.socket = socket;
        this.clientes = clientes;
    }



    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);

            // 1. Fase de identificación
            this.nickname = entrada.readLine();
            if (this.nickname == null || this.nickname.trim().isEmpty()) {
                this.nickname = "Anónimo_" + socket.getPort();
            }

            System.out.println("[SERVIDOR] " + nickname + " conectado con éxito.");

            // 2. ENVIAR HISTORIAL PERSISTENTE al usuario que se acaba de conectar
            List<String> historial = ChatHistoryManager.obtenerUltimosMensajes();
            for (String msjAntiguo : historial) {
                enviarMensaje(msjAntiguo);
            }

            // 3. Notificar la llegada y actualizar listas globales
            String avisoConexion = crearMensajeFormateado("SISTEMA", nickname + " se ha unido a la sala.");
            retransmitir(avisoConexion, true);
            ChatHistoryManager.guardarMensaje(avisoConexion); // Persiste el aviso del sistema
            actualizarListaUsuariosGlobal();

            // 4. Bucle principal de escucha
            String mensaje;
            while ((mensaje = entrada.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("/salir")) break;

                // --- DETECTOR DE MENSAJE PRIVADO: @user mensaje ---
                if (mensaje.trim().startsWith("@")) {
                    procesarMensajePrivado(mensaje);
                } else {
                    // Mensaje público: se formatea, se difunde y se guarda en archivo
                    String msgPublico = crearMensajeFormateado(nickname, mensaje);
                    retransmitir(msgPublico, true);
                    ChatHistoryManager.guardarMensaje(msgPublico);
                }
            }

        } catch (IOException e) {
            System.out.println("[INFO] Conexión perdida con: " + nickname);
        } finally {
            cerrarConexion();
        }
    }

    private void procesarMensajePrivado(String mensajeRaw) {
        try {
            int primerEspacio = mensajeRaw.indexOf(" ");
            if (primerEspacio == -1) return;

            String targetNick = mensajeRaw.substring(1, primerEspacio).trim();
            String contenidoPrivado = mensajeRaw.substring(primerEspacio).trim();

            String hora = LocalDateTime.now().format(formatter);
            String formatoPrivadoEmisor = "[" + hora + "] (Privado para " + targetNick + "): " + contenidoPrivado;
            String formatoPrivadoReceptor = "[" + hora + "] (Privado de " + nickname + "): " + contenidoPrivado;


            boolean encontrado = false ;
            synchronized (clientes) {
                for (ClientHandler cliente : clientes) {
                    if (cliente.getNickname().equalsIgnoreCase(targetNick)) {
                        cliente.enviarMensaje(formatoPrivadoReceptor);
                        encontrado = true;
                        break;
                    }
                }
            }
            if(encontrado){
                this.enviarMensaje(formatoPrivadoEmisor);
            }else {
                this.enviarMensaje("[" + hora + "] [SISTEMA]: El usuario" + targetNick + " no esta en linea.");
            }


        }catch (Exception e){
            this.enviarMensaje("[SISTEMA] Error al formatear el mensaje privado");
        }
    }



    public void enviarMensaje(String mensaje){
        if (salida != null){
            salida.println(mensaje);
            salida.flush();
        }
    }

    public String getNickname() {
        return this.nickname;
    }


    public String crearMensajeFormateado(String emisor,String texto){
        String hora = LocalDateTime.now().format(formatter);
        return "[" + hora + "] "+emisor+": "+texto;
    }

    private void retransmitir(String mensaje, boolean incluirEmisor){
        synchronized (clientes) {
            for (ClientHandler cliente : clientes) {
                if (!incluirEmisor && cliente == this){
                    continue;
                }
                cliente.enviarMensaje(mensaje);

            }
        }
    }


    private void actualizarListaUsuariosGlobal(){
        StringBuilder sb = new StringBuilder("/lista ");
        synchronized (clientes) {
            for (int i = 0; i < clientes.size(); i++) {
                sb.append(clientes.get(i).getNickname());
                if (i <clientes.size() -1) sb.append(",");
            }
        }
        String comandoLista = sb.toString();
        synchronized (clientes) {
            for (ClientHandler cliente : clientes) {
                cliente.enviarMensaje(comandoLista);
            }
        }
    }


    private void cerrarConexion(){
        try {
            synchronized (clientes) {
                clientes.remove(this);
            }
            String avisoDesconexion = crearMensajeFormateado("SISTEMA",nickname + " ha abandonado el chat");
            retransmitir(avisoDesconexion,false);
            ChatHistoryManager.guardarMensaje(avisoDesconexion);
            actualizarListaUsuariosGlobal();

            if (socket != null && !socket.isClosed()) socket.close();


        }catch (IOException e){
            System.out.println("ERROR al cerrar socket" + e.getMessage());
        }
    }

}
