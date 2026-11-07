# Collab Draw – real-time collaborative drawing (100% Java)

| Part     | Technology |
|----------|------------|
| Server   | Java 17, Spring Boot 3 (REST + WebSocket, Spring Data JPA) |
| Client   | Java 17, Swing desktop app (java.net.http HttpClient + WebSocket) |
| Database | PostgreSQL 16 (Docker) |

```
collab-draw/
├── docker-compose.yml   # PostgreSQL
├── backend/             # Spring Boot server
└── client/              # Swing client (run one per user)
```

## Run

```bash
# 1. database
docker compose up -d

# 2. server
cd backend && mvn spring-boot:run            # listens on :8080

# 3. client (start this several times, on one or many computers)
cd client && mvn compile exec:java           # connects to http://localhost:8080
# connect to another machine:
cd client && mvn compile exec:java -Dexec.args="http://192.168.1.20:8080"

# or build a runnable jar
cd client && mvn package
java -jar target/draw-client-0.0.1-jar-with-dependencies.jar http://SERVER_IP:8080
```

Create a board in one client, open the same board in another client, and draw.

## Network protocol (JSON over WebSocket `/ws/boards/{boardId}`)

Client -> server
- `{"type":"draw","strokeId","color","size","points":[{x,y},{x,y}]}`  live segment, relayed to others
- `{"type":"stroke","userId","stroke":{id,color,size,points}}`        finished stroke, saved in PostgreSQL
- `{"type":"clear"}`                                                 wipe the board

Server -> client
- relayed `draw` and `clear` messages
- `{"type":"presence","count":N}`  users in the room

REST: `GET/POST /api/boards`, `GET /api/boards/{id}/strokes`

## Ideas to extend
- Usernames + colored cursors, undo/redo, shapes and text
- Save board as PNG
- Replace the WebSocket layer with plain `java.net.Socket`/`ServerSocket` if your course requires raw sockets
