# Collab Draw – real-time collaborative drawing

| Part     | Technology |
|----------|------------|
| Server   | Java 17, Spring Boot 3 (REST + WebSocket, Spring Data JPA) |
| Frontend | Java 17, Vaadin Flow browser UI |
| Desktop  | Optional Java 17 Swing client |
| Database | PostgreSQL 16 (Docker) |

```
collab-draw/
├── docker-compose.yml   # PostgreSQL
├── backend/             # Spring Boot server and Java-authored browser UI
└── client/              # Optional Swing desktop client
```

## Run

Requires JDK 17, Maven, Docker Compose, and curl. From the project root, run:

```bash
bash run.sh
```

This starts PostgreSQL and the backend, then opens `http://localhost:8081` in
your browser. Choose Collaborative Whiteboard to create a room or join with its
six-digit room number; the whiteboard shows your name and room number, and its
toolbar is on the left. The dashboard also has a Draw & Guess room-entry dialog;
that game is not implemented yet. The browser UI is authored in Java with
Vaadin. Share the whiteboard invite link with collaborators. The launcher reuses
an existing backend on port 8081.
Backend startup logs are saved to `.run/backend.log`. Press Ctrl+C to stop a
backend started by this launcher; an existing backend and the database remain
running. Run `docker compose down` to stop the database.
If an older app still occupies port 8081, the launcher leaves it running and
starts this UI on the next available port (for example, 8082).

PostgreSQL is exposed on host port 5433; the backend uses port 8081 to avoid
common local service conflicts. To run the components manually, use separate
terminals starting at the project root:

```bash
# 1. database
docker compose up -d

# 2. server
cd backend && mvn spring-boot:run            # listens on :8081

# 3. open the web UI in a browser
# http://localhost:8081

# Optional: launch the Swing desktop client
cd client && bash run.sh                    # connects to http://localhost:8081
# connect to another machine:
cd client && bash run.sh -Dexec.args="http://192.168.1.20:8081"

# or build a runnable jar
cd client && mvn package
java -jar target/draw-client-0.0.1-jar-with-dependencies.jar http://SERVER_IP:8081
```

For browser access from other computers, set `ALLOWED_ORIGINS` to include the
server's browser origin, then open `http://SERVER_IP:8081` on each device. For
example:

```bash
ALLOWED_ORIGINS="http://localhost:8081,http://127.0.0.1:8081,http://192.168.1.20:8081" bash run.sh
```

Create a whiteboard room from the dashboard or join with its six-digit code.
Share the invite link so collaborators can enter the room with their own names.
The Draw & Guess card currently provides only its room-entry dialog; its game UI
is intentionally not implemented yet.

The client launcher clears native library overrides inherited from Snap-based
IDEs, preventing GTK/GLIBC library conflicts. You can also run
`mvn compile exec:java` directly from a regular system terminal.

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

For LAN use, run `hostname -I` to find the server IP, allow that origin with
`ALLOWED_ORIGINS`, and open `http://YOUR_IP:8081` in each browser.