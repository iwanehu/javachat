# 🔧 Javachat - Backend

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.4-6DB33F?style=flat&logo=spring-boot)
![WebSocket](https://img.shields.io/badge/WebSocket-FF6F00?style=flat&logo=socket.io)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=json-web-tokens)
![MongoDB](https://img.shields.io/badge/MongoDB-4EA94B?style=flat&logo=mongodb)
![Render](https://img.shields.io/badge/Render-46E3B7?style=flat&logo=render)

API REST y WebSocket para el chat en tiempo real Javachat. Backend desarrollado con Spring Boot, autenticación JWT y MongoDB.

## 🚀 Demo

🔗 [API Base](https://javachat.onrender.com)
🔗 [WebSocket](wss://javachat.onrender.com/ws)

## ✨ Características

- 🔐 **Autenticación JWT** - Registro y login seguro de usuarios
- 💬 **WebSocket** - Comunicación en tiempo real con `TextWebSocketHandler`
- 📜 **Historial de mensajes** - Almacenamiento y recuperación de mensajes
- 👥 **Usuarios activos** - Lista de usuarios conectados en tiempo real
- 🗄️ **MongoDB Atlas** - Persistencia de datos en la nube
- 🔒 **Seguridad** - Validación de tokens JWT en conexiones WebSocket
- 📝 **Logs** - Sistema de logging con SLF4J

## 🛠️ Stack Tecnológico

### Backend
| Tecnología | Versión | Descripción |
|------------|---------|-------------|
| Java | 17 | Lenguaje de programación |
| Spring Boot | 3.2.4 | Framework principal |
| Spring WebSocket | 3.2.4 | Comunicación bidireccional |
| Spring Data MongoDB | 3.2.4 | Persistencia de datos |
| JJWT | 0.11.5 | Generación y validación de JWT |
| Lombok | 1.18.36 | Código boilerplate |
| MongoDB Atlas | - | Base de datos en la nube |

## 📁 Estructura del Proyecto

```bash
javachat-backend/
├── src/main/java/com/chat/backend/
│ ├── config/
│ │ ├── WebConfig.java # Configuración CORS
│ │ └── WebSocketConfig.java # Configuración WebSocket
│ ├── controller/
│ │ ├── AuthController.java # Login/Registro
│ │ ├── HealthCheckController.java # Health check
│ │ └── MensajeController.java # API de mensajes
│ ├── handler/
│ │ └── ChatWebSocketHandler.java # Lógica de WebSocket
│ ├── model/
│ │ ├── Mensaje.java # Modelo de mensaje
│ │ └── Usuario.java # Modelo de usuario
│ ├── repository/
│ │ ├── MensajeRepository.java # Repositorio de mensajes
│ │ └── UsuarioRepository.java # Repositorio de usuarios
│ └── security/
│ └── JwtUtil.java # Utilidades JWT
├── src/main/resources/
│ └── application.properties # Configuración de la app
├── pom.xml # Dependencias Maven
└── Dockerfile # Configuración Docker
```


## 🚀 Instalación y Ejecución

### Requisitos Previos
- JDK 17+
- Maven 3.8+
- MongoDB Atlas (o local)

### Configuración de Variables de Entorno

Crear un archivo `.env` en la raíz:

```env
# MongoDB
MONGO_URI=mongodb+srv://usuario:password@cluster.mongodb.net/database

# JWT
JWT_SECRET=tu_clave_secreta_de_al_menos_32_caracteres

# Puerto (opcional)
PORT=8080
```

Instalación

```bash
# Clonar repositorio
git clone https://github.com/tu-usuario/javachat-backend.git
cd javachat-backend

# Compilar
mvn clean install

# Ejecutar
mvn spring-boot:run
```


Configuración en application.properties

# MongoDB
spring.data.mongodb.uri=${MONGO_URI}
spring.data.mongodb.database=javachat_db

# JWT
jwt.secret=${JWT_SECRET}

# Server
server.port=${PORT:8080}

📡 Endpoints API

Autenticación

Método	Endpoint	          Descripción
POST	/api/auth/registro	Registrar nuevo usuario
POST	/api/auth/login      Iniciar sesión


Ejemplo de Registro
POST /api/auth/registro
{
  "username": "usuario",
  "password": "contraseña123"
}

Ejemplo de Login

POST /api/auth/login
{
  "username": "usuario",
  "password": "contraseña123"
}


Mensajes

Método	       Endpoint	            Descripción
GET	        /api/mensajes/historial	Obtener historial (últimos 30)
GET	/api/mensajes/historial?limite=50	  Historial con límite personalizado
GET	 /api/mensajes/publicos	           Obtener mensajes públicos
GET    	/api/mensajes/count	                  Contar mensajes

🔌 WebSocket

Conexión
```javascript

const socket = new WebSocket('wss://javachat.onrender.com/ws', 'text');
```

Protocolo de Inicialización

{
  "type": "CONNECT_INIT",
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "username": "usuario"
}


Mensajes del Servidor
Type	           Descripción	     Datos
INIT_SUCCESS	Conexión exitosa	{ hashSesion, username }
LISTA_USUARIOS	Lista de usuarios activos	{ usuarios, total }
MENSAJE	           Nuevo mensaje	{ remitente, username, contenido, timeStamp }
SISTEMA          	Mensaje del sistema	{ contenido }

🐳 Docker

Construir la imagen
```bash
docker build -t javachat-backend .

```

Ejecutar el contenedor

```bash
docker run -d -p 8080:8080 --name javachat-backend \
  -e MONGO_URI="mongodb+srv://..." \
  -e JWT_SECRET="tu_clave_secreta" \
  javachat-backend

```

Docker Compose
version: '3.8'
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - MONGO_URI=mongodb+srv://usuario:password@cluster.mongodb.net/database
      - JWT_SECRET=tu_clave_secreta

📊 Arquitectura del Sistema

```bash

┌─────────────────────────────────────────────────────────────┐
│                          Cliente                            │
│                    (React + TypeScript)                     │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      WebSocket                              │
│                   (wss://javachat.onrender.com/ws)          │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot                              │
│              ┌─────────────────────┐                        │
│              │  ChatWebSocket      │                        │
│              │  Handler            │                        │
│              └─────────────────────┘                        │
│              ┌─────────────────────┐                        │
│              │  JWTUtil            │                        │
│              │  (Autenticación)    │                        │
│              └─────────────────────┘                        │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      MongoDB Atlas                          │
│                  (Base de datos en la nube)                 │
└─────────────────────────────────────────────────────────────┘

```


🔐 Seguridad

    🔑 JWT - Tokens firmados con HS256

    ⏰ Expiración - Tokens válidos por 24 horas

    🔒 Validación - Verificación de tokens en cada conexión WebSocket

    🛡️ CORS - Configurado para aceptar peticiones del frontend


📦 Dependencias Principales

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.2.4</version>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
        <version>3.2.4</version>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
        <version>3.2.4</version>
    </dependency>
    
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
</dependencies>


🚀 Despliegue en Render

Conecta tu repositorio de GitHub a Render

Configura:

    Build Command: mvn clean install

    Start Command: java -jar target/chat-backend-1.0-SNAPSHOT.jar

    Environment Variables:

        MONGO_URI

        JWT_SECRET


  📊 Diagrama de Flujo

  1. Usuario → Login → JWT Token
2. Usuario → WebSocket (con JWT) → Conexión establecida
3. Usuario → Envía mensaje → WebSocket Handler → Guarda en MongoDB
4. Servidor → Retransmite mensaje → Todos los usuarios conectados
5. Usuario → Desconecta → Notificación a todos


👤 Autor


    GitHub: https://github.com/iwanehu

    LinkedIn: https://www.linkedin.com/in/snayder-marulanda/


🙏 Agradecimientos

    Spring Boot - Framework

    MongoDB - Base de datos

    Render - Hosting


  ⭐️ ¡No olvides darle una estrella al repositorio!

  
    
    


