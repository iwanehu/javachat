package com.chat.server;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {
    private static final String FILE_PATH = "historial.log";
    private static final int MAX_LINEAS_VOLCADO = 30;


    public synchronized static void guardarMensaje(String mensaje){
        try {
            FileWriter fw = new FileWriter(FILE_PATH,true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            out.println(mensaje);
        }catch (Exception e){
            System.out.println("[ERROR HISTORIAL] No se puede escribir en el log: " + e.getMessage());
        }
    }

    //lee las ultimas lineas del archivo para enviarselas al nuevo usuaruo
    public synchronized static List<String> obtenerUltimosMensajes(){
        List<String> lineas = new ArrayList<>();
        File archivo = new File(FILE_PATH);
        if(!archivo.exists()) return lineas;


        try(BufferedReader br = new BufferedReader(new FileReader(archivo))){
            String linea;

            while ((linea = br.readLine())!= null){
                lineas.add(linea);
            }
        }catch (IOException e){
            System.out.println("[ERROR HISTORIAL] Al leer el log: " + e.getMessage());
        }
        int total = lineas.size();
        if (total > MAX_LINEAS_VOLCADO) {
            return lineas.subList(total - MAX_LINEAS_VOLCADO, total);
        }
        return lineas;
    }


}
